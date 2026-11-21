# X-Music Android access

X-Music is derived from Metrolist under GPL-3.0. Upstream history, notices and
the license are retained. This integration applies to Android only; desktop and
iOS retain upstream behavior and must not be distributed as access-gated builds.

## Customer login

Customers enter their existing six-digit Hub activation code. The app calls the
same `https://activationworker.extremeiptv.net/activate` endpoint as the Hub.
After resolution, it calls `/management/primary-verify` with the returned account
credentials. That worker checks the panel's current credentials, enabled and
activated flags, and subscription expiry. No IPTV channels or playlists are
loaded, and no separate music entitlement is required.

The worker's schema-version-1 response must contain a boolean `active` and a
`valid_for_seconds` duration between 1 and 900 for active accounts. An explicit
inactive response revokes access immediately. Unavailable or malformed responses
cannot create or extend access. The last successful, memory-only lease can cover
a temporary outage, up to the worker's expiry (at most 15 minutes). Checks repeat
every minute while the app process runs. A process restart requires a fresh
online check; device wall-clock changes do not extend leases.

Account credentials are AES-GCM encrypted using Android Keystore, stored in
`noBackupFilesDir`, and excluded from music-library exports. The six-digit code
itself is not saved. The authentication client has no HTTP logging interceptors,
does not follow redirects and bounds response sizes. No panel/admin secrets are
embedded in the app.

The gate covers the main activity, deep-link entry to the music UI, media-service
creation, session access and stream resolution. An access-state observer stops
playback when the subscription is rejected or its lease expires. Signing out is
available under **Settings → About → Sign out of X-Music**. Music library data is
retained when signing out.

## Build and distribution

The Android application ID is `net.extremeiptv.xmusic`, with the launcher label
`X-Music` (`.debug` / `X-Music Debug` for debug builds). The Android updater reads
releases only from the public `derangedchief-crypto/X-Music` repository.
Original source namespaces and attribution remain intact.

Use the toolchain and build commands in [development.md](development.md). The
X-Music GitHub Actions workflow uses standard Ubuntu runners. Inherited upstream
workflows remain disabled because they require upstream runners and services.
The public repository supplies corresponding source and retained GPL-3.0 notices.

## GitHub builds and production signing

Run **Actions → Build and publish X-Music → Run workflow** on `main`. Enter
customer-facing release notes. Leave `publish` off to obtain a signed APK as a
workflow artifact, or turn it on to publish a stable release that the app checks.
The workflow verifies Android before building the release APK, then checks its
package ID, version, signing certificate, non-debuggable flag and absence of the
Sentry startup provider. It publishes only after GitHub confirms the APK digest.
An existing release is never overwritten.

Signing uses four repository secrets: `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
`XMUSIC_SIGNING_SHA256` pins the expected certificate as a repository variable.
The restored key is kept in the runner's temporary directory and removed after
the build. Credential-bearing Gradle configuration caches are disabled. Keep an
independent private backup of the production key and passwords; GitHub secrets
cannot be downloaded later.

Production uses `net.extremeiptv.xmusic` and its dedicated signing key. The older
test app uses `net.extremeiptv.xmusic.debug` with a different key, so the first
production install is separate and does not inherit its local data. Subsequent
production updates retain the production identity. The app is displayed as
**X-Music** without a Debug suffix.

## Publishing Android updates

The app checks at process startup and every hour while its main UI is running,
when automatic checks are enabled in Settings. New versions produce an in-app
prompt. Downloads stay in app-private cache and Android confirms installation.
Normal update screens do not expose repository or download URLs. A public URL
can still be discovered by inspecting the binary or network traffic.

Publish a non-draft, non-prerelease GitHub release tagged `v<version>` with a
version greater than the installed `VERSION_NAME`, for example `v14.0.2` after
`14.0.1`. Increase the Android version code as part of that release. Upload the
APK as `X-Music.apk` or `X-Music-<version>.apk`. Retain the installed application
ID and production signing key. A production APK cannot update the old debug
installation in place. The updater validates package identity, signer,
size and the GitHub asset digest before installation.

Pushing code or uploading an APK under an old version does not trigger a stable
update. Existing test prereleases are not offered to customers on the stable
channel. Customers need to install the production APK once manually.

Only plain-text notes between `<!-- xmusic-notes -->` and
`<!-- /xmusic-notes -->` in a release body appear in the app. URL, GitHub and
repository-owner lines are excluded. Maintainer notes remain on GitHub.
License and upstream contributor information is under About → Open-source
licenses rather than the account-menu footer or startup prompts.

This is an access gate on the shipped client, not a server-side music license or
tamper-proof DRM. Modified clients can remove a client-side gate.

## Branding and diagnostics

The activation screen calls the customer's six-digit code **Activation Code**.
The subsequent **Connect YouTube Music** action is optional and connects a
YouTube account; it does not create an X-Music account. The activation background
uses bundled album artwork so it remains visible without an artwork network request.
The vector master is `assets/xmusic/xmusic-logo.svg`; render the raster icon
variants with `scripts/render_xmusic_logo.py` and Pillow.

Android diagnostics use the `x-music` project in the `extreme-us` Sentry
organization, separate from the Hub's `android` project. Reporting remains opt-in
and uses the existing redaction pipeline. Debug builds report to the `test`
environment and release builds to `production`. Symbol uploads require that
organization's `SENTRY_AUTH_TOKEN`; no upload token is bundled in the APK.

## Device verification

- Fresh install: code screen appears before onboarding or music navigation.
- Active Hub code: login succeeds, library/search/playback work as before.
- Invalid, expired or disabled code: content stays locked.
- Reopen after process death: saved credentials are verified before access.
- Disable the account during background playback: playback stops on the next check.
- Network outage: no new access; existing access ends at its original lease expiry.
- After expiry, test widget, notification, Android Auto and deep-link entry points.
- Settings → About → sign out: audio stops and the code screen returns.
- Backup/restore and reinstall: no saved lease or account credentials transfer.
