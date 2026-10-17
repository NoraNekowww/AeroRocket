package com.nz.waterrocket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

public class TrajectoryView extends View {

    public static final int MODE_2D = 0;
    public static final int MODE_3D = 1;

    private int currentMode = MODE_3D;
    private boolean showSatelliteMap = true; // Default satellite imagery overlay enabled

    private RocketPhysics.SimResult currentResult;
    private double targetDistanceM = -1.0;
    private double currentLaunchAngleDeg = 45.0;
    private double currentLaunchHeadingDeg = 90.0; // True North heading
    private double currentLaunchYawDeg = 0.0;
    private double currentWindSpeedMs = 0.0;
    private double currentWindAngleDeg = 0.0;

    // 3D Camera Angles
    private float camAzimuthDeg = 40.0f;   // Horizontal rotation around center
    private float camElevationDeg = 28.0f; // Tilt angle from above (elevation)
    private float camScale = 1.0f;         // Zoom factor

    // Touch interaction
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isDragging = false;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector doubleTapDetector;

    // Quick camera & satellite buttons bounds (Top overlay)
    private final RectF btnModeBounds = new RectF();
    private final RectF btnSatBounds = new RectF();
    private final RectF btnTopViewBounds = new RectF();
    private final RectF btnSideViewBounds = new RectF();
    private final RectF btnResetBounds = new RectF();

    // Satellite synthetic procedural imagery cache
    private Bitmap satelliteBitmap;

    // Paints
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waterPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint airPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coastPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint satMeshPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Paths
    private final Path waterPath = new Path();
    private final Path airPath = new Path();
    private final Path coastPath = new Path();
    private final Path shadowPath = new Path();

    private final RectF plotArea2D = new RectF();

    public TrajectoryView(Context context) {
        super(context);
        init(context);
    }

    public TrajectoryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TrajectoryView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;

