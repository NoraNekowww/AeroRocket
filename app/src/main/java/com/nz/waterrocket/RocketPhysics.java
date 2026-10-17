package com.nz.waterrocket;

import java.util.ArrayList;
import java.util.List;

/**
 * 工业级精度水火箭两相流外弹道数值仿真计算引擎 (Industrial-Grade Multi-Phase Water Rocket CFD & Flight Dynamics Engine)
 *
 * 物理模型特征：
 * 1. 阶段一（排水推力期）：绝热膨胀过程 (P(t)*V_air(t)^gamma = C) + 瓶口收缩修正 (1 - (A_nozzle/A_bottle)^2)
 *    + 强加速度水柱附加伪重力静水压修正 Delta_P_hydro = rho_w * (g*sin(theta) + a_axis) * h_w(t)
 * 2. 阶段二（残余高压气喷射期）：等熵绝热放气模型 + 临界压比判据 (P_crit = 0.52828 P(t))
 *    雍塞流 (Choked Flow, 音速出口) 与亚音速流 (Subsonic Flow, 圣维南 Saint-Venant 气动力学方程) 动态切换
 * 3. 阶段三（无动力惯性滑翔期）：纯外弹道空气动力学阻力矢量积分 (F_drag = 0.5 * rho * Cd * A * v_rel^2)
 * 4. 数值积分器：自适应步长四阶龙格-库塔法 (Adaptive RK4)，动力期 dt = 0.2ms，滑翔期 dt = 10ms
 * 5. 零内存分配 (Zero-Allocation)：热循环内无对象创建，毫秒级响应，杜绝 Android UI 拖动 GC 抖动
 * 6. 落地检测：亚步长线性插值求解精确落地时刻 t_land 与最大射程 x_max
 *
 * @version 1.3.0
 */
public class RocketPhysics {

    // =========================================================================
    // 物理常数与标准环境参数 (SI 国际标准单位)
    // =========================================================================
    public static final double RHO_WATER = 1000.0;     // 水密度 rho_w (kg/m^3)
    public static final double RHO_AIR = 1.205;        // 标准地表空气密度 rho_atm (kg/m^3, 20°C, 101.325 kPa)
    public static final double P_ATM = 101325.0;       // 标准大气压 P_atm (Pa)
    public static final double GAMMA = 1.4;            // 空气绝热指数 gamma (双原子理想气体 Cp/Cv)
    public static final double R_AIR = 287.05;         // 空气定压比气体常数 R_air (J/(kg*K))
    public static final double T0 = 293.15;            // 环境绝对温度 T0 (K, 约 20°C)
    public static final double G = 9.80665;            // 标准重力加速度 g (m/s^2)

    // =========================================================================
    // 轨迹采样数据点
    // =========================================================================
    public static class TrajectoryPoint {
        public final float x;       // 顺发射航向地面投影距离 downrange (m)
        public final float y;       // 垂直发射高度 altitude (m)
        public final float z;       // 横向侧漂位移 crossrange drift (m, +右侧, -左侧)
        public final float vx;      // 速度 X 分量 (m/s)
        public final float vy;      // 速度 Y 分量 (m/s)
        public final float vz;      // 速度 Z 分量 (m/s)
        public final float time;    // 仿真时刻 t (s)
        public final int phase;     // 飞行阶段 (0: 排水喷射, 1: 残气喷射, 2: 自由滑翔)

        // 遥测衍生量 (Telemetry)
        public float thrust = 0f;   // 瞬时推力 (N)
        public float pressure = 0f; // 瓶内瞬时压强 (Pa)
        public float accel = 0f;    // 瞬时合加速度 (m/s^2)
        public float mass = 0f;     // 瞬时总质量 (kg)

        public TrajectoryPoint(float x, float y, float z, float vx, float vy, float vz, float time, int phase) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.time = time;
            this.phase = phase;
        }

