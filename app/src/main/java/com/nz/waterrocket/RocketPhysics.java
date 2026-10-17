package com.nz.waterrocket;

import java.util.ArrayList;
import java.util.List;

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
        // Bottle specs
        public String specName = "1.5L 标准可乐瓶";
        public double bottleVolumeL = 1.5;         // L (0.50L, 1.25L, 1.50L, 2.00L)
        public double bottleDiameterMm = 90.0;     // mm (66mm for 500mL, 88mm for 1.25L, 90mm for 1.5L, 104mm for 2L)
        public double nozzleDiameterMm = 21.0;     // mm (standard bottle mouth ~21mm)
        public double dryMassG = 150.0;            // g (dry weight)
        public double dragCoefficient = 0.35;      // Cd (adjustable: 0.15 .. 0.70)
        public double launchRailLengthM = 1.0;      // m

        // Launch attitude
        public double launchAngleDeg = 45.0;       // pitch angle (30..85 deg)
        public double launchHeadingDeg = 90.0;     // True North launch heading azimuth (0..360 deg, 0=N, 90=E, 180=S, 270=W)
        public double launchYawDeg = 0.0;          // yaw aiming offset (-25..+25 deg for crosswind trim)

        // Propulsion
        public double pressureBar = 4.5;           // gauge pressure in bar
        public double waterVolumeMl = 500.0;       // ml

        // 3D Environment wind field
        public double windSpeedMs = 0.0;           // m/s
        public double meteoWindDirectionDeg = 0.0; // Meteorological wind direction (where wind comes from, 0=North, 90=East)
        public double windAngleDeg = 0.0;          // Relative wind angle to launch axis (calculated automatically or manual)
    }

    // Preset bottle specifications
    public static void applyBottlePreset(RocketConfig cfg, int presetId) {
        switch (presetId) {
            case 0: // 500mL Mini Coke bottle (Experimental Spec 试验规格)
                cfg.specName = "500mL 试验小可乐瓶";
                cfg.bottleVolumeL = 0.50;
                cfg.bottleDiameterMm = 66.0;   // slender small bottle diameter
                cfg.dryMassG = 85.0;           // lightweight airframe
                cfg.dragCoefficient = 0.28;    // lower drag coefficient due to smaller frontal area
                cfg.waterVolumeMl = 165.0;     // ~33% water
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
    }

    // Inverse solver recommendation result
    public static class SolveResult {
        public double recommendedPressureBar;
        public double recommendedWaterMl;
        public double recommendedAngleDeg;
        public double recommendedYawDeg;           // Recommended yaw aiming offset to null crosswind drift
        public double predictedRangeM;
        public double predictedDriftM;
        public double predictedApogeeM;
        public double predictedFlightTimeS;
        public double errorM;
        public SimResult simResult;
    }

    public static SimResult simulate(RocketConfig cfg) {
        SimResult res = new SimResult();

        double V_bottle = cfg.bottleVolumeL * 0.001; // m^3
        double V_water0 = cfg.waterVolumeMl * 0.000001; // m^3
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

        // Launch attitude angles (Pitch theta, Yaw psi)
        double thetaRad = Math.toRadians(cfg.launchAngleDeg);
        double psiRad = Math.toRadians(cfg.launchYawDeg);

        double uLaunchX = Math.cos(thetaRad) * Math.cos(psiRad);
        double uLaunchY = Math.sin(thetaRad);
        double uLaunchZ = Math.cos(thetaRad) * Math.sin(psiRad);

        // Calculate relative wind angle between meteorological wind direction and rocket launch heading
        // relativeAngle = (meteoWind - launchHeading + 360) % 360
        double relativeWindAngle = cfg.windAngleDeg;

        double windAngleRad = Math.toRadians(relativeWindAngle);
        double windVx = -cfg.windSpeedMs * Math.cos(windAngleRad);
        double windVz = -cfg.windSpeedMs * Math.sin(windAngleRad);

        double t = 0.0;
        double dt = 0.001;
        double x = 0.0, y = 0.0, z = 0.0;
        double vx = 0.0, vy = 0.0, vz = 0.0;

        double V_water = V_water0;
        double V_air = V_air0;

        int phase = 0; // 0: water, 1: air, 2: coast
        double t_water_end = 0.0;
        double t_air_end = 0.0;

        double maxAlt = 0.0;
        double maxSpeed = 0.0;

        res.trajectory.add(new TrajectoryPoint(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0));
        int sampleCounter = 0;

        while (t < 60.0) {
            double vMag = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vMag > maxSpeed) maxSpeed = vMag;
            if (y > maxAlt) maxAlt = y;

            double ux, uy, uz;
            double distRail = Math.sqrt(x * x + y * y + z * z);

            double vRelX = vx - windVx;
            double vRelY = vy;
            double vRelZ = vz - windVz;
            double vRelMag = Math.sqrt(vRelX * vRelX + vRelY * vRelY + vRelZ * vRelZ);

            if (distRail < cfg.launchRailLengthM) {
                ux = uLaunchX;
                uy = uLaunchY;
                uz = uLaunchZ;
            } else if (vRelMag > 0.2) {
                ux = vRelX / vRelMag;
                uy = vRelY / vRelMag;
                uz = vRelZ / vRelMag;
            } else {
                ux = uLaunchX;
                uy = uLaunchY;
                uz = uLaunchZ;
            }

            // Aerodynamic drag in 3D
            double dragMag = 0.5 * RHO_AIR * Cd * A_cross * (vRelMag * vRelMag);
            double F_drag_x = (vRelMag > 1e-4) ? -dragMag * (vRelX / vRelMag) : 0.0;
            double F_drag_y = (vRelMag > 1e-4) ? -dragMag * (vRelY / vRelMag) : 0.0;
            double F_drag_z = (vRelMag > 1e-4) ? -dragMag * (vRelZ / vRelMag) : 0.0;

            double F_thrust = 0.0;
            double m_total;

            if (phase == 0) {
                // Water expulsion
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
                // Air expulsion
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
                        ve = Math.sqrt(Math.max(0.0, 2.0 * GAMMA * R_AIR * T / (GAMMA - 1.0) * (1.0 - Math.pow(pr, (GAMMA - 1.0) / GAMMA))));
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
                // Coasting
                m_total = m_dry;
                F_thrust = 0.0;
            }

            double F_thrust_x = F_thrust * ux;
            double F_thrust_y = F_thrust * uy;
            double F_thrust_z = F_thrust * uz;

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

        return res;
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

        // Fallback search
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

        // Crosswind compensation: calculate recommended yaw correction angle
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
