package com.nz.waterrocket;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_LOCATION = 1001;

    private RocketPhysics.RocketConfig config = new RocketPhysics.RocketConfig();
    private RocketPhysics.SolveResult lastSolveResult;

    // UI elements
    private TrajectoryView trajectoryView;
    private TextView tvEcmwfStatus;
    private View viewEcmwfDot;
    private View chipEcmwf;

    private TextView hudRange;
    private TextView hudDrift;
    private TextView hudApogee;
    private TextView hudSpeed;
    private TextView hudTime;

    private RadioGroup rgBottlePreset;
    private SeekBar sbCd;
    private TextView tvCdVal;
    private SeekBar sbPressure;
    private TextView tvPressureVal;
    private SeekBar sbWater;
    private TextView tvWaterVal;
    private RadioGroup rgNozzle;

    private SeekBar sbAngle;
    private TextView tvAngleVal;
    private SeekBar sbHeading;
    private TextView tvHeadingVal;
    private SeekBar sbYaw;
    private TextView tvYawVal;

    private RadioGroup rgWindMode;
    private RadioButton rbWindEcmwf;
    private RadioButton rbWindManual;
    private SeekBar sbWindSpeed;
    private TextView tvWindSpeedVal;
    private SeekBar sbWindAngle;
    private TextView tvWindAngleVal;
    private TextView tvCrosswindBreakdown;
    private Button btnRefreshLocation;

    private EditText etTargetDist;
    private RadioGroup rgSolveMode;
    private Button btnSolveTarget;
    private View boxSolveResult;
    private TextView tvSolveSummary;
    private Button btnApplySolve;

    private TextView tvTotalGroundDist;
    private TextView tvDriftAngle;
    private TextView tvBurnWater;
    private TextView tvBurnAir;
    private TextView tvImpactEnergy;

    private boolean isEcmwfAuto = true;
    private boolean isUpdatingUi = false;

    // Last known coordinate for real satellite fetching
    private double currentLat = 38.998;
    private double currentLon = 117.315;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        requestLocationAndFetchEcmwf();
        fetchRealSatelliteMap(currentLat, currentLon);
        updateSimulation();
    }

    private void initViews() {
        trajectoryView = findViewById(R.id.trajectory_view);
        tvEcmwfStatus = findViewById(R.id.tv_ecmwf_status);
        viewEcmwfDot = findViewById(R.id.view_ecmwf_dot);
        chipEcmwf = findViewById(R.id.chip_ecmwf);

        hudRange = findViewById(R.id.hud_range);
        hudDrift = findViewById(R.id.hud_drift);
        hudApogee = findViewById(R.id.hud_apogee);
        hudSpeed = findViewById(R.id.hud_speed);
        hudTime = findViewById(R.id.hud_time);

        rgBottlePreset = findViewById(R.id.rg_bottle_preset);
        sbCd = findViewById(R.id.sb_cd);
        tvCdVal = findViewById(R.id.tv_cd_val);
        sbPressure = findViewById(R.id.sb_pressure);
        tvPressureVal = findViewById(R.id.tv_pressure_val);
        sbWater = findViewById(R.id.sb_water);
        tvWaterVal = findViewById(R.id.tv_water_val);
        rgNozzle = findViewById(R.id.rg_nozzle);

        sbAngle = findViewById(R.id.sb_angle);
        tvAngleVal = findViewById(R.id.tv_angle_val);
        sbHeading = findViewById(R.id.sb_heading);
        tvHeadingVal = findViewById(R.id.tv_heading_val);
        sbYaw = findViewById(R.id.sb_yaw);
        tvYawVal = findViewById(R.id.tv_yaw_val);

        rgWindMode = findViewById(R.id.rg_wind_mode);
        rbWindEcmwf = findViewById(R.id.rb_wind_ecmwf);
        rbWindManual = findViewById(R.id.rb_wind_manual);
        sbWindSpeed = findViewById(R.id.sb_wind_speed);
        tvWindSpeedVal = findViewById(R.id.tv_wind_speed_val);
        sbWindAngle = findViewById(R.id.sb_wind_angle);
        tvWindAngleVal = findViewById(R.id.tv_wind_angle_val);
        tvCrosswindBreakdown = findViewById(R.id.tv_crosswind_breakdown);
        btnRefreshLocation = findViewById(R.id.btn_refresh_location);

        etTargetDist = findViewById(R.id.et_target_dist);
        rgSolveMode = findViewById(R.id.rg_solve_mode);
        btnSolveTarget = findViewById(R.id.btn_solve_target);
        boxSolveResult = findViewById(R.id.box_solve_result);
        tvSolveSummary = findViewById(R.id.tv_solve_summary);
        btnApplySolve = findViewById(R.id.btn_apply_solve);

        tvTotalGroundDist = findViewById(R.id.tv_total_ground_dist);
        tvDriftAngle = findViewById(R.id.tv_drift_angle);
        tvBurnWater = findViewById(R.id.tv_burn_water);
        tvBurnAir = findViewById(R.id.tv_burn_air);
        tvImpactEnergy = findViewById(R.id.tv_impact_energy);
    }

    private void setupListeners() {
        rgBottlePreset.setOnCheckedChangeListener((group, checkedId) -> {
            if (isUpdatingUi) return;
            if (checkedId == R.id.rb_spec_500) {
                RocketPhysics.applyBottlePreset(config, 0);
            } else if (checkedId == R.id.rb_spec_1250) {
                RocketPhysics.applyBottlePreset(config, 1);
            } else if (checkedId == R.id.rb_spec_1500) {
                RocketPhysics.applyBottlePreset(config, 2);
            } else if (checkedId == R.id.rb_spec_2000) {
                RocketPhysics.applyBottlePreset(config, 3);
            }

            isUpdatingUi = true;
            sbCd.setProgress((int) Math.round((config.dragCoefficient - 0.15) * 100.0));
            sbWater.setMax((int) (config.bottleVolumeL * 1000.0 * 0.70));
            sbWater.setProgress((int) Math.round(config.waterVolumeMl));
            isUpdatingUi = false;

            updateSimulation();
        });

        sbCd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.dragCoefficient = 0.15 + progress * 0.01;
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbPressure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.pressureBar = 1.0 + progress * 0.1;
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbWater.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.waterVolumeMl = Math.max(30, progress);
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        rgNozzle.setOnCheckedChangeListener((group, checkedId) -> {
            if (isUpdatingUi) return;
            if (checkedId == R.id.rb_nozzle_21) {
                config.nozzleDiameterMm = 21.0;
            } else {
                config.nozzleDiameterMm = 9.0;
            }
            updateSimulation();
        });

        sbAngle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.launchAngleDeg = 30 + progress;
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbHeading.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.launchHeadingDeg = progress;
                recalculateRelativeWind();
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbYaw.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.launchYawDeg = progress - 25;
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        rgWindMode.setOnCheckedChangeListener((group, checkedId) -> {
            isEcmwfAuto = (checkedId == R.id.rb_wind_ecmwf);
            sbWindSpeed.setEnabled(!isEcmwfAuto);
            sbWindAngle.setEnabled(!isEcmwfAuto);
            if (isEcmwfAuto) {
                requestLocationAndFetchEcmwf();
            }
        });

        sbWindSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.windSpeedMs = progress * 0.1;
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbWindAngle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isUpdatingUi) return;
                config.meteoWindDirectionDeg = progress;
                recalculateRelativeWind();
                updateSimulation();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnRefreshLocation.setOnClickListener(v -> requestLocationAndFetchEcmwf());
        chipEcmwf.setOnClickListener(v -> requestLocationAndFetchEcmwf());

        btnSolveTarget.setOnClickListener(v -> handleSolveTarget());
        btnApplySolve.setOnClickListener(v -> handleApplySolve());
    }

    private void recalculateRelativeWind() {
        config.windAngleDeg = (config.meteoWindDirectionDeg - config.launchHeadingDeg + 360.0) % 360.0;
    }

    private void requestLocationAndFetchEcmwf() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQ_LOCATION);
            return;
        }

        executeLocationEcmwfFetch();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                executeLocationEcmwfFetch();
            } else {
                viewEcmwfDot.setBackgroundColor(0xFFF43F5E);
                tvEcmwfStatus.setText("ECMWF: 获取失败(无权限)");
                Toast.makeText(this, "定位权限被拒绝，无法获取当前位置风速", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void executeLocationEcmwfFetch() {
        tvEcmwfStatus.setText("当前定位同步中...");
        viewEcmwfDot.setBackgroundColor(0xFFFBBF24);

        WeatherService.fetchEcmwfByDeviceLocation(this, new WeatherService.WeatherCallback() {
            @Override
            public void onSuccess(WeatherService.WeatherData data) {
                viewEcmwfDot.setBackgroundColor(0xFF34D399);
                tvEcmwfStatus.setText(String.format(Locale.US, "ECMWF: %.1fm/s · %s", data.windSpeedMs, data.locationName));

                currentLat = data.latitude;
                currentLon = data.longitude;
                fetchRealSatelliteMap(currentLat, currentLon);

                if (isEcmwfAuto) {
                    isUpdatingUi = true;
                    config.windSpeedMs = data.windSpeedMs;
                    config.meteoWindDirectionDeg = data.windDirectionDeg;
                    recalculateRelativeWind();

                    sbWindSpeed.setProgress((int) (config.windSpeedMs * 10));
                    sbWindAngle.setProgress((int) config.meteoWindDirectionDeg);
                    isUpdatingUi = false;

                    updateSimulation();
                }
            }

            @Override
            public void onError(String errorMsg) {
                viewEcmwfDot.setBackgroundColor(0xFFF43F5E);
                tvEcmwfStatus.setText("ECMWF: 获取失败");
                Toast.makeText(MainActivity.this, "气象风速获取失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Real-time fetch satellite imagery from open map service (ESRI World Imagery)
     */
    private void fetchRealSatelliteMap(double lat, double lon) {
        SatelliteMapFetcher.fetchSatelliteMap(this, lat, lon, new SatelliteMapFetcher.SatelliteCallback() {
            @Override
            public void onSuccess(SatelliteMapFetcher.SatelliteMapResult result) {
                trajectoryView.setRealSatelliteMap(result);
                Toast.makeText(MainActivity.this, "真实卫星底图加载完成", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMsg) {
                // If offline or failed, user can still use procedural satellite ground or grid
            }
        });
    }

    private String getCompassHeadingName(double deg) {
        double d = (deg % 360.0 + 360.0) % 360.0;
        if (d >= 337.5 || d < 22.5) return "正北 N";
        if (d < 67.5) return "东北 NE";
        if (d < 112.5) return "正东 E";
        if (d < 157.5) return "东南 SE";
        if (d < 202.5) return "正南 S";
        if (d < 247.5) return "西南 SW";
        if (d < 292.5) return "正西 W";
        return "西北 NW";
    }

    private void updateSimulation() {
        recalculateRelativeWind();
        RocketPhysics.SimResult res = RocketPhysics.simulate(config);

        hudRange.setText(String.format(Locale.US, "%.1fm", res.rangeM));
        String driftSign = (res.lateralDriftM >= 0) ? "+" : "-";
        hudDrift.setText(String.format(Locale.US, "%s%.1fm", driftSign, Math.abs(res.lateralDriftM)));
        hudApogee.setText(String.format(Locale.US, "%.1fm", res.apogeeM));
        hudSpeed.setText(String.format(Locale.US, "%.0fkm/h", res.maxSpeedKmh));
        hudTime.setText(String.format(Locale.US, "%.1fs", res.flightTimeS));

        tvCdVal.setText(String.format(Locale.US, "%.2f", config.dragCoefficient));
        tvPressureVal.setText(String.format(Locale.US, "%.1f bar", config.pressureBar));
        double waterRatio = (config.waterVolumeMl / (config.bottleVolumeL * 1000.0)) * 100.0;
        tvWaterVal.setText(String.format(Locale.US, "%.0fmL (%.0f%%)", config.waterVolumeMl, waterRatio));
        tvAngleVal.setText(String.format(Locale.US, "%.0f°", config.launchAngleDeg));

        tvHeadingVal.setText(String.format(Locale.US, "%03.0f° (%s)", config.launchHeadingDeg, getCompassHeadingName(config.launchHeadingDeg)));

        if (config.launchYawDeg == 0) {
            tvYawVal.setText("0° (对准)");
        } else if (config.launchYawDeg > 0) {
            tvYawVal.setText(String.format(Locale.US, "+%.0f° (偏右)", config.launchYawDeg));
        } else {
            tvYawVal.setText(String.format(Locale.US, "%.0f° (偏左)", config.launchYawDeg));
        }

        tvWindSpeedVal.setText(String.format(Locale.US, "%.1f m/s", config.windSpeedMs));
        tvWindAngleVal.setText(String.format(Locale.US, "%03.0f° (%s)", config.meteoWindDirectionDeg, getCompassHeadingName(config.meteoWindDirectionDeg)));

        double rad = Math.toRadians(config.windAngleDeg);
        double headComponent = config.windSpeedMs * Math.cos(rad);
        double crossComponent = config.windSpeedMs * Math.sin(rad);

        String longStr = (Math.abs(headComponent) < 0.2) ? "纵向无风" :
                (headComponent > 0) ? String.format(Locale.US, "逆风 %.1fm/s", headComponent) :
                String.format(Locale.US, "顺风 %.1fm/s", Math.abs(headComponent));

        String crossStr = (Math.abs(crossComponent) < 0.2) ? "横向无侧风" :
                (crossComponent > 0) ? String.format(Locale.US, "右侧风 %.1fm/s", crossComponent) :
                String.format(Locale.US, "左侧风 %.1fm/s", Math.abs(crossComponent));

        tvCrosswindBreakdown.setText(String.format(Locale.US, "矢量分解: %s · %s (夹角 %.0f°)", longStr, crossStr, config.windAngleDeg));

        tvTotalGroundDist.setText(String.format(Locale.US, "地面直线位移: %.1f m", res.totalDistanceM));
        tvDriftAngle.setText(String.format(Locale.US, "侧偏航向角: %.1f°", res.deflectionDeg));
        tvBurnWater.setText(String.format(Locale.US, "喷水推力: %.1f ms", res.waterBurnTimeMs));
        tvBurnAir.setText(String.format(Locale.US, "排气推力: %.1f ms", res.airBurnTimeMs));
        tvImpactEnergy.setText(String.format(Locale.US, "落地速度: %.1f m/s · 动能: %.1f J", res.impactSpeedMs, res.impactEnergyJ));

        trajectoryView.setSimResult(res, config.launchAngleDeg, config.launchHeadingDeg, config.launchYawDeg, config.windSpeedMs, config.windAngleDeg);
    }

    private void handleSolveTarget() {
        String inputStr = etTargetDist.getText().toString().trim();
        if (TextUtils.isEmpty(inputStr)) {
            Toast.makeText(this, "请输入目标射程距离", Toast.LENGTH_SHORT).show();
            return;
        }

        double targetDist;
        try {
            targetDist = Double.parseDouble(inputStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "目标距离格式有误", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetDist <= 5.0 || targetDist > 300.0) {
            Toast.makeText(this, "目标距离超出常规可乐瓶水火箭射程范围 (5m ~ 300m)", Toast.LENGTH_SHORT).show();
            return;
        }

        int solveMode = (rgSolveMode.getCheckedRadioButtonId() == R.id.rb_solve_min_p) ? 0 : 1;

        btnSolveTarget.setEnabled(false);
        btnSolveTarget.setText("3D 逆解与侧风补偿计算中...");

        new Thread(() -> {
            recalculateRelativeWind();
            RocketPhysics.SolveResult result = RocketPhysics.solveForTarget(config, targetDist, solveMode);
            lastSolveResult = result;

            runOnUiThread(() -> {
                btnSolveTarget.setEnabled(true);
                btnSolveTarget.setText("开始反推最优发射配置");

                if (result.simResult == null) {
                    Toast.makeText(MainActivity.this, "未能找到有效解，请尝试调大容积或提高气压限制", Toast.LENGTH_SHORT).show();
                    return;
                }

                trajectoryView.setTargetDistance(targetDist);
                boxSolveResult.setVisibility(View.VISIBLE);

                double waterPercent = (result.recommendedWaterMl / (config.bottleVolumeL * 1000.0)) * 100.0;
                String yawCompensationStr;
                if (Math.abs(result.recommendedYawDeg) < 0.5) {
                    yawCompensationStr = "0° (无需侧风修正)";
                } else if (result.recommendedYawDeg > 0) {
                    yawCompensationStr = String.format(Locale.US, "+%.1f° (向右前置配平)", result.recommendedYawDeg);
                } else {
                    yawCompensationStr = String.format(Locale.US, "%.1f° (向左前置配平)", result.recommendedYawDeg);
                }

                String summary = String.format(Locale.US,
                        "【3D 最优发射配置方案 (目标 %.1fm)】\n" +
                        "▶ 规格: %s (Cd=%.2f)\n" +
                        "▶ 充气气压: %.1f bar (约 %.0f psi)\n" +
                        "▶ 推荐装水: %.0f mL (容积比 %.1f%%)\n" +
                        "▶ 发射仰角: %.0f° · 航向: %03.0f°\n" +
                        "▶ 侧风瞄准修正角: %s\n" +
                        "▶ 预估射程: %.1f m (侧漂残余: %.2f m)\n" +
                        "▶ 预计顶点高度: %.1f m · 滞空: %.1f s",
                        targetDist,
                        config.specName, config.dragCoefficient,
                        result.recommendedPressureBar, result.recommendedPressureBar * 14.5038,
                        result.recommendedWaterMl, waterPercent,
                        result.recommendedAngleDeg, config.launchHeadingDeg,
                        yawCompensationStr,
                        result.predictedRangeM, result.predictedDriftM,
                        result.predictedApogeeM, result.predictedFlightTimeS);

                tvSolveSummary.setText(summary);
            });
        }).start();
    }

    private void handleApplySolve() {
        if (lastSolveResult == null) return;

        isUpdatingUi = true;

        config.pressureBar = lastSolveResult.recommendedPressureBar;
        config.waterVolumeMl = lastSolveResult.recommendedWaterMl;
        config.launchAngleDeg = lastSolveResult.recommendedAngleDeg;
        config.launchYawDeg = lastSolveResult.recommendedYawDeg;

        sbPressure.setProgress((int) Math.round((config.pressureBar - 1.0) * 10.0));
        sbWater.setProgress((int) Math.round(config.waterVolumeMl));
        sbAngle.setProgress((int) Math.round(config.launchAngleDeg - 30.0));
        sbYaw.setProgress((int) Math.round(config.launchYawDeg + 25.0));

        isUpdatingUi = false;

        updateSimulation();
        Toast.makeText(this, "已应用最优 3D 发射与侧风补偿参数！", Toast.LENGTH_SHORT).show();
    }
}
