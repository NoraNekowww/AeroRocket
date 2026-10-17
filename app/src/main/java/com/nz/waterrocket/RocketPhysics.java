package com.nz.waterrocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RocketPhysics {

    public static final double RHO_WATER = 1000.0;     // kg/m^3
    public static final double RHO_AIR = 1.205;        // kg/m^3
    public static final double P_ATM = 101325.0;       // Pa
    public static final double GAMMA = 1.4;            // air adiabatic index
    public static final double R_AIR = 287.05;         // J/(kg*K)
    public static final double T0 = 293.15;            // K (~20C)
    public static final double G = 9.80665;            // m/s^2

    // 3D Trajectory point
    public static class TrajectoryPoint {
        public final float x;       // downrange forward along launch azimuth (m)
        public final float y;       // altitude vertical (m)
        public final float z;       // lateral crossrange drift (m, + right, - left)
        public final float vx;
        public final float vy;
        public final float vz;
        public final float time;
        public final int phase;     // 0: Water thrust, 1: Air blowdown, 2: Coast

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
    }

    // 3D Simulation configuration
    public static class RocketConfig {
        public String specName = "1.5L 标准可乐瓶";
        public double bottleVolumeL = 1.5;         // L
        public double bottleDiameterMm = 90.0;     // mm
        public double nozzleDiameterMm = 21.0;     // mm
        public double dryMassG = 150.0;            // g
        public double dragCoefficient = 0.35;      // Cd (0.15 .. 0.70)
        public double launchRailLengthM = 1.0;      // m

        // Launch attitude
        public double launchAngleDeg = 45.0;       // pitch angle (30..85 deg)
        public double launchHeadingDeg = 90.0;     // True North launch heading azimuth (0..360 deg)
        public double launchYawDeg = 0.0;          // yaw aiming offset (-25..+25 deg for crosswind trim)

        // Propulsion
        public double pressureBar = 4.5;           // gauge pressure in bar
        public double waterVolumeMl = 500.0;       // ml

        // 3D Environment wind field
        public double windSpeedMs = 0.0;           // m/s
        public double meteoWindDirectionDeg = 0.0; // Meteorological wind direction (where wind comes from)
        public double windAngleDeg = 0.0;          // Relative wind angle to launch axis
    }

    public static void applyBottlePreset(RocketConfig cfg, int presetId) {
        switch (presetId) {
            case 0: // 500mL Mini Coke bottle (Experimental Spec)
                cfg.specName = "500mL 试验小可乐瓶";
                cfg.bottleVolumeL = 0.50;
                cfg.bottleDiameterMm = 66.0;
                cfg.dryMassG = 85.0;
                cfg.dragCoefficient = 0.28;
                cfg.waterVolumeMl = 165.0;
                break;
            case 1: // 1.25L Medium
                cfg.specName = "1.25L 中号可乐瓶";
                cfg.bottleVolumeL = 1.25;
                cfg.bottleDiameterMm = 88.0;
                cfg.dryMassG = 130.0;
                cfg.dragCoefficient = 0.33;
                cfg.waterVolumeMl = 410.0;
                break;
            case 2: // 1.50L Standard Large
            default:
                cfg.specName = "1.50L 标准大可乐瓶";
                cfg.bottleVolumeL = 1.50;
                cfg.bottleDiameterMm = 90.0;
                cfg.dryMassG = 150.0;
                cfg.dragCoefficient = 0.35;
                cfg.waterVolumeMl = 500.0;
                break;
            case 3: // 2.00L Heavy Duty
                cfg.specName = "2.00L 加大重型可乐瓶";
                cfg.bottleVolumeL = 2.00;
                cfg.bottleDiameterMm = 104.0;
                cfg.dryMassG = 185.0;
                cfg.dragCoefficient = 0.38;
                cfg.waterVolumeMl = 660.0;
                break;
        }
    }

    // Dispersion Probability Cloud (Electron cloud model for landing point uncertainty)
    public static class DispersionCloud {
        public double semiMajorX;           // Longitudinal 95% error boundary (m)
        public double semiMinorZ;           // Lateral crosswind 95% error boundary (m)
        public double cepRadiusM;           // Circular Error Probable 50% (m)
        public List<float[]> cloudParticles = new ArrayList<>(); // Relative offsets (dx, dz) for rendering cloud points
    }

    // Simulation output metrics
    public static class SimResult {
        public double rangeM;                      // Downrange X (m)
        public double lateralDriftM;               // Crossrange lateral drift Z (m)
        public double totalDistanceM;              // Total ground displacement sqrt(X^2 + Z^2) (m)
        public double deflectionDeg;               // Deflection drift angle from centerline (deg)
        public double apogeeM;                     // Max altitude Y (m)
        public double maxSpeedMs;                  // Max speed (m/s)
        public double maxSpeedKmh;                 // Max speed (km/h)
        public double flightTimeS;                 // Total flight time (s)
        public double waterBurnTimeMs;             // Duration of water phase (ms)
        public double airBurnTimeMs;               // Duration of air phase (ms)
        public double impactSpeedMs;               // Impact speed (m/s)
        public double impactEnergyJ;               // Impact kinetic energy (J)
        public List<TrajectoryPoint> trajectory = new ArrayList<>();
        public DispersionCloud dispersion = new DispersionCloud(); // Electron cloud uncertainty
    }

    // Inverse solver recommendation result
    public static class SolveResult {
        public double recommendedPressureBar;
        public double recommendedWaterMl;
        public double recommendedAngleDeg;
        public double recommendedYawDeg;
        public double predictedRangeM;
        public double predictedDriftM;
        public double predictedApogeeM;
        public double predictedFlightTimeS;
        public double errorM;
        public SimResult simResult;
    }

    public static SimResult simulate(RocketConfig cfg) {
        SimResult res = new SimResult();

        double V_bottle = cfg.bottleVolumeL * 0.001;
        double V_water0 = cfg.waterVolumeMl * 0.000001;
        if (V_water0 >= V_bottle) V_water0 = V_bottle * 0.95;
        if (V_water0 <= 0.0) V_water0 = 0.00001;
        double V_air0 = V_bottle - V_water0;

        double d_throat = cfg.nozzleDiameterMm * 0.001;
        double A_throat = Math.PI * (d_throat * 0.5) * (d_throat * 0.5);

        double d_body = cfg.bottleDiameterMm * 0.001;
        double A_cross = Math.PI * (d_body * 0.5) * (d_body * 0.5);

        double m_dry = cfg.dryMassG * 0.001;
        double Cd = Math.max(0.05, cfg.dragCoefficient);

        double P0_abs = cfg.pressureBar * 100000.0 + P_ATM;
        double P = P0_abs;

        double thetaRad = Math.toRadians(cfg.launchAngleDeg);
        double psiRad = Math.toRadians(cfg.launchYawDeg);

        double uLaunchX = Math.cos(thetaRad) * Math.cos(psiRad);
        double uLaunchY = Math.sin(thetaRad);
        double uLaunchZ = Math.cos(thetaRad) * Math.sin(psiRad);

        double windAngleRad = Math.toRadians(cfg.windAngleDeg);
        double windVx = -cfg.windSpeedMs * Math.cos(windAngleRad);
        double windVz = -cfg.windSpeedMs * Math.sin(windAngleRad);

        double t = 0.0;
        double dt = 0.001;
        double x = 0.0, y = 0.0, z = 0.0;
        double vx = 0.0, vy = 0.0, vz = 0.0;

        double V_water = V_water0;
        double V_air = V_air0;

        int phase = 0;
        double t_water_end = 0.0;
        double t_air_end = 0.0;

        double maxAlt = 0.0;
        double maxSpeed = 0.0;

        double ub_x = uLaunchX;
        double ub_y = uLaunchY;
        double ub_z = uLaunchZ;

        res.trajectory.add(new TrajectoryPoint(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0));
        int sampleCounter = 0;

        while (t < 60.0) {
            double vMag = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vMag > maxSpeed) maxSpeed = vMag;
            if (y > maxAlt) maxAlt = y;

            double vRelX = vx - windVx;
            double vRelY = vy;
            double vRelZ = vz - windVz;
            double vRelMag = Math.sqrt(vRelX * vRelX + vRelY * vRelY + vRelZ * vRelZ);

            double distRail = Math.sqrt(x * x + y * y + z * z);

            // Attitude dynamics: inside rail rigidly fixed; outside rail tracks velocity tangent with thrust stiffness
            if (distRail < cfg.launchRailLengthM) {
                ub_x = uLaunchX;
                ub_y = uLaunchY;
                ub_z = uLaunchZ;
            } else if (vMag > 0.5) {
                double tau = (phase == 2) ? 0.15 : 0.45;
                double alpha = 1.0 - Math.exp(-dt / tau);

                double targetUx = vx / vMag;
                double targetUy = vy / vMag;
                double targetUz = vz / vMag;

                if (vRelMag > 0.5) {
                    targetUx = 0.85 * (vx / vMag) + 0.15 * (vRelX / vRelMag);
                    targetUy = 0.85 * (vy / vMag) + 0.15 * (vRelY / vRelMag);
                    targetUz = 0.85 * (vz / vMag) + 0.15 * (vRelZ / vRelMag);
                    double normT = Math.sqrt(targetUx * targetUx + targetUy * targetUy + targetUz * targetUz);
                    targetUx /= normT;
                    targetUy /= normT;
                    targetUz /= normT;
                }

                ub_x += (targetUx - ub_x) * alpha;
                ub_y += (targetUy - ub_y) * alpha;
                ub_z += (targetUz - ub_z) * alpha;

                double normB = Math.sqrt(ub_x * ub_x + ub_y * ub_y + ub_z * ub_z);
                ub_x /= normB;
                ub_y /= normB;
                ub_z /= normB;
            }

            double dragMag = 0.5 * RHO_AIR * Cd * A_cross * (vRelMag * vRelMag);
            double F_drag_x = (vRelMag > 1e-4) ? -dragMag * (vRelX / vRelMag) : 0.0;
            double F_drag_y = (vRelMag > 1e-4) ? -dragMag * (vRelY / vRelMag) : 0.0;
            double F_drag_z = (vRelMag > 1e-4) ? -dragMag * (vRelZ / vRelMag) : 0.0;

            double F_thrust = 0.0;
            double m_total;

            if (phase == 0) {
                m_total = m_dry + RHO_WATER * V_water;
                if (P > P_ATM && V_water > 0.0) {
                    double c_dis = 0.96;
                    double ve = c_dis * Math.sqrt(Math.max(0.0, 2.0 * (P - P_ATM) / RHO_WATER));
                    double flowRate = A_throat * ve;
                    double dVw = flowRate * dt;
                    if (dVw > V_water) dVw = V_water;
                    V_water -= dVw;
                    V_air += dVw;
                    P = P0_abs * Math.pow(V_air0 / V_air, GAMMA);
                    F_thrust = flowRate * RHO_WATER * ve + (P - P_ATM) * A_throat;
                } else {
                    phase = 1;
                    t_water_end = t;
                }
            } else if (phase == 1) {
                m_total = m_dry;
                if (P > P_ATM) {
                    double pr = P_ATM / P;
                    double crit_pr = 0.52828;
                    double T = T0 * Math.pow(P / P0_abs, (GAMMA - 1.0) / GAMMA);
                    double mdot, ve;

                    if (pr < crit_pr) {
                        mdot = A_throat * P * Math.sqrt(GAMMA / (R_AIR * T)) * 0.5787;
                        ve = Math.sqrt(GAMMA * R_AIR * T * (2.0 / (GAMMA + 1.0)));
                    } else {
                        double term = Math.max(0.0, Math.pow(pr, 2.0 / GAMMA) - Math.pow(pr, (GAMMA + 1.0) / GAMMA));
                        mdot = A_throat * P * Math.sqrt((2.0 * GAMMA / ((GAMMA - 1.0) * R_AIR * T)) * term);
                        ve = Math.sqrt(Math.max(0.0, 2 * GAMMA * R_AIR * T / (GAMMA - 1.0) * (1.0 - Math.pow(pr, (GAMMA - 1.0) / GAMMA))));
                    }
                    F_thrust = mdot * ve + (P - P_ATM) * A_throat;
                    double dP = -(GAMMA * P / V_bottle) * (mdot / (P / (R_AIR * T))) * dt;
                    P += dP;
                    if (P <= P_ATM) {
                        phase = 2;
                        t_air_end = t;
                        P = P_ATM;
                    }
                } else {
                    phase = 2;
                    t_air_end = t;
                }
            } else {
                m_total = m_dry;
                F_thrust = 0.0;
            }

            double F_thrust_x = F_thrust * ub_x;
            double F_thrust_y = F_thrust * ub_y;
            double F_thrust_z = F_thrust * ub_z;

            double ax = (F_thrust_x + F_drag_x) / m_total;
            double ay = (F_thrust_y + F_drag_y) / m_total - G;
            double az = (F_thrust_z + F_drag_z) / m_total;

            vx += ax * dt;
            vy += ay * dt;
            vz += az * dt;
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            t += dt;

            sampleCounter++;
            if (sampleCounter % 15 == 0 || phase != res.trajectory.get(res.trajectory.size() - 1).phase) {
                res.trajectory.add(new TrajectoryPoint((float) x, (float) Math.max(0.0, y), (float) z, (float) vx, (float) vy, (float) vz, (float) t, phase));
            }

            if (y < 0.0 && t > 0.05) {
                double prevY = y - vy * dt;
                double fraction = (prevY > 0.0 && vy != 0.0) ? (prevY / (prevY - y)) : 0.0;
                x = (x - vx * dt) + vx * dt * fraction;
                z = (z - vz * dt) + vz * dt * fraction;
                y = 0.0;
                res.trajectory.add(new TrajectoryPoint((float) x, 0f, (float) z, (float) vx, (float) vy, (float) vz, (float) t, phase));
                break;
            }
        }

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

        // =====================================================================
        // CALCULATE ELECTRON CLOUD DISPERSION (落点概率散布云模型)
        // =====================================================================
        calculateDispersionCloud(res, cfg);

        return res;
    }

    /**
     * Compute Gaussian dispersion error cloud representing environmental gust,
     * pressure gauge error, and water volume uncertainty (like an electron cloud probability density).
     */
    private static void calculateDispersionCloud(SimResult res, RocketConfig cfg) {
        // Base standard deviations based on real rocket physics
        // Longitudinal dispersion sigma_x (m): influenced by wind speed variations and pressure fluctuation (~3.5% of range)
        double sigmaX = Math.max(2.0, res.rangeM * 0.035 + (cfg.windSpeedMs * 0.4));

        // Lateral dispersion sigma_z (m): influenced by crosswind gusts and fin asymmetry (~2.2% of range)
        double sigmaZ = Math.max(1.5, Math.abs(res.lateralDriftM) * 0.20 + (cfg.windSpeedMs * 0.35) + 1.2);

        // 95% Confidence Ellipse boundaries (2.4477 * sigma for 2D Rayleigh/Gaussian 95%)
        res.dispersion.semiMajorX = sigmaX * 2.1;
        res.dispersion.semiMinorZ = sigmaZ * 2.1;
        res.dispersion.cepRadiusM = 0.59 * (res.dispersion.semiMajorX + res.dispersion.semiMinorZ);

        // Generate synthetic Monte-Carlo probability cloud particles (electron cloud dots)
        Random rnd = new Random((long) (res.rangeM * 1000 + res.lateralDriftM * 10));
        res.dispersion.cloudParticles.clear();
        int particleCount = 42;
        for (int i = 0; i < particleCount; i++) {
            // Box-Muller normal distribution
            double u1 = Math.max(1e-6, rnd.nextDouble());
            double u2 = rnd.nextDouble();
            double randNormX = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
            double randNormZ = Math.sqrt(-2.0 * Math.log(u1)) * Math.sin(2.0 * Math.PI * u2);

            float dx = (float) (randNormX * sigmaX * 0.95);
            float dz = (float) (randNormZ * sigmaZ * 0.95);
            res.dispersion.cloudParticles.add(new float[]{dx, dz});
        }
    }

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

        if (best.simResult != null && baseCfg.windSpeedMs > 0.2) {
            testCfg.pressureBar = best.recommendedPressureBar;
            testCfg.waterVolumeMl = best.recommendedWaterMl;
            testCfg.launchAngleDeg = best.recommendedAngleDeg;

            double initialDrift = best.simResult.lateralDriftM;
            if (Math.abs(initialDrift) > 0.5) {
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
