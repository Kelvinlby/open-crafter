# Releasing Open Crafter

Release builds are produced by the **Release** GitHub Actions workflow
(`.github/workflows/release.yml`). It runs on **every push (commit) to `main`** and on
**manual dispatch**, and builds **release-mode, unsigned** packages for all three desktop
platforms. Artifacts are uploaded automatically and kept for **7 days**.

| Platform | Output | Built with |
|----------|--------|-----------|
| Linux    | `.deb` + `.rpm` | `flutter_distributor` (Ubuntu 22.04 runner) |
| macOS    | `.app` (zipped) | ad-hoc signed |
| Windows  | `.exe` installer | Inno Setup |

## How to cut a release

Every commit pushed to `main` automatically builds all three platforms and uploads the
artifacts (`linux-packages`, `macos-app`, `windows-installer`), downloadable from the
run summary for 7 days. These builds are named with the **short commit SHA** (e.g.
`open_crafter-6bf8a27-macos-app.zip`) since no version is supplied. (Linux `.deb`/`.rpm` carry
the SHA in the semver build-metadata field — `0.0.0+<sha>` — which also becomes the `.rpm`
release.) To produce a named, publishable build with a GitHub Release:

1. GitHub → **Actions** → **Release** → **Run workflow**.
2. Set **version** (e.g. `1.0.0`).
3. Leave **make_release** off for a test build (artifacts only), or turn it on to also
   publish a **draft** GitHub Release `v<version>` with everything attached. A draft stays
   private until you click *Publish* in the Releases tab.
4. Download artifacts from the run summary.

> Push builds use the short commit SHA for naming (the dispatch input only applies to manual
> runs). The draft-release step runs on manual dispatch only.

## Installing the unsigned builds

- **Linux** — `sudo dpkg -i open-crafter*.deb` (Debian/Ubuntu) or
  `sudo dnf install ./open-crafter*.rpm` (Fedora/RHEL).
- **macOS** — unzip the `*-macos-app.zip` and move `open_crafter.app` to Applications. First
  launch: right-click → **Open**, or run
  `xattr -dr com.apple.quarantine /Applications/open_crafter.app`. (Unsigned/not notarized,
  so Gatekeeper blocks a normal double-click.)
- **Windows** — run the `*-setup.exe`. SmartScreen shows an "unknown publisher" warning →
  **More info** → **Run anyway**. (Unsigned.)

## Adding code signing later

All secrets go in **Settings → Secrets and variables → Actions**.

### macOS (Apple Developer Program, $99/yr)
1. Create a **Developer ID Application** certificate via the Apple Developer portal, export
   from Keychain Access as a password-protected `.p12`.
2. Add secrets: `MACOS_CERT_P12_BASE64` (`base64 -i cert.p12`), `MACOS_CERT_PASSWORD`,
   `MACOS_TEAM_ID`, plus notarization creds `APPLE_ID` + `APPLE_APP_PASSWORD`
   (an app-specific password) — or an App Store Connect API key.
3. In the workflow: import the cert into a temporary keychain, drop the `CODE_SIGN_IDENTITY="-"`
   override (sign with the real identity + Hardened Runtime `--options runtime`), then
   `xcrun notarytool submit --wait` a zip of the `.app` and `xcrun stapler staple` the `.app`.

### Windows
- **File-based `.pfx`:** secrets `WIN_CERT_PFX_BASE64` + `WIN_CERT_PASSWORD`; add a
  `signtool sign /f cert.pfx /p <pw> /fd sha256 /tr <timestamp-url> /td sha256` step on both
  the built `.exe` and the installer.
- **Cloud signing (recommended for CI — no hardware token):** Azure Trusted Signing
  (~$10/mo) via `azure/trusted-signing-action`, run after the Inno build. Modern OV/EV certs
  ship on hardware tokens that cannot run in CI, so this is usually the practical path.

### Linux (optional)
Direct `.deb`/`.rpm` downloads are normally distributed unsigned. Only needed if you host an
apt/yum repo: GPG-sign with `dpkg-sig` / `rpm --addsign` using a `GPG_PRIVATE_KEY` secret.
