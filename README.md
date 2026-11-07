# Quick Drop — Local-First Intelligent Academic Learning Companion & Timetable System

[![Version](https://img.shields.io/badge/version-0.0.1-emerald.svg)](https://github.com/setonslight3/quick-drop/releases/tag/v0.0.1)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Desktop-cyan.svg)](https://github.com/setonslight3/quick-drop)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Security](https://img.shields.io/badge/privacy-AES--256--GCM%20BYOK-violet.svg)](https://github.com/setonslight3/quick-drop)

Quick Drop is a high-performance, local-first academic companion built with Kotlin Multiplatform, Jetpack Compose, Material 3, Room, Android Keystore, and Google Gemini BYOK. Students manage academic timetables, ingest lecture slides/notes (PDF, HTML, ICS, PPTX), review grounded AI-extracted flashcards ("Drops"), and practice active recall through an adaptive SuperMemo SM-2 spaced repetition engine.

---

## 📥 Downloads & Installation

- **Android APK**: Download [`quickdrop.apk`](https://github.com/setonslight3/quick-drop/releases/download/v0.0.1/quickdrop.apk) from the [v0.0.1 Release](https://github.com/setonslight3/quick-drop/releases/tag/v0.0.1). Sideload directly onto any Android device (Android 8.0+ / API 26+).
- **Desktop Companion**: Run on Windows, macOS, or Linux using Compose Multiplatform (`./gradlew :desktop:run`).

---

## 🌟 Highlights in v0.0.1

### 1. 📅 Obsidian Neon Timetable Board & Dual-Mode View
- **Matrix Board**: Full academic grid with credit units, elective indicators, double-slot badges, and location tags matching modern university portal templates.
- **Mobile Day Cards View**: Touch-first daily agenda with interactive **Mon–Fri** tabs and rich obsidian neon course cards.
- **AI Timetable Ingestion (HTML/ICS/PDF/CSV)**: Drag and drop or upload timetable files to auto-fill the entire semester schedule without manual data entry.

### 2. ⚡ Anchored Micro-Drops & Active Recall
- **Grounded AI Extraction**: Segments slides and notes into bite-sized concept cards with verbatim citation quotes and page-level anchors.
- **Confidence Flip Cards**: Interactive 3D flip cards with active recall rating (Hard, Good, Understood, Review).
- **10-Question Sprint Quizzes**: Weighted sampling based on upcoming lecture schedules and student error history.

### 3. 📈 SuperMemo SM-2 Spaced Repetition
- Dynamic interval expansion (1d → 6d → weeks) based on recall performance.
- Automatic mastery classification (`NEW` → `LEARNING` → `REVIEW` → `STABLE`).

### 4. 📱 Fluid Responsive Architecture
- Responsive breakpoints (`< 768dp` mobile bottom nav, `≥ 768dp` desktop sidebar).
- Fluid vertical and horizontal scrolling across touch, trackpad, and mouse wheel.
- Adaptive 2x2 stats metrics grid on mobile screens.

### 5. 🔐 Air-Gapped Privacy & Security
- **BYOK (Bring Your Own Key)**: Direct device-to-Google Gemini HTTPS API calls.
- **Zero Intermediaries**: Encrypted SQLite storage with AES-256-GCM keystore protection.

---

## 🏗️ Multi-Module Architecture

- **`:core`** (Pure Kotlin JVM Domain):
  - Domain models, state machines, and business logic.
  - Timetable calculators, non-destructive one-off exceptions, and DST handling.
  - Multi-format file extractors (PDF, HTML, ICS, PPTX, images).
  - Draft validators, grounded citation checkers, and prompt builders.
  - SuperMemo SM-2 spaced repetition scheduler.
- **`:app`** (Android Jetpack Compose):
  - Material 3 user interface, Room database schemas, and SQLite migrations.
  - Hardware-backed `AndroidKeyStore` AES-GCM encryption.
  - Background notification scheduling and deep links.
- **`:desktop`** (Compose Multiplatform Desktop):
  - Ultra-wide desktop cockpit with fluid responsiveness, timetable matrix board, and file ingestion studio.

---

## 🚀 Building From Source

```bash
# Clone the repository
git clone https://github.com/setonslight3/quick-drop.git
cd quick-drop

# Run Desktop App
./gradlew :desktop:run

# Build Android Signed Release APK
./gradlew :app:assembleRelease
```

---

## 📄 License
This project is open-source under the [MIT License](LICENSE).
