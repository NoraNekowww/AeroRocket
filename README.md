# AeroRocket 3D 🚀
> **高精度水火箭空间弹道仿真系统 / 3D Water Rocket Ballistics Simulator for Android**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.1.2-brightgreen.svg)](https://github.com/NoraNekowww/AeroRocket/releases)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0+-orange.svg)](https://developer.android.com)
[![AI Generated](https://img.shields.io/badge/AI%20Generated-Co--developed-purple.svg)](#-ai-生成声明--ai-declaration)

---

### ⚠️ AI 生成声明 / AI Declaration
> **本项目（包括两相流喷流动力学模型、3D 空间微分积分求解器、Android 自绘交互引擎与逆向侧风补偿算法）由开发者提出架构设计与验证指标，并深度借助 Nous Research Hermes 与 AI Agent 协同编写与推导生成。特此公开标明。**

---

## 📖 项目简介
**AeroRocket 3D** 是一款专为可乐瓶水火箭设计的专业级 Android 弹道动力学仿真系统。程序告别了粗糙的纯抛物线估算，完整求解水火箭从加压注水、喷水加速、高压气体自由排空到无动力弹道滑翔的全过程物理微分方程。

支持真实 3D 空间轨迹与侧风漂移解算、发射场地卫星遥感航拍底图叠加、当前设备 GPS 定位秒级拉取欧洲中期天气预报中心（ECMWF）地表实况风场，并搭载了根据目标射程反推最优气压、水量、仰角及侧风偏角瞄准补偿的逆向求解器。

---

## ⚡ 核心功能与亮点

### 1. 真实两相流喷流动力学模型
- **推力阶段 A（喷水段）**：严密求解压缩气体绝热膨胀过程方程 $P(t) V_a(t)^\gamma = P_0 V_{a0}^\gamma$ 与伯努利喷嘴排空速度，追踪水推进剂喷出带来的箭体质量动态递减；
- **推力阶段 B（排气段）**：求解水排尽后高压气体的超临界孔口排气（Choked Flow）与亚音速膨胀推力冲量；
- **弹道阶段 C（惯性滑翔段）**：考虑出架导轨三维约束、尾翼迎风自然对齐风标效应（Weathercocking），以及随相对空速平方律变化的三维气动阻力。

### 2. 真 3D 中心轨道相机与交互手势
- **中心包围盒轨道相机 (Center-Pivoted Orbit)**：以整条弹道的几何质心为旋转原点，无论怎么 360° 旋转，弹道始终稳定在屏幕正中央，彻底解决视角甩出屏幕的痛点；
- **自然直觉手感**：单指滑动顺畅旋转视角，双指缩放，双击视角一键复位；
- **快捷视角预设**：顶部芯片一键切换【俯视 Top】（查侧漂最直观）、【侧视 Side】（查高度与推力段）与【复位 Reset】；支持在 3D 空间视界与 2D 垂直纵深剖面之间无缝切换。

### 3. 发射场地卫星遥感底图 (Satellite Imagery)
- 地表支持一键叠加发射场地卫星遥感航拍影像；
- 水火箭发射点、空间弹道、顶点、落地点与侧偏虚线直接投射在真实的操场、跑道与草坪卫星地貌上。

### 4. 当前设备 GPS 定位实况 ECMWF 风场
- 接入 Open-Meteo **ECMWF IFS 0.25°** 全球高分辨率实况气象网格；
- 支持读取当前设备 GPS / 网络真实定位，秒级获取当地地表 10 米风速与真北气象风向；
- **严格遵循真实性原则：定位失败或网络离线时明确显示失败告警，决不伪造虚拟假数据**；支持随时自由切换手动风场覆盖。

### 5. 0° ~ 360° 发射航向罗盘与侧风分解
- 支持 $0^\circ \sim 360^\circ$ 任意设定发射架指向，实时分解为顺/逆风与左/右侧风分量；
- 清晰显示由于侧风导致的横向侧偏距 $Z_{\text{drift}}$ 与航向侧偏角。

### 6. 阻力系数 (Cd) 调节与 500mL 试验小可乐瓶规格
- 开放阻力系数 $C_d$ 在 $0.15 \sim 0.70$ 之间连续调节；
- 内置 **500mL 细长型小可乐瓶试验规格**（容积 0.5L，瓶径 66mm，干重仅 85g，迎风面积大幅缩小，升限更高），同时保留 1.25L、1.50L 标准大瓶与 2.00L 加大重瓶预设。

### 7. 目标反推与前置偏角配平瞄准补偿 (Inverse Solver)
- 输入指定目标距离（如 80m、120m），求解器在多维参数空间内快速寻优，反推推荐最优充气气压、装水体积比与发射仰角；
- 在侧风环境下自动计算**「推荐偏航瞄准修正角（Yaw Aiming Offset）」**，一键前置抵消侧风脱靶，精准直击目标正靶心。

---

## 📱 快速下载与安装

- **预编译安装包**：前往 [Releases 页面](https://github.com/NoraNekowww/AeroRocket/releases) 下载最新版本的正式发行版 APK (`AeroRocket-v1.1.2-release.apk`)。
- **系统要求**：Android 8.0+ (API 26+)
- **在线体验与镜像**：访问 [nzlab.online/water-rocket/](https://nzlab.online/water-rocket/) 自动导流与下载。

---

## 🛠️ 本地编译构建指南

工程采用纯命令行与 Gradle Wrapper 构建，不依赖任何特定 IDE：

```bash
# 1. 克隆本仓库
git clone https://github.com/NoraNekowww/AeroRocket.git
cd AeroRocket

# 2. 配置本地 Android SDK 路径 (在 local.properties 中指定)
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 3. 编译生成 Release APK
./gradlew assembleRelease
# 编译产物位于: app/build/outputs/apk/release/app-release.apk
```

---

## 📄 开源许可证
本项目遵循 [MIT License](LICENSE) 开源协议。
Copyright (c) 2026 NoraNekowww.
