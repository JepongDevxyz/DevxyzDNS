# DevxyzDNS

A fast, private DNS changer for Android — IPv4 & IPv6. Powered by Jepong Devxyz.

## Features

- **Speed Test** — measures real latency to 20 public DNS providers and activates the fastest with one tap
- **One-tap DNS connect** — big center DNS button; uses a lightweight DNS-only VPN (only port-53 traffic enters the tunnel, everything else bypasses it)
- **DNS-over-TLS** — set a DoT hostname on any provider (e.g. your private ControlD hostname) for encrypted DNS
- **Per-app control** — include/exclude individual apps from the DNS changer
- **DNS Lookup** — query any hostname against any provider, see A/AAAA answers
- **Network Info** — live traffic, connection type, public/gateway/local IP, DNS status
- **Custom DNS servers** — add your own with optional DoT hostname
- **Quick Settings tile** — start/stop from the notification shade
- **Automation** — start on boot, auto-switch to the fastest DNS while active, persistent notification
- **Backup** — export/import all settings as JSON
- No account, no ads, no trackers. Everything stays on your device.

## Building

### GitHub Actions (recommended)

1. Add the workflow file: in this repo on github.com, go to **Add file → Create new file**, name it `.github/workflows/build.yml`, and paste the contents of `.github/workflows/build.yml` from this repo (kept as a local mirror).
2. Add a release keystore as a secret: **Settings → Secrets and variables → Actions → New repository secret**
   - `KEYSTORE_BASE64` — base64 of your release `.jks` file
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. Push any commit (or run the workflow manually) — the signed release APK appears under **Actions → Artifacts**.

### Locally

```bash
export ANDROID_HOME=/path/to/android-sdk
export KEYSTORE_PATH=/path/to/devxyzdns-release.jks
export KEYSTORE_PASSWORD=... KEY_ALIAS=devxyzdns KEY_PASSWORD=...
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Notes

- Turn **OFF Google Play Protect** before installing the APK (sideloaded apps get flagged).
- The app asks for VPN permission on first connect — that is how Android lets a DNS changer work; no traffic is proxied or logged.
- App exclusions and auto-change take effect by restarting the tunnel automatically.

Powered by Jepong Devxyz.
