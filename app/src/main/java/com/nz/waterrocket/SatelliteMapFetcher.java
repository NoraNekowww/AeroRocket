package com.nz.waterrocket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SatelliteMapFetcher {

    private static final String TAG = "SatelliteMapFetcher";
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class SatelliteMapResult {
        public Bitmap mapBitmap;
        public double centerLat;
        public double centerLon;
        public float centerPixelX;
        public float centerPixelY;
        public float pixelsPerMeter;
        public int zoomLevel;
    }

    public interface SatelliteCallback {
        void onSuccess(SatelliteMapResult result);
        void onError(String errorMsg);
    }

    /**
     * Fetch real-time satellite imagery for given latitude and longitude from open ESRI World Imagery.
     * Downloads 2x2 adjacent tiles at Zoom 17 (~1.19 m/pixel) and stitches them into a 512x512 ground map.
     */
    public static void fetchSatelliteMap(Context context, double lat, double lon, SatelliteCallback callback) {
        executor.execute(() -> {
            try {
                int zoom = 17;
                double n = Math.pow(2.0, zoom);

                // Exact tile coordinate (floating point)
                double xExact = ((lon + 180.0) / 360.0) * n;
                double latRad = Math.toRadians(lat);
                double yExact = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

                int baseTileX = (int) Math.floor(xExact);
                int baseTileY = (int) Math.floor(yExact);

                // Fractional position inside base tile [0..1]
                double fracX = xExact - baseTileX;
                double fracY = yExact - baseTileY;

                // Stitch 2x2 tiles around the center
                int startTileX = (fracX > 0.5) ? baseTileX : (baseTileX - 1);
                int startTileY = (fracY > 0.5) ? baseTileY : (baseTileY - 1);

                Bitmap stitched = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(stitched);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

                boolean atLeastOneSuccess = false;
                File cacheDir = new File(context.getCacheDir(), "satellite_tiles");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                for (int dx = 0; dx < 2; dx++) {
                    for (int dy = 0; dy < 2; dy++) {
                        int tx = startTileX + dx;
                        int ty = startTileY + dy;
                        Bitmap tileBmp = downloadTile(cacheDir, zoom, tx, ty);
                        if (tileBmp != null) {
                            atLeastOneSuccess = true;
                            canvas.drawBitmap(tileBmp, dx * 256f, dy * 256f, p);
                        }
                    }
                }

                if (!atLeastOneSuccess) {
                    mainHandler.post(() -> callback.onError("无法从开放地图网站下载卫星瓦片"));
                    return;
                }

                SatelliteMapResult res = new SatelliteMapResult();
                res.mapBitmap = stitched;
                res.centerLat = lat;
                res.centerLon = lon;
                res.zoomLevel = zoom;

                // Center pixel in the 512x512 stitched bitmap
                res.centerPixelX = (float) ((xExact - startTileX) * 256.0);
                res.centerPixelY = (float) ((yExact - startTileY) * 256.0);

                // Meters per pixel at this latitude for Web Mercator:
                // S = 156543.03392 * cos(lat) / 2^zoom
                double metersPerPixel = (156543.03392 * Math.cos(latRad)) / n;
                res.pixelsPerMeter = (float) (1.0 / metersPerPixel);

                mainHandler.post(() -> callback.onSuccess(res));
            } catch (Exception e) {
                Log.w(TAG, "fetchSatelliteMap error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("卫星地图获取异常: " + e.getMessage()));
            }
        });
    }

    private static Bitmap downloadTile(File cacheDir, int z, int x, int y) {
        File cacheFile = new File(cacheDir, String.format(java.util.Locale.US, "tile_%d_%d_%d.jpg", z, x, y));
        if (cacheFile.exists() && cacheFile.length() > 1000) {
            Bitmap cached = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (cached != null) return cached;
        }

        // Open ESRI World Imagery Tile Service
        String urlStr = String.format(java.util.Locale.US,
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d",
                z, y, x);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "AeroRocket-WaterRocket-Sim/1.2.1");

            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(cacheFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();

                return BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.w(TAG, "Tile download failed [" + z + "/" + x + "/" + y + "]: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
