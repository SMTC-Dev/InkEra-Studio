<div align="center">
  <p><strong>An open-source, professional-grade manga and comic creation studio tailored for Linux.</strong></p>

  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
  <a href="https://openjfx.io/"><img src="https://img.shields.io/badge/JavaFX-21-orange.svg" alt="JavaFX"></a>
  <img src="https://img.shields.io/badge/Platform-Linux-green.svg" alt="Platform: Linux">
  <img src="https://img.shields.io/badge/Stage-Pre--Alpha-red.svg" alt="Stage: Pre--Alpha">
</div>

---

## What is InkEra Studio?

InkEra Studio is an ambitious open-source desktop application designed specifically for manga artists, comic creators, and storytellers. Built with **JavaFX 21** and backed by a high-performance **C++ FFM (Project Panama)** rendering engine, it aims to bridge the gap between complex drawing software and project management tools.

Instead of dealing with scattered image files, InkEra provides a **Print Simulation Engine** and a structured workspace. It manages your chapters, page spreads (Left/Right offset), metadata, and reading directions seamlessly, allowing you to focus entirely on your art and story.

## Why InkEra? (The Vision)

We are building features that make InkEra stand out as the ultimate open-source alternative for digital storytelling:

*   **Dual-Canvas Editing (Planned):** Seamlessly draw on double-page spreads simultaneously. No more guessing how your two-page action panel will look when printed. We aim to offer this advanced feature—often locked behind expensive paywalls in proprietary software—completely free and natively.
*   **Generative AI Integration (Planned):** A built-in AI Lasso tool. Select an area, type a prompt (e.g., "draw a cyberpunk cityscape background"), and let the engine generate it directly on your canvas. A workflow similar to Photoshop's Generative Fill, built right into your open-source manga studio.
*   **InkEra Web & Brush Marketplace (Planned):** A fully integrated web ecosystem. Share your projects, discover new manga, and most importantly, share custom brushes via QR codes. Scan a QR code from the built-in "Marketplace" to instantly import a brush configuration (inspired by the community-driven aspect of ibisPaint).
*   **Real-time Collaborative Drawing (Planned):** Connect to a server and draw on the same canvas with two or more artists simultaneously. Perfect for co-authors, inking/storyboarding duos, or just having fun with friends.

---

## Screenshots

|                      Home Dashboard & Main Menu                      |                      Main Image Gallery                      |
| :------------------------------------------------------------------: | :----------------------------------------------------------: |
| <img src="https://github.com/user-attachments/assets/7a20203e-9af8-4247-9faf-023c54f992cf" width="500"> | <img src="https://github.com/user-attachments/assets/16b14467-5695-488e-a4ca-1139e4ba2daa" width="500"> |

|                      Manga Series Gallery                       |                      Manga Series Detail                       |
| :-------------------------------------------------------------: | :------------------------------------------------------------: |
| <img src="https://github.com/user-attachments/assets/146d0730-2e93-49b2-89ea-00f5ba18a7cb" width="500"> | <img src="https://github.com/user-attachments/assets/36697c85-e4f5-44d5-b8d8-45b6b52cd8fc" width="500"> |

|                      Chapter & Print Spread Management                      |                      Drawing Engine & C++ Workspace                      |
| :-------------------------------------------------------------------------: | :----------------------------------------------------------------------: |
| <img src="https://github.com/user-attachments/assets/f8d5e346-8545-4e4f-89c7-178a1352ef7f" width="500"> | <img src="https://github.com/user-attachments/assets/b0f80871-6c2f-486b-8407-fa2a6a537c45" width="500"> |

---

## Features

### Currently Implemented (v0.1-alpha)
*   **High-Performance C++ Rendering:** Custom drawing engine compiled via CMake and communicating with JavaFX through the new Project Panama (FFM API) for zero-copy memory (Arena) operations.
*   **Anti-Aliased Brush Engine:** Features a custom accumulator-based stroke pathing with Max-Alpha blending (SrcOver) for smooth, non-destructive brush strokes, Bezier spline smoothing, and Krita-style eraser toggle mechanics.
*   **Advanced Project Scaffolding:** Automatically creates organized folder hierarchies (`.inkera/`, `Episodes/`) for new series.
*   **Print Simulation Engine:** A dynamic pagination system that calculates Left/Right page spreads based on the reading direction (Manga RTL or Comic LTR) and offset.
*   **Payload/Registry Separation:** Heavy project data is stored locally in `.inkepisode/meta.json` files, keeping the main app registry lightning fast.
*   **Dynamic Tagging System:** Modern, space-separated token input system for genre and tag management.
*   **Native Linux UI Integration:** Undecorated custom title bars with KDE/KWin compatibility and a premium dark theme.

### Planned Roadmap
*   **[v0.5-beta] Core Drawing Expansion:** Advanced layer management, custom brush engine presets, and fill tools.
*   **[v0.8-beta] Advanced Tools:** Selection tools, vector lines, text/balloon engine, and screen-tones.
*   **[v1.0-stable] Publishing Pipeline:** Export to CBZ, PDF, and standard Webtoon vertical-scroll formats. Cloud sync integration.
*   **[v2.0] Plugin & Collaboration System:** Multi-user server integration, Web ecosystem linkage, and AI toolsets.

## Installation & Build Guide (Linux)

InkEra Studio is currently in active development. To run it from the source code, you need **Java 21+ (JDK)**, **CMake**, a C++ compiler, and **Maven** installed on your system.

**1. Clone the repository:**

```bash
git clone [https://github.com/SametCirik/InkEra-Studio.git](https://github.com/SametCirik/InkEra-Studio.git)
cd InkEra-Studio/
```

**2. Compile C++ Engine and Run:**

```bash
cmake -S src/main/cpp -B src/main/cpp/build && cmake --build src/main/cpp/build -j$(nproc)
mvn clean javafx:run
```

Note: Pre-compiled AppImage and binary files for Linux will be provided in the upcoming `v0.2-alpha` release.

## Contributing

Contributions, issues, and feature requests are always welcome! We are looking for C++ engineers, JavaFX developers, UI/UX designers, and open-source enthusiasts to help build the ultimate open-source alternative to proprietary comic software.

Please check our CONTRIBUTING.md (coming soon) for detailed guidelines

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0). See the LICENSE file for more details.
