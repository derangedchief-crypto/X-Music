"""Build evidence and publication for the X-Music Android workflow."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def version():
    text = Path("gradle.properties").read_text()
    match = re.search(r"^appVersion=(\d+\.\d+\.\d+)$", text, re.M)
    if not match:
        raise SystemExit("appVersion must be a stable major.minor.patch version")
    return match.group(1)


def release_exists(tag):
    result = subprocess.run(
        ["gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/releases/tags/{tag}"],
        capture_output=True, text=True,
    )
    if result.returncode == 0:
        return True
    if "HTTP 404" not in result.stderr:
        raise SystemExit("Could not verify whether the release exists")
    return False


def prepare():
    tag = "v" + version()
    if os.environ["PUBLISH"] == "true" and release_exists(tag):
        raise SystemExit("This version is already published. Increase appVersion and versionCode.")
    notes = os.environ["RELEASE_NOTES"].strip()
    if not notes or any(word in notes.lower() for word in ("://", "github", "derangedchief-crypto", "<!--")):
        raise SystemExit("Supply plain-text customer notes without URLs, repository names or HTML markers")
    Path("dist").mkdir(exist_ok=True)
    Path("dist/release-notes.md").write_text(
        "<!-- xmusic-notes -->\n" + notes + "\n<!-- /xmusic-notes -->\n\n"
        + f"Source: {os.environ['GITHUB_SHA']}. Built and signed by GitHub Actions.\n"
        + "AI usage: OpenAI GPT-6 Astra authored the release workflow and app changes.\n"
    )


def verify():
    source = Path("androidApp/build/outputs/apk/release/androidApp-release.apk")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    certificate = run(str(tools / "apksigner"), "verify", "--print-certs", str(source))
    expected = os.environ["SIGNING_SHA256"].lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit("Missing production signing certificate fingerprint")
    if f"Signer #1 certificate SHA-256 digest: {expected}" not in certificate:
        raise SystemExit("APK does not match the production signing identity")
    manifest = run(str(tools / "aapt2"), "dump", "xmltree", str(source), "--file", "AndroidManifest.xml")
    if "io.sentry.android.core.SentryInitProvider" in manifest:
        raise SystemExit("Automatic Sentry startup provider must not be packaged")
    if re.search(r"android:debuggable[^\n]*=true", manifest):
        raise SystemExit("Customer APK must not be debuggable")
    metadata = json.loads((source.parent / "output-metadata.json").read_text())
    if metadata["applicationId"] != "net.extremeiptv.xmusic":
        raise SystemExit("Unexpected production package ID")
    element, = metadata["elements"]
    if element["versionName"] != version():
        raise SystemExit("APK version differs from the release version")
    target = Path("dist/X-Music.apk")
    shutil.copyfile(source, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    Path("dist/SHA256SUMS").write_text(f"{digest}  X-Music.apk\n")
    Path("dist/build-info.json").write_text(json.dumps({
        "source": os.environ["GITHUB_SHA"], "version": version(),
        "versionCode": element["versionCode"], "applicationId": metadata["applicationId"],
        "certificateSha256": expected, "apkSha256": digest,
    }, indent=2) + "\n")


def publish():
    tag = "v" + version()
    if release_exists(tag):
        raise SystemExit("Refusing to replace an existing release")
    repo = os.environ["GITHUB_REPOSITORY"]
    run("gh", "release", "create", tag, "dist/X-Music.apk", "dist/SHA256SUMS", "dist/build-info.json",
        "--repo", repo, "--target", os.environ["GITHUB_SHA"], "--draft", "--title", f"X-Music {version()}",
        "--notes-file", "dist/release-notes.md")
    release = json.loads(run("gh", "api", f"repos/{repo}/releases/tags/{tag}"))
    apk, = [asset for asset in release["assets"] if asset["name"] == "X-Music.apk"]
    expected = "sha256:" + hashlib.sha256(Path("dist/X-Music.apk").read_bytes()).hexdigest()
    if apk["state"] != "uploaded" or apk.get("digest") != expected:
        raise SystemExit("Uploaded APK digest is not verified; release remains a draft")
    run("gh", "release", "edit", tag, "--repo", repo, "--draft=false", "--latest")


if __name__ == "__main__":
    {"prepare": prepare, "verify": verify, "publish": publish}[sys.argv[1]]()
