# DUNE - Jellyfin Android TV Client

[![License: GPL v2](https://img.shields.io/badge/License-GPL_v2-blue?style=for-the-badge)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Latest Release](https://img.shields.io/github/v/release/Sam42a/DUNE?style=for-the-badge&label=Latest%20Release)](https://github.com/Sam42a/DUNE/releases/latest)
[![GitHub Stars](https://img.shields.io/github/stars/Sam42a/DUNE?style=for-the-badge&label=Stars)](https://github.com/Sam42a/DUNE/stargazers)
[![Support Me](https://img.shields.io/badge/Support_Me-Buy_a_Coffee-orange?style=for-the-badge)](https://coff.ee/sam42)

<p align="center">
  <img src="https://i.imgur.com/qvsQBdr.png" alt="DUNE Logo" width="300">
  <br>
  <img src="https://i.imgur.com/x38f0Tw.png" alt="DUNE Screenshot" width="800">
</p>

> **Copyright © 2025 Sam42a**  
> Based on [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) (C) Jellyfin Contributors

## About

**DUNE** is a modified version of the official [Jellyfin](https://jellyfin.org/) Android TV client with enhanced UI/UX and additional customization options.

> **Note**: This is an unofficial fork not affiliated with the Jellyfin project. The official Jellyfin Android TV client can be found at [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv).

## Translating

This project uses the same translation system as the original Jellyfin Android TV client. If you'd like to help, instead please contribute to the [official Jellyfin Weblate instance](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv).

## ✨ Key Features

### 🎨 Visual & Interface
- **Modernized UI Framework**
  - 🏠 Redesigned homescreen with improved content hierarchy
  - 🔑 Enhanced login experience with visual feedback 
  - 🔑 Default avatars for users without profile images
  - 🔍 Intuitive search interface with voice input
  - 🎭 Multiple theme options including OLED-optimized dark mode, based on [![Jellyfin Android TV OLED](https://img.shields.io/badge/Jellyfin%20Android%20TV-OLED-blue?logo=github)](https://github.com/LitCastVlog/jellyfin-androidtv-OLED) 

### 🛠️ Customization
- **Library Presentation**
  - 📚 Toggle between classic and modern layouts
  - 🖼️ Dynamic backdrops from media artwork
  - 🎬 Customizable homescreen rows (genres, favorites, collections)

### 🎥 Media Experience
- **Enhanced Playback**
  - 🎬 Advanced subtitle controls
  - 🎨 Customizable background effects
  - ⚡ Optimized performance

### ⚙️ Technical Improvements
- 🚀 Reduced memory usage
- ⚡ Faster app startup
- 📦 Side-by-side installation with official client


## 🛠️ Building from Source

### Requirements
- Android Studio Giraffe (2022.3.1+)
- Android SDK (API 35)
- OpenJDK 21+

### Build Instructions
```bash
# Clone repository
git clone [https://github.com/Sam42a/DUNE.git](https://github.com/Sam42a/DUNE.git)
cd DUNE-main

# Build standard version
./gradlew assembleStandardRelease

# Or build enhanced version (coexists with official app)
./gradlew assembleEnhancedRelease

# Install on Device

# Install debug version
./gradlew installStandardDebug

# Install enhanced release
./gradlew installEnhancedRelease

**Note:** The enhanced version uses package ID `Dune.enhanced.tv` which allows it to be installed alongside the original Jellyfin app.


```

## Third-Party Libraries

This project uses the following third-party libraries:

- **Jellyfin SDK** - [GPL-2.0](https://github.com/jellyfin/sdk-kotlin)
- **AndroidX Libraries** - [Apache-2.0](https://developer.android.com/jetpack/androidx)
- **Kotlin Coroutines** - [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines)
- **Koin** - [Apache-2.0](https://insert-koin.io/)
- **Coil** - [Apache-2.0](https://coil-kt.github.io/coil/)
- **Markwon** - [Apache-2.0](https://noties.io/Markwon/)
- **Timber** - [Apache-2.0](https://github.com/JakeWharton/timber)
- **ACRA** - [Apache-2.0](https://github.com/ACRA/acra)
- **Kotest** - [Apache-2.0](https://kotest.io/)
- **MockK** - [Apache-2.0](https://mockk.io/)
## Acknowledgments

This project is based on the work of the Jellyfin Contributors. Special thanks to all the developers and community members who have contributed to the Jellyfin Android TV project.

## License

This project is licensed under the **GNU General Public License v2.0 (GPL-2.0)**. See the [LICENSE](LICENSE) file for details.