        gridPaint.setColor(Color.parseColor("#1E293B"));
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);

        axisPaint.setColor(Color.parseColor("#475569"));
        axisPaint.setStrokeWidth(1.5f * density);
        axisPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextSize(10f * density);

        waterPathPaint.setColor(Color.parseColor("#38BDF8")); // Cyan
        waterPathPaint.setStrokeWidth(3.0f * density);
        waterPathPaint.setStyle(Paint.Style.STROKE);
        waterPathPaint.setStrokeCap(Paint.Cap.ROUND);

        airPathPaint.setColor(Color.parseColor("#34D399")); // Emerald Green
        airPathPaint.setStrokeWidth(2.6f * density);
        airPathPaint.setStyle(Paint.Style.STROKE);
        airPathPaint.setStrokeCap(Paint.Cap.ROUND);

        coastPathPaint.setColor(Color.parseColor("#F8FAFC")); // White
        coastPathPaint.setStrokeWidth(2.2f * density);
        coastPathPaint.setStyle(Paint.Style.STROKE);
        coastPathPaint.setStrokeCap(Paint.Cap.ROUND);

        shadowPathPaint.setColor(Color.parseColor("#334155"));
        shadowPathPaint.setStrokeWidth(1.5f * density);
        shadowPathPaint.setStyle(Paint.Style.STROKE);
        shadowPathPaint.setPathEffect(new DashPathEffect(new float[]{4f * density, 3f * density}, 0));

        targetPaint.setColor(Color.parseColor("#FBBF24")); // Amber Gold
        targetPaint.setStrokeWidth(1.5f * density);
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setPathEffect(new DashPathEffect(new float[]{5f * density, 4f * density}, 0));

        dropLinePaint.setColor(Color.parseColor("#475569"));
        dropLinePaint.setStrokeWidth(1f * density);
        dropLinePaint.setStyle(Paint.Style.STROKE);
        dropLinePaint.setPathEffect(new DashPathEffect(new float[]{3f * density, 3f * density}, 0));

        railPaint.setColor(Color.parseColor("#FB7185"));
        railPaint.setStrokeWidth(2.5f * density);
        railPaint.setStyle(Paint.Style.STROKE);

        satMeshPaint.setFilterBitmap(true);
        satMeshPaint.setDither(true);

        createSatelliteGroundTexture();

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (currentMode == MODE_3D) {
                    camScale *= detector.getScaleFactor();
                    camScale = Math.max(0.4f, Math.min(3.5f, camScale));
                    invalidate();
                    return true;
                }
                return false;
            }
        });

        doubleTapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (currentMode == MODE_3D) {
                    reset3DCamera();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Generate high-resolution launch field satellite surface texture (sports ground / grass fairway / runway)
     */
    private void createSatelliteGroundTexture() {
        int size = 512;
        satelliteBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(satelliteBitmap);

        // Dark realistic satellite turf background
        Paint pGrass = new Paint();
        pGrass.setShader(new LinearGradient(0, 0, size, size,
                Color.parseColor("#12231A"), Color.parseColor("#183024"), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, pGrass);

        // Runway / Athletics track oval lines
        Paint pTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        pTrack.setColor(Color.parseColor("#1C382A"));
        pTrack.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(40, 100, size - 40, size - 100), 120, 120, pTrack);

        Paint pRunway = new Paint(Paint.ANTI_ALIAS_FLAG);
        pRunway.setColor(Color.parseColor("#264535"));
        pRunway.setStrokeWidth(16);
        pRunway.setStyle(Paint.Style.STROKE);
        c.drawLine(40, size / 2f, size - 40, size / 2f, pRunway);

        // Center line
        Paint pCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        pCenter.setColor(Color.parseColor("#3A614B"));
        pCenter.setStrokeWidth(2);
        pCenter.setStyle(Paint.Style.STROKE);
        c.drawCircle(size / 2f, size / 2f, 70, pCenter);
        c.drawLine(0, size / 2f, size, size / 2f, pCenter);
        c.drawLine(size / 2f, 0, size / 2f, size, pCenter);
    }

    public void reset3DCamera() {
        camAzimuthDeg = 40.0f;
        camElevationDeg = 28.0f;
        camScale = 1.0f;
        invalidate();
    }

    public void setViewMode(int mode) {
        this.currentMode = mode;
        invalidate();
    }

    public int getViewMode() {
        return currentMode;
    }

    public void toggleSatelliteMap() {
        showSatelliteMap = !showSatelliteMap;
        invalidate();
    }

    public void setSimResult(RocketPhysics.SimResult result, double launchAngle, double launchHeading, double launchYaw, double windSpeed, double windAngle) {
        this.currentResult = result;
        this.currentLaunchAngleDeg = launchAngle;
        this.currentLaunchHeadingDeg = launchHeading;
        this.currentLaunchYawDeg = launchYaw;
        this.currentWindSpeedMs = windSpeed;
        this.currentWindAngleDeg = windAngle;
        invalidate();
    }

    public void setTargetDistance(double targetM) {
        this.targetDistanceM = targetM;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Mode toggle
            if (btnModeBounds.contains(x, y)) {
                setViewMode(currentMode == MODE_3D ? MODE_2D : MODE_3D);
                return true;
            }
            // Satellite toggle
            if (btnSatBounds.contains(x, y)) {
                toggleSatelliteMap();
                return true;
            }
            if (currentMode == MODE_3D) {
                // Top view (俯视看侧漂)
                if (btnTopViewBounds.contains(x, y)) {
                    camElevationDeg = 88.0f;
                    camAzimuthDeg = 0.0f;
                    invalidate();
                    return true;
                }
                // Side view (侧视看高度)
                if (btnSideViewBounds.contains(x, y)) {
                    camElevationDeg = 8.0f;
                    camAzimuthDeg = 90.0f;
                    invalidate();
                    return true;
                }
                // Reset view
                if (btnResetBounds.contains(x, y)) {
                    reset3DCamera();
                    return true;
                }
            }
        }

        if (currentMode == MODE_3D) {
            // Disallow parent scrollview interception
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }

            boolean handled = doubleTapDetector.onTouchEvent(event);
            handled |= scaleDetector.onTouchEvent(event);

            if (scaleDetector.isInProgress()) {
                isDragging = false;
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = x;
                    lastTouchY = y;
                    isDragging = true;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging && event.getPointerCount() == 1) {
                        float dx = x - lastTouchX;
                        float dy = y - lastTouchY;

                        // Horizontal rotation
                        camAzimuthDeg = (camAzimuthDeg - dx * 0.42f + 360f) % 360f;

                        // FLIPPED VERTICAL SCROLL: Inverted as requested by user!
                        // Now dragging finger downwards increases elevation (looks down from above),
                        // dragging upwards decreases elevation (flattens perspective).
                        camElevationDeg = Math.max(5.0f, Math.min(88.0f, camElevationDeg + dy * 0.35f));

                        lastTouchX = x;
                        lastTouchY = y;
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    return true;
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        if (currentMode == MODE_3D) {
            draw3DScene(canvas, w, h, density);
        } else {
            draw2DScene(canvas, w, h, density);
        }

        // Draw Interactive Camera, Satellite & Mode Controls Header
        drawTopControls(canvas, density, w);
    }

    // =========================================================================
    // 3D SPATIAL PROJECTION RENDERING ENGINE
    // =========================================================================

    private static class Point2D {
        float px, py;
        Point2D(float px, float py) {
            this.px = px;
            this.py = py;
        }
    }

    private Point2D project3D(double x, double y, double z,
                             double pivotX, double pivotY, double pivotZ,
                             float cx, float cy, float scaleWorld,
                             double sinAz, double cosAz, double sinEl, double cosEl) {
        double relX = x - pivotX;
        double relY = y - pivotY;
        double relZ = z - pivotZ;

        double x1 = relX * cosAz - relZ * sinAz;
        double z1 = relX * sinAz + relZ * cosAz;
        double y1 = relY;

        double xProj = x1;
        double yProj = y1 * cosEl - z1 * sinEl;

        float px = (float) (cx + xProj * scaleWorld);
        float py = (float) (cy - yProj * scaleWorld);
        return new Point2D(px, py);
    }

    private void draw3DScene(Canvas canvas, int w, int h, float density) {
        double maxDist = 60.0;
        double maxAlt = 25.0;
        double maxDrift = 15.0;

        if (currentResult != null && !currentResult.trajectory.isEmpty()) {
            maxDist = Math.max(maxDist, currentResult.totalDistanceM * 1.15);
            maxAlt = Math.max(maxAlt, currentResult.apogeeM * 1.25);
            maxDrift = Math.max(maxDrift, Math.abs(currentResult.lateralDriftM) * 1.5 + 10.0);
        }
        if (targetDistanceM > 0) {
            maxDist = Math.max(maxDist, targetDistanceM * 1.15);
        }

        double boxSize = Math.max(maxDist, Math.max(maxAlt, maxDrift));

        double pivotX = (currentResult != null && currentResult.rangeM > 10.0) ? (currentResult.rangeM * 0.45) : (boxSize * 0.4);
        double pivotY = (currentResult != null && currentResult.apogeeM > 5.0) ? (currentResult.apogeeM * 0.35) : (boxSize * 0.2);
        double pivotZ = (currentResult != null) ? (currentResult.lateralDriftM * 0.5) : 0.0;

        float cx = w * 0.50f;
        float cy = h * 0.60f;
        float scaleWorld = (Math.min(w, h) * 0.70f / (float) boxSize) * camScale;

        double azRad = Math.toRadians(camAzimuthDeg);
        double elRad = Math.toRadians(camElevationDeg);
        double sinAz = Math.sin(azRad), cosAz = Math.cos(azRad);
        double sinEl = Math.sin(elRad), cosEl = Math.cos(elRad);

        double gridStep = calculateNiceStep(boxSize, 5);
        double gridLimitX = Math.ceil(boxSize / gridStep) * gridStep;
        double gridLimitZ = Math.ceil(boxSize * 0.5 / gridStep) * gridStep;

        // 1. Draw Satellite Imagery Ground Surface (Y = 0)
        if (showSatelliteMap && satelliteBitmap != null) {
            drawSatelliteGround(canvas, gridLimitX, gridLimitZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl, density);
        }

        // 2. Draw 3D Ground Plane Grid
        for (double z = -gridLimitZ; z <= gridLimitZ; z += gridStep) {
            Point2D pStart = project3D(0, 0, z, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            Point2D pEnd = project3D(gridLimitX, 0, z, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            canvas.drawLine(pStart.px, pStart.py, pEnd.px, pEnd.py, gridPaint);
        }

        for (double x = 0; x <= gridLimitX; x += gridStep) {
            Point2D pStart = project3D(x, 0, -gridLimitZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            Point2D pEnd = project3D(x, 0, gridLimitZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            canvas.drawLine(pStart.px, pStart.py, pEnd.px, pEnd.py, gridPaint);

            Point2D pLabel = project3D(x, 0, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            canvas.drawText(String.format(Locale.US, "%.0fm", x), pLabel.px + 2f * density, pLabel.py + 10f * density, textPaint);
        }

        // 3. Main 3D Axes
        Point2D origin = project3D(0, 0, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);

        Point2D xEnd = project3D(gridLimitX, 0, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Paint pAxisX = new Paint(axisPaint);
        pAxisX.setColor(Color.parseColor("#F43F5E"));
        pAxisX.setStrokeWidth(2f * density);
        canvas.drawLine(origin.px, origin.py, xEnd.px, xEnd.py, pAxisX);
        canvas.drawText(String.format(Locale.US, "X 射程 (%03.0f°)", currentLaunchHeadingDeg), xEnd.px + 4f * density, xEnd.py, pAxisX);

        Point2D yEnd = project3D(0, gridLimitX * 0.5, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Paint pAxisY = new Paint(axisPaint);
        pAxisY.setColor(Color.parseColor("#34D399"));
        pAxisY.setStrokeWidth(2f * density);
        canvas.drawLine(origin.px, origin.py, yEnd.px, yEnd.py, pAxisY);
        canvas.drawText("Y 高度", yEnd.px, yEnd.py - 4f * density, pAxisY);

        Point2D zEnd = project3D(0, 0, gridLimitZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Paint pAxisZ = new Paint(axisPaint);
        pAxisZ.setColor(Color.parseColor("#38BDF8"));
        pAxisZ.setStrokeWidth(2f * density);
        canvas.drawLine(origin.px, origin.py, zEnd.px, zEnd.py, pAxisZ);
        canvas.drawText("Z 侧偏", zEnd.px + 4f * density, zEnd.py + 4f * density, pAxisZ);

        // Launch Pad Origin
        Paint originDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        originDot.setColor(Color.parseColor("#38BDF8"));
        canvas.drawCircle(origin.px, origin.py, 4.5f * density, originDot);

        // 4. Target Marker on Ground
        if (targetDistanceM > 0) {
            Point2D tCenter = project3D(targetDistanceM, 0, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            canvas.drawCircle(tCenter.px, tCenter.py, 6f * density, targetPaint);
            canvas.drawCircle(tCenter.px, tCenter.py, 12f * density, targetPaint);

            Point2D tPinTop = project3D(targetDistanceM, 6.0, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            canvas.drawLine(tCenter.px, tCenter.py, tPinTop.px, tPinTop.py, targetPaint);

            Paint pTgtText = new Paint(Paint.ANTI_ALIAS_FLAG);
            pTgtText.setColor(Color.parseColor("#FBBF24"));
            pTgtText.setTextSize(9.5f * density);
            pTgtText.setFakeBoldText(true);
            canvas.drawText(String.format(Locale.US, "目标: %.0fm", targetDistanceM), tPinTop.px + 4f * density, tPinTop.py, pTgtText);
        }

        if (currentResult == null || currentResult.trajectory.isEmpty()) return;

        List<RocketPhysics.TrajectoryPoint> traj = currentResult.trajectory;

        // 5. Ground Shadow Trace (X, 0, Z)
        shadowPath.reset();
        for (int i = 0; i < traj.size(); i++) {
            RocketPhysics.TrajectoryPoint pt = traj.get(i);
            Point2D ps = project3D(pt.x, 0, pt.z, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            if (i == 0) shadowPath.moveTo(ps.px, ps.py);
            else shadowPath.lineTo(ps.px, ps.py);
        }
        canvas.drawPath(shadowPath, shadowPathPaint);

        // 6. 3D Flight Trajectory
        waterPath.reset();
        airPath.reset();
        coastPath.reset();

        Point2D apexPt2D = null;
        RocketPhysics.TrajectoryPoint apexPt3D = null;
        float maxAltFound = -1f;
        RocketPhysics.TrajectoryPoint prevPt = null;

        for (int i = 0; i < traj.size(); i++) {
            RocketPhysics.TrajectoryPoint pt = traj.get(i);
            Point2D p2d = project3D(pt.x, pt.y, pt.z, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);

            if (pt.y > maxAltFound) {
                maxAltFound = pt.y;
                apexPt2D = p2d;
                apexPt3D = pt;
            }

            if (i == 0) {
                waterPath.moveTo(p2d.px, p2d.py);
            } else {
                if (pt.phase == 0) {
                    waterPath.lineTo(p2d.px, p2d.py);
                } else if (pt.phase == 1) {
                    if (prevPt != null && prevPt.phase == 0) airPath.moveTo(p2d.px, p2d.py);
                    else airPath.lineTo(p2d.px, p2d.py);
                } else {
                    if (prevPt != null && prevPt.phase != 2) coastPath.moveTo(p2d.px, p2d.py);
                    else coastPath.lineTo(p2d.px, p2d.py);
                }
            }
            prevPt = pt;
        }

        canvas.drawPath(waterPath, waterPathPaint);
        canvas.drawPath(airPath, airPathPaint);
        canvas.drawPath(coastPath, coastPathPaint);

        // 7. Apex Highlight with ground drop line
        if (apexPt2D != null && apexPt3D != null) {
            Point2D apexGround = project3D(apexPt3D.x, 0, apexPt3D.z, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
            canvas.drawLine(apexPt2D.px, apexPt2D.py, apexGround.px, apexGround.py, dropLinePaint);

            Paint pDot = new Paint(Paint.ANTI_ALIAS_FLAG);
            pDot.setColor(Color.parseColor("#38BDF8"));
            canvas.drawCircle(apexPt2D.px, apexPt2D.py, 4.5f * density, pDot);

            Paint pTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
            pTxt.setColor(Color.parseColor("#F8FAFC"));
            pTxt.setTextSize(9.5f * density);
            pTxt.setFakeBoldText(true);
            canvas.drawText(String.format(Locale.US, "最高: %.1fm", currentResult.apogeeM),
                    apexPt2D.px + 5f * density, apexPt2D.py - 4f * density, pTxt);
        }

        // 8. Ground Impact & Lateral Drift
        RocketPhysics.TrajectoryPoint lastPt = traj.get(traj.size() - 1);
        Point2D impactPt = project3D(lastPt.x, 0, lastPt.z, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);

        Paint landDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        landDot.setColor(Color.parseColor("#F43F5E"));
        canvas.drawCircle(impactPt.px, impactPt.py, 5f * density, landDot);

        Point2D axisRef = project3D(lastPt.x, 0, 0, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Paint pDriftLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        pDriftLine.setColor(Color.parseColor("#FB7185"));
        pDriftLine.setStrokeWidth(1.2f * density);
        pDriftLine.setStyle(Paint.Style.STROKE);
        pDriftLine.setPathEffect(new DashPathEffect(new float[]{3f * density, 3f * density}, 0));
        canvas.drawLine(axisRef.px, axisRef.py, impactPt.px, impactPt.py, pDriftLine);

        Paint landText = new Paint(Paint.ANTI_ALIAS_FLAG);
        landText.setColor(Color.parseColor("#FDA4AF"));
        landText.setTextSize(9.5f * density);
        landText.setFakeBoldText(true);

        String driftDir = (currentResult.lateralDriftM >= 0) ? "偏右" : "偏左";
        String landLabel = String.format(Locale.US, "落点: %.1fm (%s %.1fm)",
                currentResult.rangeM, driftDir, Math.abs(currentResult.lateralDriftM));
        canvas.drawText(landLabel, impactPt.px + 5f * density, impactPt.py + 4f * density, landText);

        // 9. Bottom Hint
        Paint tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipPaint.setColor(Color.parseColor("#475569"));
        tipPaint.setTextSize(8.5f * density);
        String tip = "单指旋转 · 双指缩放 · 双击复位";
        canvas.drawText(tip, (w - tipPaint.measureText(tip)) / 2f, h - 6f * density, tipPaint);
    }

    private void drawSatelliteGround(Canvas canvas, double maxX, double maxZ,
                                    double pivotX, double pivotY, double pivotZ,
                                    float cx, float cy, float scaleWorld,
                                    double sinAz, double cosAz, double sinEl, double cosEl, float density) {
        // Project 4 ground boundary corners in 3D
        Point2D p00 = project3D(0, 0, -maxZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Point2D p10 = project3D(maxX, 0, -maxZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Point2D p11 = project3D(maxX, 0, maxZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);
        Point2D p01 = project3D(0, 0, maxZ, pivotX, pivotY, pivotZ, cx, cy, scaleWorld, sinAz, cosAz, sinEl, cosEl);

        Path quadPath = new Path();
        quadPath.moveTo(p00.px, p00.py);
        quadPath.lineTo(p10.px, p10.py);
        quadPath.lineTo(p11.px, p11.py);
        quadPath.lineTo(p01.px, p01.py);
        quadPath.close();

        // Satellite ground turf base
        Paint pSatBase = new Paint(Paint.ANTI_ALIAS_FLAG);
        pSatBase.setColor(Color.parseColor("#132219"));
        pSatBase.setStyle(Paint.Style.FILL);
        canvas.drawPath(quadPath, pSatBase);

        // Satellite turf border line
        Paint pSatBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        pSatBorder.setColor(Color.parseColor("#264230"));
        pSatBorder.setStyle(Paint.Style.STROKE);
        pSatBorder.setStrokeWidth(1.2f * density);
        canvas.drawPath(quadPath, pSatBorder);
    }

    // =========================================================================
    // 2D ELEVATION PROFILE RENDERING
    // =========================================================================

    private void draw2DScene(Canvas canvas, int w, int h, float density) {
        float paddingLeft = 36f * density;
        float paddingBottom = 26f * density;
        float paddingTop = 36f * density;
        float paddingRight = 24f * density;

        plotArea2D.set(paddingLeft, paddingTop, w - paddingRight, h - paddingBottom);

        double maxX = 50.0;
        double maxY = 20.0;

        if (currentResult != null && !currentResult.trajectory.isEmpty()) {
            maxX = Math.max(maxX, currentResult.rangeM * 1.15);
            maxY = Math.max(maxY, currentResult.apogeeM * 1.25);
        }
        if (targetDistanceM > 0) {
            maxX = Math.max(maxX, targetDistanceM * 1.15);
        }

        double stepX = calculateNiceStep(maxX, 5);
        double stepY = calculateNiceStep(maxY, 4);
        maxX = Math.ceil(maxX / stepX) * stepX;
        maxY = Math.ceil(maxY / stepY) * stepY;

        for (double gx = 0; gx <= maxX; gx += stepX) {
            float px = (float) (plotArea2D.left + (gx / maxX) * plotArea2D.width());
            canvas.drawLine(px, plotArea2D.top, px, plotArea2D.bottom, gridPaint);
            String label = String.format(Locale.US, "%.0fm", gx);
            float tw = textPaint.measureText(label);
            canvas.drawText(label, px - tw / 2f, plotArea2D.bottom + 16f * density, textPaint);
        }

        for (double gy = 0; gy <= maxY; gy += stepY) {
            float py = (float) (plotArea2D.bottom - (gy / maxY) * plotArea2D.height());
            canvas.drawLine(plotArea2D.left, py, plotArea2D.right, py, gridPaint);
            String label = String.format(Locale.US, "%.0fm", gy);
            canvas.drawText(label, plotArea2D.left - textPaint.measureText(label) - 6f * density, py + 4f * density, textPaint);
        }

        canvas.drawLine(plotArea2D.left, plotArea2D.bottom, plotArea2D.right, plotArea2D.bottom, axisPaint);
        canvas.drawLine(plotArea2D.left, plotArea2D.top, plotArea2D.left, plotArea2D.bottom, axisPaint);

        if (targetDistanceM > 0 && targetDistanceM <= maxX) {
            float tx = (float) (plotArea2D.left + (targetDistanceM / maxX) * plotArea2D.width());
            canvas.drawLine(tx, plotArea2D.top, tx, plotArea2D.bottom, targetPaint);
            Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            pFill.setColor(Color.parseColor("#FBBF24"));
            pFill.setStyle(Paint.Style.FILL);
            pFill.setTextSize(9f * density);
            canvas.drawCircle(tx, plotArea2D.top + 6f * density, 4f * density, pFill);
            canvas.drawText(String.format(Locale.US, "目标: %.0fm", targetDistanceM), tx + 6f * density, plotArea2D.top + 9f * density, pFill);
        }

        if (currentResult == null || currentResult.trajectory.isEmpty()) return;

        List<RocketPhysics.TrajectoryPoint> traj = currentResult.trajectory;
        waterPath.reset();
        airPath.reset();
        coastPath.reset();

        float apexPx = 0, apexPy = 0;
        float maxAltFound = -1f;

        RocketPhysics.TrajectoryPoint prevPt = null;
        for (int i = 0; i < traj.size(); i++) {
            RocketPhysics.TrajectoryPoint pt = traj.get(i);
            float px = (float) (plotArea2D.left + (pt.x / maxX) * plotArea2D.width());
            float py = (float) (plotArea2D.bottom - (pt.y / maxY) * plotArea2D.height());

            if (pt.y > maxAltFound) {
                maxAltFound = pt.y;
                apexPx = px;
                apexPy = py;
            }

            if (i == 0) {
                waterPath.moveTo(px, py);
            } else {
                if (pt.phase == 0) waterPath.lineTo(px, py);
                else if (pt.phase == 1) {
                    if (prevPt != null && prevPt.phase == 0) airPath.moveTo(px, py);
                    else airPath.lineTo(px, py);
                } else {
                    if (prevPt != null && prevPt.phase != 2) coastPath.moveTo(px, py);
                    else coastPath.lineTo(px, py);
                }
            }
            prevPt = pt;
        }

        canvas.drawPath(waterPath, waterPathPaint);
        canvas.drawPath(airPath, airPathPaint);
        canvas.drawPath(coastPath, coastPathPaint);

        if (maxAltFound > 0) {
            Paint apexDot = new Paint(Paint.ANTI_ALIAS_FLAG);
            apexDot.setColor(Color.parseColor("#38BDF8"));
            canvas.drawCircle(apexPx, apexPy, 4.5f * density, apexDot);
            Paint apexText = new Paint(Paint.ANTI_ALIAS_FLAG);
            apexText.setColor(Color.parseColor("#F8FAFC"));
            apexText.setTextSize(10f * density);
            apexText.setFakeBoldText(true);
            String apexLabel = String.format(Locale.US, "最高: %.1fm", currentResult.apogeeM);
            canvas.drawText(apexLabel, apexPx - apexText.measureText(apexLabel) / 2f, apexPy - 8f * density, apexText);
        }

        RocketPhysics.TrajectoryPoint lastPt = traj.get(traj.size() - 1);
        float landPx = (float) (plotArea2D.left + (lastPt.x / maxX) * plotArea2D.width());
        float landPy = plotArea2D.bottom;

        Paint landDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        landDot.setColor(Color.parseColor("#F43F5E"));
        canvas.drawCircle(landPx, landPy, 5f * density, landDot);

        Paint landText = new Paint(Paint.ANTI_ALIAS_FLAG);
        landText.setColor(Color.parseColor("#FDA4AF"));
        landText.setTextSize(10.5f * density);
        landText.setFakeBoldText(true);
        String landLabel = String.format(Locale.US, "落点: %.1fm", currentResult.rangeM);
        canvas.drawText(landLabel, Math.min(plotArea2D.right - landText.measureText(landLabel), landPx - 20f * density), landPy - 10f * density, landText);
    }

    // =========================================================================
    // TOP CONTROLS & CAMERA PRESETS OVERLAY
    // =========================================================================

    private void drawTopControls(Canvas canvas, float density, int width) {
        float btnHeight = 24f * density;
        float y0 = 6f * density;

        // 1. Mode Switcher (2D / 3D)
        float modeW = 60f * density;
        btnModeBounds.set(8f * density, y0, 8f * density + modeW, y0 + btnHeight);
        drawChip(canvas, btnModeBounds, density,
                currentMode == MODE_3D ? "3D 空间" : "2D 剖面",
                currentMode == MODE_3D ? Color.parseColor("#38BDF8") : Color.parseColor("#94A3B8"),
                true);

        // 2. Satellite Imagery Switcher
        float satW = 54f * density;
        float satX = btnModeBounds.right + 5f * density;
        btnSatBounds.set(satX, y0, satX + satW, y0 + btnHeight);
        drawChip(canvas, btnSatBounds, density,
                showSatelliteMap ? "卫星开" : "卫星关",
                showSatelliteMap ? Color.parseColor("#34D399") : Color.parseColor("#64748B"),
                showSatelliteMap);

        if (currentMode == MODE_3D) {
            // 3. Preset: Top-down view (俯视看侧偏)
            float topW = 42f * density;
            float topX = btnSatBounds.right + 5f * density;
            btnTopViewBounds.set(topX, y0, topX + topW, y0 + btnHeight);
            boolean isTopActive = camElevationDeg >= 80f;
            drawChip(canvas, btnTopViewBounds, density, "俯视",
                    isTopActive ? Color.parseColor("#FBBF24") : Color.parseColor("#64748B"), isTopActive);

            // 4. Preset: Side view (侧视看高度)
            float sideW = 42f * density;
            float sideX = btnTopViewBounds.right + 5f * density;
            btnSideViewBounds.set(sideX, y0, sideX + sideW, y0 + btnHeight);
            boolean isSideActive = camElevationDeg <= 12f;
            drawChip(canvas, btnSideViewBounds, density, "侧视",
                    isSideActive ? Color.parseColor("#FBBF24") : Color.parseColor("#64748B"), isSideActive);

            // 5. Preset: Reset (复位)
            float rstW = 42f * density;
            float rstX = btnSideViewBounds.right + 5f * density;
            btnResetBounds.set(rstX, y0, rstX + rstW, y0 + btnHeight);
            drawChip(canvas, btnResetBounds, density, "复位", Color.parseColor("#94A3B8"), false);
        }

        // 6. Wind HUD on top right
        drawWindHud(canvas, density, width - 8f * density, y0);
    }

    private void drawChip(Canvas canvas, RectF rect, float density, String text, int color, boolean highlight) {
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(highlight ? Color.parseColor("#14223A") : Color.parseColor("#0F172A"));
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(rect, 3f * density, 3f * density, bgPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(color);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * density);
        canvas.drawRoundRect(rect, 3f * density, 3f * density, borderPaint);

        Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
        textP.setColor(color);
        textP.setTextSize(9.5f * density);
        textP.setFakeBoldText(true);

        float tw = textP.measureText(text);
        canvas.drawText(text, rect.centerX() - tw / 2f, rect.centerY() + 3.5f * density, textP);
    }

    private void drawWindHud(Canvas canvas, float density, float right, float top) {
        float hudW = 92f * density;
        float hudH = 24f * density;
        float x0 = right - hudW;
        float y0 = top;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#0F172A"));
        bgPaint.setStyle(Paint.Style.FILL);
        RectF rect = new RectF(x0, y0, right, y0 + hudH);
        canvas.drawRoundRect(rect, 3f * density, 3f * density, bgPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#334155"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * density);
        canvas.drawRoundRect(rect, 3f * density, 3f * density, borderPaint);

        Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        txtPaint.setColor(Color.parseColor("#38BDF8"));
        txtPaint.setTextSize(9.5f * density);

        String windText;
        if (currentWindSpeedMs < 0.1) {
            windText = "风速: 无风";
        } else {
            double rad = Math.toRadians(currentWindAngleDeg);
            double crossComponent = currentWindSpeedMs * Math.sin(rad);
            String crossStr = (Math.abs(crossComponent) > 0.3) ?
                    String.format(Locale.US, " 侧%.1f", Math.abs(crossComponent)) : "";
            windText = String.format(Locale.US, "风:%.1f%s", currentWindSpeedMs, crossStr);
        }
        canvas.drawText(windText, x0 + 5f * density, y0 + 16f * density, txtPaint);
    }

    private double calculateNiceStep(double range, int approxCount) {
        double roughStep = range / approxCount;
        double magnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
        double normStep = roughStep / magnitude;

        double niceNorm;
        if (normStep < 1.5) niceNorm = 1;
        else if (normStep < 3) niceNorm = 2;
        else if (normStep < 7) niceNorm = 5;
        else niceNorm = 10;

        return niceNorm * magnitude;
    }
}
