package com.nz.waterrocket;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherService {

    private static final String TAG = "WaterRocketWeather";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class WeatherData {
        public double latitude;
        public double longitude;
        public double windSpeedMs;      // m/s
        public double windDirectionDeg; // 0..360 (meteorological: 0 = from North, 90 = from East)
        public String updateTime;
        public String sourceModel;
        public String locationName;
    }

    public interface WeatherCallback {
        void onSuccess(WeatherData data);
        void onError(String errorMsg);
    }

    /**
     * Fetch ECMWF wind data by current device location (GPS/Network).
     * If location or network fails, reports explicit failure to caller.
     */
    @SuppressLint("MissingPermission")
    public static void fetchEcmwfByDeviceLocation(Context context, WeatherCallback callback) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            callback.onError("系统定位服务不可用");
            return;
        }

        Location bestLocation = null;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (bestLocation == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (bestLocation == null && lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                bestLocation = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (SecurityException e) {
            callback.onError("未获得定位权限，无法获取当前位置");
            return;
        }

        if (bestLocation != null) {
            // Check if location is reasonably fresh (within 30 mins)
            double lat = bestLocation.getLatitude();
            double lon = bestLocation.getLongitude();
            if (LocationManager.NETWORK_PROVIDER.equals(bestLocation.getProvider())) {
                CoordinateTransform.LatLon wgs = CoordinateTransform.gcj02ToWgs84(lat, lon);
                lat = wgs.lat;
                lon = wgs.lon;
            }
            fetchEcmwfWind(lat, lon, "当前设备定位", callback);
            return;
        }

        // If last known location is null, attempt single active update with a 4-second timeout
        try {
            final boolean[] received = {false};
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    if (!received[0]) {
                        received[0] = true;
                        try {
                            lm.removeUpdates(this);
                        } catch (SecurityException ignored) {}
                        double lat = loc.getLatitude();
                        double lon = loc.getLongitude();
                        if (LocationManager.NETWORK_PROVIDER.equals(loc.getProvider())) {
                            CoordinateTransform.LatLon wgs = CoordinateTransform.gcj02ToWgs84(lat, lon);
                            lat = wgs.lat;
                            lon = wgs.lon;
                        }
                        fetchEcmwfWind(lat, lon, "当前实时定位", callback);
                    }
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper());
            } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
            } else {
                callback.onError("定位服务未开启，无法获取当前坐标");
                return;
            }

            // Watchdog timeout: 4.5 seconds
            mainHandler.postDelayed(() -> {
                if (!received[0]) {
                    received[0] = true;
                    try {
                        lm.removeUpdates(listener);
                    } catch (SecurityException ignored) {}
                    callback.onError("获取当前定位超时，请确认开启GPS");
                }
            }, 4500);

        } catch (SecurityException e) {
            callback.onError("定位权限受限: " + e.getMessage());
        } catch (Exception e) {
            callback.onError("定位请求异常: " + e.getMessage());
        }
    }

    /**
     * Fetch real-time ECMWF IFS 0.25 deg surface wind data via Open-Meteo for given coordinates.
     */
    public static void fetchEcmwfWind(double lat, double lon, String locName, WeatherCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=wind_speed_10m,wind_direction_10m&wind_speed_unit=ms&models=ecmwf_ifs025",
                        lat, lon);

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6500);
                conn.setReadTimeout(6500);
                conn.setRequestProperty("User-Agent", "WaterRocket-Simulator/1.1.2");

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    if (root.has("current")) {
                        JSONObject current = root.getJSONObject("current");
                        WeatherData data = new WeatherData();
                        data.latitude = lat;
                        data.longitude = lon;
                        data.windSpeedMs = current.optDouble("wind_speed_10m", 0.0);
                        data.windDirectionDeg = current.optDouble("wind_direction_10m", 0.0);
                        data.updateTime = current.optString("time", "now");
                        data.sourceModel = "ECMWF IFS";
                        data.locationName = locName;

                        mainHandler.post(() -> callback.onSuccess(data));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onError("ECMWF 接口异常 (" + code + ")"));
            } catch (Exception e) {
                Log.w(TAG, "ECMWF fetch failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络请求超时或无连接: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
}
