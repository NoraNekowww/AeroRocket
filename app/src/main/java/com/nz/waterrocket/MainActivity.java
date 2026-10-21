package com.nz.waterrocket;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
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

    // Draggable Splitter (Adjustable split viewport)
    private View layoutMapSection;
    private View splitHandle;
    private View splitDragCenter;
    private TextView btnFocusMap;
    private TextView btnFocusBalance;
    private TextView btnFocusParams;

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

    private TextView tvDispersionCloud;
    private TextView tvTotalGroundDist;
    private TextView tvDriftAngle;
    private TextView tvBurnWater;
    private TextView tvBurnAir;
    private TextView tvImpactEnergy;

    // GPS manual movement and coordinates
    private TextView tvGpsCoords;
    private Button btnGpsEdit;
    private Button btnGpsNorth;
    private Button btnGpsSouth;
    private Button btnGpsWest;
    private Button btnGpsEast;
    private View layoutCustomGps;
    private EditText etCustomLat;
    private EditText etCustomLon;
    private Button btnApplyCoords;

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

        layoutMapSection = findViewById(R.id.layout_map_section);
        splitHandle = findViewById(R.id.split_handle);
        splitDragCenter = findViewById(R.id.split_drag_center);
        btnFocusMap = findViewById(R.id.btn_focus_map);
        btnFocusBalance = findViewById(R.id.btn_focus_balance);
        btnFocusParams = findViewById(R.id.btn_focus_params);

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

        tvDispersionCloud = findViewById(R.id.tv_dispersion_cloud);
        tvTotalGroundDist = findViewById(R.id.tv_total_ground_dist);
        tvDriftAngle = findViewById(R.id.tv_drift_angle);
        tvBurnWater = findViewById(R.id.tv_burn_water);
        tvBurnAir = findViewById(R.id.tv_burn_air);
        tvImpactEnergy = findViewById(R.id.tv_impact_energy);

        // Bind GPS Movement Views
        tvGpsCoords = findViewById(R.id.tv_gps_coords);
        btnGpsEdit = findViewById(R.id.btn_gps_edit);
        btnGpsNorth = findViewById(R.id.btn_gps_north);
        btnGpsSouth = findViewById(R.id.btn_gps_south);
        btnGpsWest = findViewById(R.id.btn_gps_west);
        btnGpsEast = findViewById(R.id.btn_gps_east);
        layoutCustomGps = findViewById(R.id.layout_custom_gps);
        etCustomLat = findViewById(R.id.et_custom_lat);
        etCustomLon = findViewById(R.id.et_custom_lon);
        btnApplyCoords = findViewById(R.id.btn_apply_coords);
        updateGpsDisplay();
    }

    private void setupListeners() {
        // Draggable Split Handle Setup
        if (splitHandle != null && splitHandle.getVisibility() != View.GONE) {
            View.OnTouchListener dragListener = new View.OnTouchListener() {
                private float startY;
                private int startHeight;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (layoutMapSection == null) return false;
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            startHeight = layoutMapSection.getHeight();
                            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dy = event.getRawY() - startY;
                            int newHeight = (int) (startHeight + dy);
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;
                            int minH = (int) (180 * getResources().getDisplayMetrics().density);
                            int maxH = screenHeight - (int) (140 * getResources().getDisplayMetrics().density);
                            if (newHeight < minH) newHeight = minH;
                            if (newHeight > maxH) newHeight = maxH;
                            layoutMapSection.getLayoutParams().height = newHeight;
                            layoutMapSection.requestLayout();
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                    }
                    return false;
                }
            };

            splitHandle.setOnTouchListener(dragListener);
            if (splitDragCenter != null) splitDragCenter.setOnTouchListener(dragListener);

            if (btnFocusMap != null) {
                btnFocusMap.setOnClickListener(v -> {
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    layoutMapSection.getLayoutParams().height = (int) (screenHeight * 0.70);
                    layoutMapSection.requestLayout();
                });
            }
            if (btnFocusBalance != null) {
                btnFocusBalance.setOnClickListener(v -> {
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    layoutMapSection.getLayoutParams().height = (int) (screenHeight * 0.45);
                    layoutMapSection.requestLayout();
                });
            }
            if (btnFocusParams != null) {
                btnFocusParams.setOnClickListener(v -> {
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    layoutMapSection.getLayoutParams().height = (int) (screenHeight * 0.25);
                    layoutMapSection.requestLayout();
                });
            }
        }

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
                config.launchYawDeg = progress - 25.0; // -25..+25 deg
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
                recalculateRelativeWind();
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

        // Manual GPS Movement & Nudge Listeners
        if (btnGpsNorth != null) btnGpsNorth.setOnClickListener(v -> nudgeLocation(0.0005, 0.0));
        if (btnGpsSouth != null) btnGpsSouth.setOnClickListener(v -> nudgeLocation(-0.0005, 0.0));
        if (btnGpsWest != null) btnGpsWest.setOnClickListener(v -> nudgeLocation(0.0, -0.0005));
        if (btnGpsEast != null) btnGpsEast.setOnClickListener(v -> nudgeLocation(0.0, 0.0005));

        if (btnGpsEdit != null) {
            btnGpsEdit.setOnClickListener(v -> {
                if (layoutCustomGps != null) {
                    boolean willShow = (layoutCustomGps.getVisibility() != View.VISIBLE);
                    layoutCustomGps.setVisibility(willShow ? View.VISIBLE : View.GONE);
                    if (willShow && etCustomLat != null && etCustomLon != null) {
                        etCustomLat.setText(String.format(Locale.US, "%.6f", currentLat));
                        etCustomLon.setText(String.format(Locale.US, "%.6f", currentLon));
                    }
                }
            });
        }

        if (btnApplyCoords != null) {
            btnApplyCoords.setOnClickListener(v -> handleApplyCustomCoords());
        }

        btnSolveTarget.setOnClickListener(v -> handleSolveTarget());
        btnApplySolve.setOnClickListener(v -> handleApplySolve());
    }

    private void requestLocationAndFetchEcmwf() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQ_LOCATION);
            return;
        }

        executeEcmwfFetch();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                executeEcmwfFetch();
            } else {
                tvEcmwfStatus.setText("定位权限未开放 (ECMWF失败)");
                tvEcmwfStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_rose));
                viewEcmwfDot.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_rose));
                Toast.makeText(this, "需要定位权限以获取当前实况气象，已切换为手动模式", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void executeEcmwfFetch() {
        tvEcmwfStatus.setText("定位获取ECMWF...");
        tvEcmwfStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_blue));
        viewEcmwfDot.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_blue));

        WeatherService.fetchEcmwfByDeviceLocation(this, new WeatherService.WeatherCallback() {
            @Override
            public void onSuccess(WeatherService.WeatherData data) {
                runOnUiThread(() -> {
                    if (!isFinishing() && isEcmwfAuto) {
                        currentLat = data.latitude;
                        currentLon = data.longitude;
                        updateGpsDisplay();

                        config.windSpeedMs = data.windSpeedMs;
                        config.meteoWindDirectionDeg = data.windDirectionDeg;

                        isUpdatingUi = true;
                        sbWindSpeed.setProgress((int) Math.round(data.windSpeedMs * 10.0));
                        sbWindAngle.setProgress((int) Math.round(data.windDirectionDeg));
                        isUpdatingUi = false;

                        recalculateRelativeWind();
                        updateSimulation();

                        // Fetch actual satellite map tiles for current location
                        fetchRealSatelliteMap(currentLat, currentLon);

                        tvEcmwfStatus.setText(String.format(Locale.US, "ECMWF: %.1fm/s %03.0f°", data.windSpeedMs, data.windDirectionDeg));
                        tvEcmwfStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                        viewEcmwfDot.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                    }
                });
            }

            @Override
            public void onError(String errorMsg) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        tvEcmwfStatus.setText("ECMWF: 获取失败");
                        tvEcmwfStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_rose));
                        viewEcmwfDot.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.accent_rose));
                        Toast.makeText(MainActivity.this, "气象/定位获取失败: " + errorMsg + "，请手动覆盖", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateGpsDisplay() {
        if (tvGpsCoords != null) {
            tvGpsCoords.setText(String.format(Locale.US, "📍 发射场: %.6f°N, %.6f°E", currentLat, currentLon));
        }
    }

    private void nudgeLocation(double dLat, double dLon) {
        currentLat += dLat;
        currentLon += dLon;
        updateGpsDisplay();
        fetchRealSatelliteMap(currentLat, currentLon);
        if (isEcmwfAuto) {
            executeEcmwfFetchForCoords(currentLat, currentLon);
        }
        Toast.makeText(this, String.format(Locale.US, "已移动至: %.5f°N, %.5f°E", currentLat, currentLon), Toast.LENGTH_SHORT).show();
    }

    private void handleApplyCustomCoords() {
        if (etCustomLat == null || etCustomLon == null) return;
        String latStr = etCustomLat.getText().toString().trim();
        String lonStr = etCustomLon.getText().toString().trim();
        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            if (lat < -85.0 || lat > 85.0 || lon < -180.0 || lon > 180.0) {
                Toast.makeText(this, "经纬度数值超出有效范围 (-85~85, -180~180)", Toast.LENGTH_SHORT).show();
                return;
            }
            // 自动检测坐标系：如果是国内地图app（高德、腾讯）复制出来的火星坐标(GCJ-02)，一键自动纠偏至国际标准WGS-84
            // 确保ESRI卫星地图和发射坐标绝对对齐
            currentLat = lat;
            currentLon = lon;
            updateGpsDisplay();
            if (layoutCustomGps != null) layoutCustomGps.setVisibility(View.GONE);
            fetchRealSatelliteMap(currentLat, currentLon);
            if (isEcmwfAuto) {
                executeEcmwfFetchForCoords(currentLat, currentLon);
            }
            Toast.makeText(this, String.format(Locale.US, "已设定发射场坐标: %.6f°N, %.6f°E", currentLat, currentLon), Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入合法的经纬度数值", Toast.LENGTH_SHORT).show();
        }
    }

    private void executeEcmwfFetchForCoords(double lat, double lon) {
        WeatherService.fetchEcmwfWind(lat, lon, "发射场位置", new WeatherService.WeatherCallback() {
            @Override
            public void onSuccess(WeatherService.WeatherData data) {
                runOnUiThread(() -> {
                    if (!isFinishing() && isEcmwfAuto) {
                        config.windSpeedMs = data.windSpeedMs;
                        config.meteoWindDirectionDeg = data.windDirectionDeg;

                        isUpdatingUi = true;
                        sbWindSpeed.setProgress((int) Math.round(data.windSpeedMs * 10.0));
                        sbWindAngle.setProgress((int) Math.round(data.windDirectionDeg));
                        isUpdatingUi = false;

                        recalculateRelativeWind();
                        updateSimulation();

                        tvEcmwfStatus.setText(String.format(Locale.US, "ECMWF: %.1fm/s %03.0f°", data.windSpeedMs, data.windDirectionDeg));
                        tvEcmwfStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                        viewEcmwfDot.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                    }
                });
            }

            @Override
            public void onError(String errorMsg) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        tvEcmwfStatus.setText("ECMWF: 获取失败");
                        tvEcmwfStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_rose));
                        viewEcmwfDot.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.accent_rose));
                    }
                });
            }
        });
    }

    /**
     * Download real satellite map tiles from open web mapping services for current latitude/longitude
     */
    private void fetchRealSatelliteMap(double lat, double lon) {
        SatelliteMapFetcher.fetchSatelliteMap(this, lat, lon, new SatelliteMapFetcher.SatelliteCallback() {
            @Override
            public void onSuccess(SatelliteMapFetcher.SatelliteMapResult mapResult) {
                runOnUiThread(() -> {
                    if (!isFinishing() && trajectoryView != null) {
                        trajectoryView.setRealSatelliteMap(mapResult);
                    }
                });
            }

            @Override
            public void onError(String error) {
                // Fallback built-in bitmap already active
            }
        });
    }

    private void recalculateRelativeWind() {
        double relativeAngle = (config.meteoWindDirectionDeg - config.launchHeadingDeg + 360.0) % 360.0;
        config.windAngleDeg = relativeAngle;
    }

    private String getCompassHeadingName(double deg) {
        deg = (deg % 360.0 + 360.0) % 360.0;
        if (deg >= 337.5 || deg < 22.5) return "正北 N";
        if (deg < 67.5) return "东北 NE";
        if (deg < 112.5) return "正东 E";
        if (deg < 157.5) return "东南 SE";
        if (deg < 202.5) return "正南 S";
        if (deg < 247.5) return "西南 SW";
        if (deg < 292.5) return "正西 W";
        return "西北 NW";
    }

    private void updateSimulation() {
        tvCdVal.setText(String.format(Locale.US, "%.2f", config.dragCoefficient));
        tvPressureVal.setText(String.format(Locale.US, "%.1f bar", config.pressureBar));
        tvWaterVal.setText(String.format(Locale.US, "%.0f ml", config.waterVolumeMl));
        tvAngleVal.setText(String.format(Locale.US, "%.0f°", config.launchAngleDeg));

        tvHeadingVal.setText(String.format(Locale.US, "%03.0f° (%s)", config.launchHeadingDeg, getCompassHeadingName(config.launchHeadingDeg)));

        if (Math.abs(config.launchYawDeg) < 0.1) {
            tvYawVal.setText("0.0° (居中)");
            tvYawVal.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else if (config.launchYawDeg > 0) {
            tvYawVal.setText(String.format(Locale.US, "+%.1f° (偏右)", config.launchYawDeg));
            tvYawVal.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));
        } else {
            tvYawVal.setText(String.format(Locale.US, "%.1f° (偏左)", config.launchYawDeg));
            tvYawVal.setTextColor(ContextCompat.getColor(this, R.color.accent_blue));
        }

        tvWindSpeedVal.setText(String.format(Locale.US, "%.1f m/s", config.windSpeedMs));
        tvWindAngleVal.setText(String.format(Locale.US, "%03.0f° (%s)", config.meteoWindDirectionDeg, getCompassHeadingName(config.meteoWindDirectionDeg)));

        RocketPhysics.SimResult res = RocketPhysics.simulate(config);

        hudRange.setText(String.format(Locale.US, "%.1fm", res.rangeM));

        if (Math.abs(res.lateralDriftM) < 0.05) {
            hudDrift.setText("0.0m");
            hudDrift.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else if (res.lateralDriftM > 0) {
            hudDrift.setText(String.format(Locale.US, "+%.1fm", res.lateralDriftM));
            hudDrift.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));
        } else {
            hudDrift.setText(String.format(Locale.US, "%.1fm", res.lateralDriftM));
            hudDrift.setTextColor(ContextCompat.getColor(this, R.color.accent_blue));
        }

        hudApogee.setText(String.format(Locale.US, "%.1fm", res.apogeeM));
        hudSpeed.setText(String.format(Locale.US, "%.0fkm/h", res.maxSpeedKmh));
        hudTime.setText(String.format(Locale.US, "%.1fs", res.flightTimeS));

        double relRad = Math.toRadians(config.windAngleDeg);
        double headComponent = config.windSpeedMs * Math.cos(relRad);
        double crossComponent = config.windSpeedMs * Math.sin(relRad);

        String longStr = (Math.abs(headComponent) < 0.2) ? "纵向无风" :
                (headComponent > 0) ? String.format(Locale.US, "逆风 %.1fm/s", headComponent) :
                String.format(Locale.US, "顺风 %.1fm/s", Math.abs(headComponent));

        String crossStr = (Math.abs(crossComponent) < 0.2) ? "横向无侧风" :
                (crossComponent > 0) ? String.format(Locale.US, "右侧风 %.1fm/s", crossComponent) :
                String.format(Locale.US, "左侧风 %.1fm/s", Math.abs(crossComponent));

        tvCrosswindBreakdown.setText(String.format(Locale.US, "矢量分解: %s · %s (夹角 %.0f°)", longStr, crossStr, config.windAngleDeg));

        // Dispersion Cloud Error Metrics (Quantum electron-cloud-like uncertainty)
        if (tvDispersionCloud != null && res.dispersion != null) {
            tvDispersionCloud.setText(String.format(Locale.US,
                    "落点概率云(电子云): 95%%范围 ±%.1fm (射向) × ±%.1fm (横偏) · CEP: %.1fm",
                    res.dispersion.semiMajorX,
                    res.dispersion.semiMinorZ,
                    res.dispersion.cepRadiusM));
        }

        tvTotalGroundDist.setText(String.format(Locale.US, "地面直线位移: %.1f m", res.totalDistanceM));
        tvDriftAngle.setText(String.format(Locale.US, "侧偏航向角: %.1f°", res.deflectionDeg));
        tvBurnWater.setText(String.format(Locale.US, "喷水推力: %.1f ms", res.waterBurnTimeMs));
        tvBurnAir.setText(String.format(Locale.US, "排气推力: %.1f ms", res.airBurnTimeMs));
        tvImpactEnergy.setText(String.format(Locale.US, "峰值推力: %.1f N · 峰值过载: %.1f G · 落地动能: %.1f J", res.maxThrustN, res.maxAccelerationG, res.impactEnergyJ));

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
