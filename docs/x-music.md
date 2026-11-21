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
`X-Music` (`.debug` / `X-Music Debug` for debug builds). The upstream automatic
updater is disabled so an upstream APK is not offered as an X-Music update.
Original source namespaces and attribution remain intact.

Use the toolchain and build commands in [development.md](development.md). GitHub
Actions is initially disabled on the X-Music repository because the copied
workflows rely on upstream custom runners and secrets. Configure X-Music signing,
build infrastructure and distribution before enabling release workflows. The
private repository's source must still be made available to recipients as
required by GPL-3.0 when distributing binaries.

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
