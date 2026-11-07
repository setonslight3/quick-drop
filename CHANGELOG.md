# Changelog & Version Logging

All notable changes, version logs, and release specifications for **Quick Drop** are documented in this file.

---

## [0.0.1] — First Build (Initial Public Release) — 2026-11-07

### 🚀 Overview
Quick Drop is a local-first, privacy-respecting intelligent academic companion and timetable system built for university students. It combines an obsidian neon schedule matrix board, AI document ingestion, grounded active-recall flashcard "Drops", and a modified SM-2 spaced repetition engine.

### ✨ Key Features in v0.0.1

#### 📅 Timetable & Schedule System
- **Obsidian Neon Timetable Board**: Complete matrix board featuring credit units, double slot tags, elective indicators, venue details, and academic headers matching university portal standards.
- **Dual-Mode Schedule View**:
  - `[ 📊 Full Matrix Board ]`: Full weekly matrix grid with horizontal swipe indicators.
  - `[ 📱 Day Cards View ]`: Mobile-optimized daily agenda with interactive **Mon–Fri** tabs and rich touch-friendly course cards.
- **Multi-Format Timetable Ingestion (HTML, ICS, PDF, CSV)**:
  - Smart DOM & AI parsing extracts course codes (e.g. `SEN 201`, `CYB 203`, `IFT 211`, `COS 201`, `CYB 201`, `ENT 211`, `WU 211`), days, start/end minutes, and room numbers with zero manual entry required.
- **One-Off Schedule Exception Handling**: Ability to cancel, reschedule, or override single lecture occurrences without corrupting the recurring weekly template.

#### 🧠 Grounded AI Lecture Studio & Micro-Drops
- **Direct BYOK HTTPS Engine**: Connect directly to Google Gemini 1.5 Pro / 2.0 Flash with encrypted Keystore API keys — zero third-party proxy relays or intermediate tracking.
- **Anchored Micro-Drops**: Automatically segments lecture slides and notes into bite-sized revision cards with verbatim citation quotes and page-level anchors.
- **Multiple Item Formats**:
  - Concept Flashcards (Active recall body + hidden explanation + confidence rating).
  - Multiple Choice Questions (MCQs with immediate feedback, detailed rationale, and distractors).

#### 📈 SuperMemo SM-2 Spaced Repetition Engine
- **Calculated Mastery States**: Classifies topics dynamically across `NEW`, `LEARNING`, `REVIEW`, and `STABLE`.
- **E-Factor & Interval Scheduling**: Adapts repetition intervals from 1 day to 6 days to weeks based on student review responses (EASY, GOOD, HARD, AGAIN).
- **Streak & Readiness Tracking**: Computes exam and class readiness percentage based on topic mastery distribution and active recall streaks.

#### 📱 Fully Responsive Mobile Architecture
- **Fluid Vertical & Horizontal Movement**: Unfrozen scroll views with smooth mouse wheel, trackpad, and touch support.
- **Adaptive Viewport Breakpoints**:
  - Desktop (≥ 768dp): Modern 260dp dark sidebar with AI engine status indicator.
  - Mobile (< 768dp): Full-width viewport with sleek Mobile Top App Bar and glowing Bottom Navigation Bar (`Dashboard`, `Timetable`, `Study`, `Upload Materials`, `Analytics`).
  - Mobile 2x2 Stats Grid: Metric cards scale from 1x4 to 2x2 on narrow screens for maximum legibility.

#### 🔐 Security & Privacy
- **Encrypted Local SQLite Database**: Local Room / SQLite storage with AES-256-GCM encryption for stored API keys and student data.
- **Air-Gapped Offline Functionality**: Full study sprints, quiz sessions, and schedule checks run 100% offline without requiring internet access.

---

### 📦 Build Artifacts
- **Android APK**: `quickdrop.apk` (Signed Release, Version `0.0.1`, Build Code `1`)
- **Desktop Companion**: Compose Multiplatform Desktop JAR / Native Distribution Package Version `0.0.1`