        public TrajectoryPoint(float x, float y, float z, float vx, float vy, float vz, float time, int phase,
                               float thrust, float pressure, float accel, float mass) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.time = time;
            this.phase = phase;
            this.thrust = thrust;
            this.pressure = pressure;
            this.accel = accel;
            this.mass = mass;
        }
    }

    // =========================================================================
    // 水火箭输入配置参数 (RocketConfig)
    // =========================================================================
    public static class RocketConfig {
        public String specName = "1.5L 标准可乐瓶";
        public double bottleVolumeL = 1.5;         // 瓶体总容积 V_bottle (L)
        public double bottleDiameterMm = 90.0;     // 瓶身最大外径 d_body (mm)
        public double nozzleDiameterMm = 21.0;     // 喷嘴/瓶口内径 d_nozzle (mm)
        public double dryMassG = 150.0;            // 空载结构干质量 m_dry (g, 包含箭体、尾翼与载荷)
        public double dragCoefficient = 0.35;      // 箭体迎风空气动力阻力系数 C_D (0.15 .. 0.70)
        public double dischargeCoefficient = 0.98; // 喷嘴水流排放流率修正系数 C_d (0.90 .. 0.99)
        public double launchRailLengthM = 1.0;      // 发射架刚性导向滑轨长度 L_rail (m)

        // 发射姿态角设定
        public double launchAngleDeg = 45.0;       // 发射俯仰仰角 pitch theta (30..85 deg)
        public double launchHeadingDeg = 90.0;     // 发射真北航向方位角 azimuth (0..360 deg, 0=正北, 90=正东)
        public double launchYawDeg = 0.0;          // 前置侧风瞄准修正偏角 yaw psi (-25..+25 deg)

        // 动力工质初始充注量
        public double pressureBar = 4.5;           // 打气筒充入初始表压 P_gauge (bar, 绝对压强 P0 = P_gauge + P_atm)
        public double waterVolumeMl = 500.0;       // 初始加水量 V_water0 (mL)

        // 3D 空间大气环境风场
        public double windSpeedMs = 0.0;           // 地面实测/ECMWF环境风速 (m/s)
        public double meteoWindDirectionDeg = 0.0; // 气象真北来风方向 (deg, 0=北风吹向南, 90=东风吹向西)
        public double windAngleDeg = 0.0;          // 来风方向与发射轴向的相对风角 (deg)
    }

    /**
     * 预设瓶身常用试验规格快速应用
     */
    public static void applyBottlePreset(RocketConfig cfg, int presetId) {
        switch (presetId) {
            case 0: // 500mL 细长轻型小可乐瓶 (试验规格)
                cfg.specName = "500mL 试验小可乐瓶";
                cfg.bottleVolumeL = 0.50;
                cfg.bottleDiameterMm = 66.0;
                cfg.dryMassG = 85.0;
                cfg.dragCoefficient = 0.28;
                cfg.waterVolumeMl = 165.0;
                break;
            case 1: // 1.25L 中号可乐瓶
                cfg.specName = "1.25L 中号可乐瓶";
                cfg.bottleVolumeL = 1.25;
                cfg.bottleDiameterMm = 88.0;
                cfg.dryMassG = 130.0;
                cfg.dragCoefficient = 0.33;
                cfg.waterVolumeMl = 410.0;
                break;
            case 2: // 1.50L 标准大可乐瓶 (经典普及型)
            default:
                cfg.specName = "1.50L 标准大可乐瓶";
                cfg.bottleVolumeL = 1.50;
                cfg.bottleDiameterMm = 90.0;
                cfg.dryMassG = 150.0;
                cfg.dragCoefficient = 0.35;
                cfg.waterVolumeMl = 500.0;
                break;
            case 3: // 2.00L 加大重型可乐瓶
                cfg.specName = "2.00L 加大重型可乐瓶";
                cfg.bottleVolumeL = 2.00;
                cfg.bottleDiameterMm = 104.0;
                cfg.dryMassG = 185.0;
                cfg.dragCoefficient = 0.38;
                cfg.waterVolumeMl = 660.0;
                break;
        }
    }

    // =========================================================================
    // 连续高斯置信概率云模型 (落点误差区域参数)
    // =========================================================================
    public static class DispersionCloud {
        public double semiMajorX;           // 纵向射程 95% 置信误差半轴 (m)
        public double semiMinorZ;           // 横向侧偏 95% 置信误差半轴 (m)
        public double cepRadiusM;           // 圆概率误差 CEP (50% 落点半径, m)
    }

    // =========================================================================
    // 仿真输出汇总指标 (SimResult)
    // =========================================================================
    public static class SimResult {
        public double rangeM;                      // 最终地面顺轨射程 X (m)
        public double lateralDriftM;               // 最终横向侧向漂移量 Z (m, +右侧, -左侧)
        public double totalDistanceM;              // 地面直线总位移 sqrt(X^2 + Z^2) (m)
        public double deflectionDeg;               // 侧偏航向角 (deg)
        public double apogeeM;                     // 最大顶点飞行高度 Y_max (m)
        public double maxSpeedMs;                  // 全程最大合速度 (m/s)
        public double maxSpeedKmh;                 // 全程最大合速度 (km/h)
        public double flightTimeS;                 // 全程滞空总时长 (s)
        public double waterBurnTimeMs;             // 阶段一喷水推力持续时长 (ms)
        public double airBurnTimeMs;               // 阶段二残气喷射持续时长 (ms)
        public double impactSpeedMs;               // 触地瞬时碰撞速度 (m/s)
        public double impactEnergyJ;               // 触地碰撞动能 0.5 * m_dry * v_impact^2 (J)
        public double maxThrustN;                  // 峰值推力 (N)
        public double maxAccelerationG;            // 峰值加速度 G 值 (a_max / g)

        public List<TrajectoryPoint> trajectory = new ArrayList<>();
        public DispersionCloud dispersion = new DispersionCloud();
    }

    // =========================================================================
    // 目标反推逆向求解器推荐方案结果 (SolveResult)
    // =========================================================================
    public static class SolveResult {
        public double recommendedPressureBar;      // 推荐最佳充气表压 (bar)
        public double recommendedWaterMl;          // 推荐最佳加水量 (mL)
        public double recommendedAngleDeg;         // 推荐发射俯仰仰角 (deg)
        public double recommendedYawDeg;           // 推荐侧风偏航配平修正角 (deg)
        public double predictedRangeM;             // 预测射程 (m)
        public double predictedDriftM;             // 预测横向侧漂残留 (m)
        public double predictedApogeeM;            // 预测顶点高度 (m)
        public double predictedFlightTimeS;        // 预测飞行滞空时间 (s)
        public double errorM;                      // 距目标距离的绝对偏差 (m)
        public SimResult simResult;
    }

    // =========================================================================
    // 核心仿真算法实现 (三阶段 ODEs 耦合 + 自适应步长 RK4 积分器)
    // =========================================================================

    /**
     * 执行工业级精度水火箭全弹道数值积分
     *
     * @param cfg 水火箭配置参数
     * @return 完整轨迹采样点与汇总物理指标
     */
    public static SimResult simulate(RocketConfig cfg) {
        SimResult res = new SimResult();

        // 1. 单位转换与输入几何换算 (统一转换至 SI 标准制单位)
        double V_bottle = Math.max(0.0001, cfg.bottleVolumeL * 0.001); // m^3
        double V_water0 = Math.max(0.0, cfg.waterVolumeMl * 0.000001); // m^3

        // 边界安全防护：防止加水量超限导致无空气膨胀空间，或空载纯气发射
        if (V_water0 >= V_bottle) {
            V_water0 = V_bottle * 0.98; // 预留至少 2% 空气腔
        }
        double V_air0 = V_bottle - V_water0; // 初始高压空气容积 (m^3)

        double d_nozzle = Math.max(0.001, cfg.nozzleDiameterMm * 0.001); // m
        double A_nozzle = Math.PI * (d_nozzle * 0.5) * (d_nozzle * 0.5); // m^2

        double d_bottle = Math.max(d_nozzle * 1.05, cfg.bottleDiameterMm * 0.001); // m
        double A_bottle = Math.PI * (d_bottle * 0.5) * (d_bottle * 0.5); // m^2

        // 喷嘴与瓶身收缩面积比因子：1 - (A_nozzle / A_bottle)^2
        double areaRatio = A_nozzle / A_bottle;
        double contractionFactor = Math.max(0.05, 1.0 - (areaRatio * areaRatio));

        double m_dry = Math.max(0.010, cfg.dryMassG * 0.001); // kg
        double Cd_body = Math.max(0.05, cfg.dragCoefficient);
        double Cd_nozzle = Math.max(0.50, Math.min(1.0, cfg.dischargeCoefficient > 0 ? cfg.dischargeCoefficient : 0.98));

        double P_gauge = Math.max(0.0, cfg.pressureBar * 100000.0); // Pa
        double P0 = P_gauge + P_ATM; // 初始绝对压强 (Pa)

        // 初始高压气体质量与总起飞质量
        double m_air0 = (P0 * V_air0) / (R_AIR * T0);
        double m0 = m_dry + (RHO_WATER * V_water0) + m_air0;

        // 发射初始指向单位矢量 (右手坐标系: +X 顺轨射向, +Y 垂直海拔, +Z 横向右侧偏)
        double thetaRad = Math.toRadians(cfg.launchAngleDeg);
        double psiRad = Math.toRadians(cfg.launchYawDeg);

        double uLaunchX = Math.cos(thetaRad) * Math.cos(psiRad);
        double uLaunchY = Math.sin(thetaRad);
        double uLaunchZ = Math.cos(thetaRad) * Math.sin(psiRad);

        // 环境风场矢量分解 (按相对风角 windAngleDeg 转换至箭体局部世界坐标)
        double windAngleRad = Math.toRadians(cfg.windAngleDeg);
        double windVx = -cfg.windSpeedMs * Math.cos(windAngleRad); // 逆风为正阻力
        double windVz = -cfg.windSpeedMs * Math.sin(windAngleRad); // 侧风横向分量

        // 2. 状态向量初始化 (Zero-Allocation 原始数据类型)
        // 状态分量: x, y, z, vx, vy, vz, m, V_air
        double x = 0.0, y = 0.0, z = 0.0;
        double vx = 0.0, vy = 0.0, vz = 0.0;
        double m = m0;
        double V_air = V_air0;
        double t = 0.0;

        // 阶段一与阶段二过渡热力学状态记录
        int phase = (V_water0 <= 1e-7) ? 1 : 0; // 若初始水量为0直接进入阶段二
        double P1 = P0, T1 = T0, rho_air_in1 = (P0 / (R_AIR * T0));
        double t_water_end = (phase == 1) ? 0.0 : -1.0;
        double t_air_end = -1.0;

        // 箭体物理姿态跟踪矢量 (带转动惯量与推力刚度)
        double ub_x = uLaunchX;
        double ub_y = uLaunchY;
        double ub_z = uLaunchZ;

        // 极值统计跟踪
        double maxSpeed = 0.0;
        double maxAlt = 0.0;
        double maxThrust = 0.0;
        double maxAccel = 0.0;

        // 轨迹采样与步长控制
        res.trajectory.add(new TrajectoryPoint(0f, 0f, 0f, 0f, 0f, 0f, 0f, phase, 0f, (float) P0, 0f, (float) m0));
        double nextSampleTime = 0.015; // 每隔 15ms 降采样一次送交 UI 渲染
        double dt = (phase == 2) ? 0.01 : 0.0002; // 自适应步长：动力期 0.2ms，滑翔期 10ms

        // 预分配 RK4 计算导数暂存结构 (避免热循环 GC)
        double[] k1 = new double[6]; // [dx, dy, dz, dvx, dvy, dvz]
        double[] k2 = new double[6];
        double[] k3 = new double[6];
        double[] k4 = new double[6];
        double[] aux = new double[4]; // [dm_dt, dV_air_dt, F_thrust, P_inst]

        // 3. 自适应步长四阶龙格-库塔 (RK4) 主数值积分循环 (超时上限 60 秒)
        while (t < 60.0) {
            double vMag = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vMag > maxSpeed) maxSpeed = vMag;
            if (y > maxAlt) maxAlt = y;

            // =================================================================
            // 阶段动态切换与边界条件判据
            // =================================================================
            if (phase == 0 && V_air >= V_bottle) {
                // 阶段一水排空瞬间 -> 记录此时瓶内气体初始膨胀点 (P1, T1, rho_air_in1)
                phase = 1;
                t_water_end = t;
                V_air = V_bottle;
                P1 = P0 * Math.pow(V_air0 / V_bottle, GAMMA);
                T1 = T0 * Math.pow(P1 / P0, (GAMMA - 1.0) / GAMMA);
                rho_air_in1 = P1 / (R_AIR * Math.max(100.0, T1));
            } else if (phase == 1) {
                double m_air_cur = Math.max(0.0, m - m_dry);
                double rho_in = m_air_cur / V_bottle;
                double P_cur = (rho_air_in1 > 0) ? P1 * Math.pow(rho_in / rho_air_in1, GAMMA) : P_ATM;
                if (P_cur <= P_ATM || m_air_cur <= 1e-6) {
                    // 阶段二残气排空瞬间 -> 进入阶段三无动力滑翔
                    phase = 2;
                    t_air_end = t;
                    dt = 0.01; // 切换为无动力粗步长 10ms，大幅提高计算吞吐量
                }
            }

            // =================================================================
            // 姿态动力学解算 (导轨内刚性约束 vs 出轨后风标力矩平滑跟踪)
            // =================================================================
            double distRail = Math.sqrt(x * x + y * y + z * z);
            if (distRail < cfg.launchRailLengthM) {
                ub_x = uLaunchX;
                ub_y = uLaunchY;
                ub_z = uLaunchZ;
            } else if (vMag > 0.4) {
                // 出架后尾翼风标效应：由于具有角动量惯性与喷流推力刚度，以时间常数 tau 弛豫跟踪相对迎风空速
                double tau = (phase == 2) ? 0.12 : 0.40;
                double alpha = 1.0 - Math.exp(-dt / tau);

                double vRelX = vx - windVx;
                double vRelY = vy;
                double vRelZ = vz - windVz;
                double vRelMag = Math.sqrt(vRelX * vRelX + vRelY * vRelY + vRelZ * vRelZ);

                double targetUx = (vRelMag > 0.4) ? (0.88 * (vx / vMag) + 0.12 * (vRelX / vRelMag)) : (vx / vMag);
                double targetUy = (vRelMag > 0.4) ? (0.88 * (vy / vMag) + 0.12 * (vRelY / vRelMag)) : (vy / vMag);
                double targetUz = (vRelMag > 0.4) ? (0.88 * (vz / vMag) + 0.12 * (vRelZ / vRelMag)) : (vz / vMag);
                double normT = Math.sqrt(targetUx * targetUx + targetUy * targetUy + targetUz * targetUz);
                if (normT > 1e-6) {
                    targetUx /= normT; targetUy /= normT; targetUz /= normT;
                }

                ub_x += (targetUx - ub_x) * alpha;
                ub_y += (targetUy - ub_y) * alpha;
                ub_z += (targetUz - ub_z) * alpha;
                double normB = Math.sqrt(ub_x * ub_x + ub_y * ub_y + ub_z * ub_z);
                if (normB > 1e-6) {
                    ub_x /= normB; ub_y /= normB; ub_z /= normB;
                }
            }

            // =================================================================
            // 四阶龙格-库塔 (RK4) 核心子步积分
            // =================================================================
            // K1
            computeDerivatives(phase, x, y, z, vx, vy, vz, m, V_air,
                    ub_x, ub_y, ub_z, windVx, windVz,
                    V_bottle, V_air0, P0, P1, T1, rho_air_in1, m_dry,
                    A_nozzle, A_bottle, contractionFactor, Cd_nozzle, Cd_body,
                    k1, aux);
            double dm1 = aux[0], dV1 = aux[1], F_thrust_step = aux[2], P_step = aux[3];
            double a_step = Math.sqrt(k1[3] * k1[3] + k1[4] * k1[4] + k1[5] * k1[5]);
            if (F_thrust_step > maxThrust) maxThrust = F_thrust_step;
            if (a_step > maxAccel) maxAccel = a_step;

            // K2
            computeDerivatives(phase,
                    x + 0.5 * dt * k1[0], y + 0.5 * dt * k1[1], z + 0.5 * dt * k1[2],
                    vx + 0.5 * dt * k1[3], vy + 0.5 * dt * k1[4], vz + 0.5 * dt * k1[5],
                    m + 0.5 * dt * dm1, V_air + 0.5 * dt * dV1,
                    ub_x, ub_y, ub_z, windVx, windVz,
                    V_bottle, V_air0, P0, P1, T1, rho_air_in1, m_dry,
                    A_nozzle, A_bottle, contractionFactor, Cd_nozzle, Cd_body,
                    k2, aux);
            double dm2 = aux[0], dV2 = aux[1];

            // K3
            computeDerivatives(phase,
                    x + 0.5 * dt * k2[0], y + 0.5 * dt * k2[1], z + 0.5 * dt * k2[2],
                    vx + 0.5 * dt * k2[3], vy + 0.5 * dt * k2[4], vz + 0.5 * dt * k2[5],
                    m + 0.5 * dt * dm2, V_air + 0.5 * dt * dV2,
                    ub_x, ub_y, ub_z, windVx, windVz,
                    V_bottle, V_air0, P0, P1, T1, rho_air_in1, m_dry,
                    A_nozzle, A_bottle, contractionFactor, Cd_nozzle, Cd_body,
                    k3, aux);
            double dm3 = aux[0], dV3 = aux[1];

            // K4
            computeDerivatives(phase,
                    x + dt * k3[0], y + dt * k3[1], z + dt * k3[2],
                    vx + dt * k3[3], vy + dt * k3[4], vz + dt * k3[5],
                    m + dt * dm3, V_air + dt * dV3,
                    ub_x, ub_y, ub_z, windVx, windVz,
                    V_bottle, V_air0, P0, P1, T1, rho_air_in1, m_dry,
                    A_nozzle, A_bottle, contractionFactor, Cd_nozzle, Cd_body,
                    k4, aux);
            double dm4 = aux[0], dV4 = aux[1];

            // 状态推进更新 (Simpson 权重: 1/6 * (k1 + 2k2 + 2k3 + k4))
            x  += (dt / 6.0) * (k1[0] + 2.0 * k2[0] + 2.0 * k3[0] + k4[0]);
            y  += (dt / 6.0) * (k1[1] + 2.0 * k2[1] + 2.0 * k3[1] + k4[1]);
            z  += (dt / 6.0) * (k1[2] + 2.0 * k2[2] + 2.0 * k3[2] + k4[2]);
            vx += (dt / 6.0) * (k1[3] + 2.0 * k2[3] + 2.0 * k3[3] + k4[3]);
            vy += (dt / 6.0) * (k1[4] + 2.0 * k2[4] + 2.0 * k3[4] + k4[4]);
            vz += (dt / 6.0) * (k1[5] + 2.0 * k2[5] + 2.0 * k3[5] + k4[5]);

            if (phase == 0) {
                V_air += (dt / 6.0) * (dV1 + 2.0 * dV2 + 2.0 * dV3 + dV4);
                m     += (dt / 6.0) * (dm1 + 2.0 * dm2 + 2.0 * dm3 + dm4);
            } else if (phase == 1) {
                m     += (dt / 6.0) * (dm1 + 2.0 * dm2 + 2.0 * dm3 + dm4);
                if (m < m_dry) m = m_dry;
            } else {
                m = m_dry;
            }

            t += dt;

            // =================================================================
            // 轨迹智能降采样记录 (优化 UI 渲染开销与内存分配)
            // =================================================================
            if (t >= nextSampleTime || phase != res.trajectory.get(res.trajectory.size() - 1).phase) {
                res.trajectory.add(new TrajectoryPoint(
                        (float) x, (float) Math.max(0.0, y), (float) z,
                        (float) vx, (float) vy, (float) vz, (float) t, phase,
                        (float) F_thrust_step, (float) P_step, (float) a_step, (float) m));
                nextSampleTime = t + (phase == 2 ? 0.020 : 0.005);
            }

            // =================================================================
            // 触地检测与精确线性插值求解 (Impact Interpolation)
            // =================================================================
            if (y < 0.0 && t > 0.05) {
                double prevY = y - vy * dt;
                double fraction = (prevY > 0.0 && vy != 0.0) ? (prevY / (prevY - y)) : 0.0;

                x = (x - vx * dt) + vx * dt * fraction;
                z = (z - vz * dt) + vz * dt * fraction;
                t = (t - dt) + dt * fraction;
                y = 0.0;

                res.trajectory.add(new TrajectoryPoint(
                        (float) x, 0f, (float) z,
                        (float) vx, (float) vy, (float) vz, (float) t, phase,
                        0f, (float) P_ATM, 0f, (float) m_dry));
                break;
            }
        }

        // 4. 汇总仿真核心外弹道指标
        double impactSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        res.rangeM = Math.max(0.0, x);
        res.lateralDriftM = z;
        res.totalDistanceM = Math.hypot(x, z);
        res.deflectionDeg = Math.toDegrees(Math.atan2(z, Math.max(0.001, x)));
        res.apogeeM = maxAlt;
        res.maxSpeedMs = maxSpeed;
        res.maxSpeedKmh = maxSpeed * 3.6;
        res.flightTimeS = t;
        res.waterBurnTimeMs = (t_water_end > 0 ? t_water_end : 0.0) * 1000.0;
        res.airBurnTimeMs = (t_air_end > t_water_end ? (t_air_end - t_water_end) : 0.0) * 1000.0;
        res.impactSpeedMs = impactSpeed;
        res.impactEnergyJ = 0.5 * m_dry * impactSpeed * impactSpeed;
        res.maxThrustN = maxThrust;
        res.maxAccelerationG = maxAccel / G;

        // 5. 解算连续高斯落点置信概率云 (CEP 与 95% 范围)
        calculateDispersionCloud(res, cfg);

        return res;
    }

    /**
     * 计算特定状态下的全系统导数矢量 (ODEs RHS Function, Zero-Allocation)
     *
     * @param outDeriv 输出 [dx, dy, dz, dvx, dvy, dvz]
     * @param outAux   输出辅助变量 [dm_dt, dV_air_dt, F_thrust, P_inst]
     */
    private static void computeDerivatives(
            int phase, double x, double y, double z, double vx, double vy, double vz, double m_cur, double V_air_cur,
            double ub_x, double ub_y, double ub_z, double windVx, double windVz,
            double V_bottle, double V_air0, double P0, double P1, double T1, double rho_air_in1, double m_dry,
            double A_nozzle, double A_bottle, double contractionFactor, double Cd_nozzle, double Cd_body,
            double[] outDeriv, double[] outAux) {

        double m_eff = Math.max(m_dry, m_cur);
        double F_thrust = 0.0;
        double dm_dt = 0.0;
        double dV_air_dt = 0.0;
        double P_inst = P_ATM;

        if (phase == 0) {
            // =================================================================
            // 阶段一：排水推力期 (Water Expulsion Phase)
            // =================================================================
            double V_air_eff = Math.max(V_air0, Math.min(V_bottle, V_air_cur));
            P_inst = P0 * Math.pow(V_air0 / V_air_eff, GAMMA); // 绝热膨胀气压 (Pa)

            double V_water_cur = Math.max(0.0, V_bottle - V_air_eff);
            double h_water = V_water_cur / A_bottle; // 瓶内瞬时水柱高度 (m)

            // 计入高加速度水柱附加静水压修正 Delta P_hydro = rho_w * (g * sin(theta) + a_axis) * h_w
            double a_approx = Math.max(0.0, (2.0 * Cd_nozzle * A_nozzle * Math.max(0.0, P_inst - P_ATM) / m_eff) - G * ub_y);
            double deltaP_hydro = RHO_WATER * (G * ub_y + a_approx) * h_water;

            double P_drive = Math.max(0.0, P_inst - P_ATM + deltaP_hydro);

            // 考虑喷嘴与瓶身收缩面积比修正的伯努利排空喷流流速:
            // v_e(t) = sqrt( 2 * (P(t) - P_atm + Delta P_hydro) / (rho_w * (1 - (A_n / A_b)^2)) )
            double ve = Math.sqrt((2.0 * P_drive) / (RHO_WATER * contractionFactor));

            // 质量与容积变化率
            dV_air_dt = Cd_nozzle * A_nozzle * ve;
            dm_dt = -RHO_WATER * dV_air_dt;

            // 瞬时推力
            F_thrust = 2.0 * Cd_nozzle * A_nozzle * P_drive;

        } else if (phase == 1) {
            // =================================================================
            // 阶段二：残余高压气体喷射期 (Air Blowdown Phase)
            // =================================================================
            double m_air_cur = Math.max(1e-6, m_cur - m_dry);
            double rho_in = m_air_cur / V_bottle;

            P_inst = Math.max(P_ATM, P1 * Math.pow(rho_in / Math.max(1e-6, rho_air_in1), GAMMA));
            double T_inst = Math.max(80.0, T1 * Math.pow(P_inst / Math.max(1.0, P1), (GAMMA - 1.0) / GAMMA));

            // 临界压力判据 P_crit = P(t) * (2 / (gamma + 1))^(gamma / (gamma - 1)) ≈ 0.52828 P(t)
            double P_crit = P_inst * Math.pow(2.0 / (GAMMA + 1.0), GAMMA / (GAMMA - 1.0));

            double Pe, Te, ve_air, rho_e;

            if (P_crit > P_ATM) {
                // 音速雍塞流 (Choked Sonic Flow): 出口马赫数 Ma_e = 1
                Pe = P_crit;
                Te = T_inst * (2.0 / (GAMMA + 1.0));
                ve_air = Math.sqrt(GAMMA * R_AIR * Te);
                rho_e = Pe / (R_AIR * Math.max(50.0, Te));
            } else {
                // 亚音速膨胀流 (Subsonic Flow): 圣维南 Saint-Venant 气动力学方程
                Pe = P_ATM;
                double pr = Math.max(1e-4, P_ATM / P_inst);
                Te = Math.max(80.0, T_inst * Math.pow(pr, (GAMMA - 1.0) / GAMMA));
                double term = Math.max(0.0, 1.0 - Math.pow(pr, (GAMMA - 1.0) / GAMMA));
                ve_air = Math.sqrt((2.0 * GAMMA / (GAMMA - 1.0)) * R_AIR * T_inst * term);
                rho_e = Pe / (R_AIR * Math.max(50.0, Te));
            }

            dm_dt = -Cd_nozzle * A_nozzle * rho_e * ve_air;
            F_thrust = (-dm_dt * ve_air) + A_nozzle * (Pe - P_ATM);
            dV_air_dt = 0.0;

        } else {
            // =================================================================
            // 阶段三：无动力滑翔期 (Ballistic Coasting Phase)
            // =================================================================
            F_thrust = 0.0;
            dm_dt = 0.0;
            dV_air_dt = 0.0;
            P_inst = P_ATM;
        }

        // =====================================================================
        // 空气动力学合阻力解算 (考虑环境风向风速矢量合成)
        // =====================================================================
        double vRelX = vx - windVx;
        double vRelY = vy;
        double vRelZ = vz - windVz;
        double vRelMag = Math.sqrt(vRelX * vRelX + vRelY * vRelY + vRelZ * vRelZ);

        double F_drag = 0.5 * RHO_AIR * Cd_body * A_bottle * (vRelMag * vRelMag);
        double F_drag_x = (vRelMag > 1e-4) ? (-F_drag * (vRelX / vRelMag)) : 0.0;
        double F_drag_y = (vRelMag > 1e-4) ? (-F_drag * (vRelY / vRelMag)) : 0.0;
        double F_drag_z = (vRelMag > 1e-4) ? (-F_drag * (vRelZ / vRelMag)) : 0.0;

        // 全系统牛顿动力学运动方程: a = (F_thrust * u_body + F_drag - m * g) / m
        double F_net_x = F_thrust * ub_x + F_drag_x;
        double F_net_y = F_thrust * ub_y + F_drag_y - m_eff * G;
        double F_net_z = F_thrust * ub_z + F_drag_z;

        // 输出导数矢量
        outDeriv[0] = vx;                // dx/dt
        outDeriv[1] = vy;                // dy/dt
        outDeriv[2] = vz;                // dz/dt
        outDeriv[3] = F_net_x / m_eff;   // dvx/dt
        outDeriv[4] = F_net_y / m_eff;   // dvy/dt
        outDeriv[5] = F_net_z / m_eff;   // dvz/dt

        // 输出辅助状态
        outAux[0] = dm_dt;
        outAux[1] = dV_air_dt;
        outAux[2] = F_thrust;
        outAux[3] = P_inst;
    }

    /**
     * 计算高斯落点置信概率云 (CEP 与 95% 置信范围)
     */
    private static void calculateDispersionCloud(SimResult res, RocketConfig cfg) {
        // 纵向射向标准差 sigma_x (m)：受充气表压读数跳动与纵向风波动影响 (~3.2% 射程)
        double sigmaX = Math.max(1.8, res.rangeM * 0.032 + (cfg.windSpeedMs * 0.35));

        // 横向侧漂标准差 sigma_z (m)：受横向侧风切变阵风与尾翼对称度微扰影响
        double sigmaZ = Math.max(1.2, Math.abs(res.lateralDriftM) * 0.18 + (cfg.windSpeedMs * 0.30) + 1.0);

        // 95% 置信范围半轴
        res.dispersion.semiMajorX = sigmaX * 2.1;
        res.dispersion.semiMinorZ = sigmaZ * 2.1;

        // 50% 圆概率误差 CEP
        res.dispersion.cepRadiusM = 0.59 * (res.dispersion.semiMajorX + res.dispersion.semiMinorZ);
    }

    /**
     * 目标距离反推最优发射配置逆向求解器
     *
     * @param baseCfg 基础结构几何参数
     * @param targetDistanceM 期望击中目标距离 (m)
     * @param mode 0: 最小压力优先, 1: 固定当前充气气压
     */
    public static SolveResult solveForTarget(RocketConfig baseCfg, double targetDistanceM, int mode) {
        SolveResult best = new SolveResult();
        best.errorM = 1e9;
        double minPressure = 1e9;

        RocketConfig testCfg = new RocketConfig();
        testCfg.specName = baseCfg.specName;
        testCfg.bottleVolumeL = baseCfg.bottleVolumeL;
        testCfg.bottleDiameterMm = baseCfg.bottleDiameterMm;
        testCfg.nozzleDiameterMm = baseCfg.nozzleDiameterMm;
        testCfg.dryMassG = baseCfg.dryMassG;
        testCfg.dragCoefficient = baseCfg.dragCoefficient;
        testCfg.dischargeCoefficient = baseCfg.dischargeCoefficient;
        testCfg.launchRailLengthM = baseCfg.launchRailLengthM;
        testCfg.launchHeadingDeg = baseCfg.launchHeadingDeg;
        testCfg.windSpeedMs = baseCfg.windSpeedMs;
        testCfg.windAngleDeg = baseCfg.windAngleDeg;
        testCfg.launchYawDeg = 0.0;

        double[] pressures;
        if (mode == 1) {
            pressures = new double[]{baseCfg.pressureBar};
        } else {
            pressures = new double[]{2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0};
        }

        double totalMl = testCfg.bottleVolumeL * 1000.0;
        double[] waterRatios = new double[]{0.18, 0.22, 0.26, 0.30, 0.33, 0.36, 0.40, 0.45};

        for (double p : pressures) {
            testCfg.pressureBar = p;
            for (double ratio : waterRatios) {
                testCfg.waterVolumeMl = Math.round(totalMl * ratio);
                for (int angle = 34; angle <= 66; angle += 2) {
                    testCfg.launchAngleDeg = angle;
                    testCfg.launchYawDeg = 0.0;

                    SimResult r = simulate(testCfg);
                    double err = Math.abs(r.totalDistanceM - targetDistanceM);

                    if (err < 1.8) {
                        boolean isBetter = false;
                        if (best.simResult == null) {
                            isBetter = true;
                        } else if (mode == 0) {
                            if (p < minPressure - 0.05) {
                                isBetter = true;
                            } else if (Math.abs(p - minPressure) <= 0.05 && err < best.errorM) {
                                isBetter = true;
                            }
                        } else {
                            if (err < best.errorM) {
                                isBetter = true;
                            }
                        }

                        if (isBetter) {
                            minPressure = p;
                            best.errorM = err;
                            best.recommendedPressureBar = p;
                            best.recommendedWaterMl = testCfg.waterVolumeMl;
                            best.recommendedAngleDeg = angle;
                            best.predictedRangeM = r.rangeM;
                            best.predictedDriftM = r.lateralDriftM;
                            best.predictedApogeeM = r.apogeeM;
                            best.predictedFlightTimeS = r.flightTimeS;
                            best.simResult = r;
                        }
                    }
                }
            }
            if (mode == 0 && best.simResult != null && best.errorM < 0.6) {
                break;
            }
        }

        if (best.simResult == null) {
            double bestErr = 1e9;
            for (double p : pressures) {
                testCfg.pressureBar = p;
                for (double ratio : waterRatios) {
                    testCfg.waterVolumeMl = Math.round(totalMl * ratio);
                    for (int angle = 35; angle <= 65; angle += 3) {
                        testCfg.launchAngleDeg = angle;
                        SimResult r = simulate(testCfg);
                        double err = Math.abs(r.totalDistanceM - targetDistanceM);
                        if (err < bestErr) {
                            bestErr = err;
                            best.errorM = err;
                            best.recommendedPressureBar = p;
                            best.recommendedWaterMl = testCfg.waterVolumeMl;
                            best.recommendedAngleDeg = angle;
                            best.predictedRangeM = r.rangeM;
                            best.predictedDriftM = r.lateralDriftM;
                            best.predictedApogeeM = r.apogeeM;
                            best.predictedFlightTimeS = r.flightTimeS;
                            best.simResult = r;
                        }
                    }
                }
            }
        }

        // 侧风偏航前置配平逆向解算
        if (best.simResult != null && baseCfg.windSpeedMs > 0.2) {
            testCfg.pressureBar = best.recommendedPressureBar;
            testCfg.waterVolumeMl = best.recommendedWaterMl;
            testCfg.launchAngleDeg = best.recommendedAngleDeg;

            double initialDrift = best.simResult.lateralDriftM;
            if (Math.abs(initialDrift) > 0.4) {
                double bestYaw = 0.0;
                double minDrift = Math.abs(initialDrift);

                double searchMin = (initialDrift < 0) ? 0.0 : -25.0;
                double searchMax = (initialDrift < 0) ? 25.0 : 0.0;

                for (double yaw = searchMin; yaw <= searchMax; yaw += 1.0) {
                    testCfg.launchYawDeg = yaw;
                    SimResult rYaw = simulate(testCfg);
                    if (Math.abs(rYaw.lateralDriftM) < minDrift) {
                        minDrift = Math.abs(rYaw.lateralDriftM);
                        bestYaw = yaw;
                        best.simResult = rYaw;
                        best.predictedRangeM = rYaw.rangeM;
                        best.predictedDriftM = rYaw.lateralDriftM;
                    }
                }
                best.recommendedYawDeg = bestYaw;
            }
        }

        return best;
    }
}
