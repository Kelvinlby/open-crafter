<div align="center">

<img width="160" height="160" alt="open-crafter" src="macos/Runner/Assets.xcassets/AppIcon.appiconset/app_icon_1024.png" />

# Open Crafter
Inference Engine for General Embodied World Models

</br>

[![modrinth](https://img.shields.io/static/v1?label=Modrinth&message=Download&color=brightgreen&logo=modrinth&style=for-the-badge)](https://arxiv.org/)
[![release](https://img.shields.io/static/v1?label=Github&message=Release&color=white&logo=github&style=for-the-badge)](https://github.com/Kelvinlby/open-crafter/releases/latest)
[![discord](https://img.shields.io/static/v1?label=Discord&message=Chat&color=7289DA&logo=discord&style=for-the-badge)](https://discord.gg/FjRpnp3S8z)

</div>

## Installation

### Choose a build

| | Stable release | Daily build |
|---|---|---|
| **Where** | [GitHub Releases](https://github.com/Kelvinlby/open-crafter/releases/latest) | **Actions → Release →** latest run → **Artifacts** ([open](https://github.com/Kelvinlby/open-crafter/actions/workflows/release.yml)) |
| **Built from** | A tagged, reviewed version | The latest `main` commit |
| **Use it for** | Everyday use — recommended | Frontier features before they're released |
| **Trade-off** | Stable | May be unstable; built automatically by CI |
| **Notes** | — | Requires a (free) GitHub login to download; artifacts expire after **7 days** and are named by commit SHA |

> Builds are currently **unsigned**, so the first launch shows a security prompt (see per-platform steps below). This is expected.

### Download the right file for your OS

| Platform | Architecture | File to download | How to install |
|---|---|---|---|
| **Windows 10 / 11** | x64 | `open_crafter-<ver>-setup.exe` | Run it. At the SmartScreen warning: **More info → Run anyway**. |
| **macOS** (Apple Silicon, M1 or newer) | arm64 | `open_crafter-<ver>-macos-app.zip` | Unzip, move `open_crafter.app` to **Applications**, then **right-click → Open** the first time (or run `xattr -dr com.apple.quarantine /Applications/open_crafter.app`). |
| **Debian / Ubuntu / Mint / Pop!_OS** | x86_64 | `open-crafter-<ver>-*.deb` | `sudo apt install ./open-crafter-*.deb` |
| **Fedora / RHEL / openSUSE** | x86_64 | `open-crafter-<ver>-*.rpm` | `sudo dnf install ./open-crafter-*.rpm` |

### Platform support

| Platform | Status |
|---|---|
| macOS — Apple Silicon (M1+) | ✅ Supported |
| macOS — Intel (x86_64) | ❌ Not supported |
| Linux x86_64 (glibc 2.35+, e.g. Ubuntu 22.04+) | ✅ Supported |
| Linux ARM / aarch64 | ❌ Not supported |
| Windows 11 / 10 (x64) | ✅ Supported |
| Windows on ARM | ❌ Not supported |

Older glibc Linux distros (pre-2.35) and Flatpak/Snap are not provided. Need a platform that
isn't listed? Open an issue.
