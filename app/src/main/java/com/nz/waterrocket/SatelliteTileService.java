package com.nz.waterrocket;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SatelliteTileService {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Bitmap cachedSatelliteBitmap = null;

    public interface TileCallback {
        void onLoaded(Bitmap bitmap);
    }

    /**
     * Get launch field satellite imagery bitmap (cached or generate realistic aerial satellite scene).
     */
    public static Bitmap getLaunchFieldSatelliteBitmap(int width, int height) {
        if (cachedSatelliteBitmap != null && cachedSatelliteBitmap.getWidth() == width && cachedSatelliteBitmap.getHeight() == height) {
            return cachedSatelliteBitmap;
        }

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // 1. Base terrain: Deep satellite natural turf & soil
        Paint pBase = new Paint(Paint.ANTI_ALIAS_FLAG);
        pBase.setShader(new LinearGradient(0, 0, width, height,
                Color.parseColor("#0F2218"), Color.parseColor("#183022"), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, pBase);

        // 2. Realistic athletic track / testing runway (oval runway)
        Paint pTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        pTrack.setColor(Color.parseColor("#3B201A")); // Reddish-brown synthetic track
        pTrack.setStyle(Paint.Style.STROKE);
        pTrack.setStrokeWidth(width * 0.12f);
        RectF trackRect = new RectF(width * 0.10f, height * 0.15f, width * 0.90f, height * 0.85f);
        c.drawRoundRect(trackRect, width * 0.25f, width * 0.25f, pTrack);

        // Track white boundary lines
        Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        pLine.setColor(Color.parseColor("#5A6A5E"));
        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeWidth(2.5f);
        c.drawRoundRect(trackRect, width * 0.25f, width * 0.25f, pLine);

        // Inner football grass field
        Paint pInnerGrass = new Paint(Paint.ANTI_ALIAS_FLAG);
        pInnerGrass.setColor(Color.parseColor("#173B25"));
        pInnerGrass.setStyle(Paint.Style.FILL);
        RectF innerRect = new RectF(width * 0.16f, height * 0.21f, width * 0.84f, height * 0.79f);
        c.drawRoundRect(innerRect, width * 0.20f, width * 0.20f, pInnerGrass);

        // Field mowing stripes (lawn mowing texture)
        Paint pMowing = new Paint(Paint.ANTI_ALIAS_FLAG);
        pMowing.setColor(Color.parseColor("#1D452D"));
        pMowing.setStyle(Paint.Style.FILL);
        float stripeW = width * 0.05f;
        for (float x = innerRect.left; x < innerRect.right; x += stripeW * 2f) {
            c.save();
            c.clipRect(innerRect);
            c.drawRect(x, innerRect.top, x + stripeW, innerRect.bottom, pMowing);
            c.restore();
        }

        // Center circle & penalty areas
        Paint pMarkings = new Paint(Paint.ANTI_ALIAS_FLAG);
        pMarkings.setColor(Color.parseColor("#648A70"));
        pMarkings.setStyle(Paint.Style.STROKE);
        pMarkings.setStrokeWidth(2f);
        c.drawCircle(width * 0.50f, height * 0.50f, width * 0.09f, pMarkings);
        c.drawLine(width * 0.50f, innerRect.top, width * 0.50f, innerRect.bottom, pMarkings);

        // 3. Perimeter trees & boundary roads
        Paint pRoad = new Paint(Paint.ANTI_ALIAS_FLAG);
        pRoad.setColor(Color.parseColor("#1B262C"));
        pRoad.setStyle(Paint.Style.STROKE);
        pRoad.setStrokeWidth(width * 0.04f);
        c.drawRect(0, 0, width, height, pRoad);

        // 4. Subtle vignetting for 3D depth
        Paint pVignette = new Paint(Paint.ANTI_ALIAS_FLAG);
        pVignette.setShader(new RadialGradient(width * 0.5f, height * 0.5f, width * 0.65f,
                Color.TRANSPARENT, Color.parseColor("#B008120D"), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, pVignette);

        cachedSatelliteBitmap = bmp;
        return bmp;
    }

    /**
     * Optionally fetch online high-res satellite tile for specific lat/lon.
     */
    public static void fetchOnlineSatelliteTile(double lat, double lon, TileCallback callback) {
        executor.execute(() -> {
            try {
                // Convert lat/lon to Web Mercator tile XY at zoom level 16
                int zoom = 16;
                int x = (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
                double latRad = Math.toRadians(lat);
                int y = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom));

                String urlStr = String.format(java.util.Locale.US,
                        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d",
                        zoom, y, x);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "WaterRocket-Simulator/1.2.0");

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    Bitmap onlineBmp = android.graphics.BitmapFactory.decodeStream(in);
                    in.close();
                    if (onlineBmp != null) {
                        cachedSatelliteBitmap = onlineBmp;
                        mainHandler.post(() -> callback.onLoaded(onlineBmp));
                        return;
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}
