# AeroRocket 3D 🚀
> **High-Precision 3D Water Rocket Ballistics Simulator for Android**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.1.2-brightgreen.svg)](https://github.com/NoraNekowww/AeroRocket/releases)
[![AI Generated](https://img.shields.io/badge/AI%20Generated-Co--developed-purple.svg)](#-ai-declaration)

---

### ⚠️ AI Declaration
> **This project—including its two-phase propulsion thermodynamics, 3D crosswind ballistic numerical integration, Android native Canvas interactive 3D orbit renderer, and inverse solver—was designed and generated with deep assistance from Nous Research Hermes and AI Agents. This notice is openly disclosed.**

---

## 🌟 Highlights
- **Realistic Two-Phase Thrust Physics**: Isentropic adiabatic air expansion ($P V^\gamma = \text{const}$), Bernoulli water expulsion with time-varying mass loss, and choked/subsonic gas blowdown.
- **Center-Pivoted 3D Orbit Camera**: Smooth gesture rotation centered on the trajectory centroid, top-down drift inspection, and side-elevation presets.
- **Satellite Launch Field Overlay**: Ground satellite imagery overlay with real-time trajectory projections.
- **Current GPS Location ECMWF Wind Integration**: Live 10m surface wind speed & direction from ECMWF IFS 0.25° grid with explicit failure alerts.
- **360° Launch Heading & Crosswind Decomposition**: True North heading compass with automatic lateral drift calculations.
- **Adjustable Drag Coefficient (Cd) & 500mL Mini Bottle Spec**: Slender small bottle spec ($C_d = 0.28, D = 66\text{mm}$) alongside standard 1.25L/1.5L/2.0L presets.
- **Target Inverse Solver with Wind Correction**: Optimizes pressure, water volume, and launch pitch while auto-calculating yaw trim to nullify crosswind drift.

---

## 📱 Download
Download the latest APK (`AeroRocket-v1.1.2-release.apk`) directly from [GitHub Releases](https://github.com/NoraNekowww/AeroRocket/releases).

## 📄 License
Licensed under the [MIT License](LICENSE).
Copyright (c) 2026 NoraNekowww.
