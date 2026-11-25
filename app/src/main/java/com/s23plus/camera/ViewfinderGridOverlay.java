package com.s23plus.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Superposicion ligera de guias de encuadre (regla de tercios y cruz central)
 * para el visor Camera2 universal, sin asignaciones en onDraw().
 */
public final class ViewfinderGridOverlay extends View {

    private final Paint thirdsPaint;
    private final Paint faintThirdsPaint;
    private final Paint crosshairPaint;
    private final float crosshairArmPx;

    private CameraViewerMath.GridMode gridMode = CameraViewerMath.GridMode.OFF;

    public ViewfinderGridOverlay(Context context) {
        this(context, null);
    }

    public ViewfinderGridOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewfinderGridOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;

        thirdsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thirdsPaint.setColor(Color.parseColor("#77FFFFFF"));
        thirdsPaint.setStyle(Paint.Style.STROKE);
        thirdsPaint.setStrokeWidth(1.2f * density);

        faintThirdsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        faintThirdsPaint.setColor(Color.parseColor("#44FFFFFF"));
        faintThirdsPaint.setStyle(Paint.Style.STROKE);
        faintThirdsPaint.setStrokeWidth(1.0f * density);

        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setColor(Color.parseColor("#AA38BDF8"));
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(1.6f * density);

        crosshairArmPx = 16.0f * density;
    }

    public void setGridMode(CameraViewerMath.GridMode mode) {
        this.gridMode = (mode != null) ? mode : CameraViewerMath.GridMode.OFF;
        invalidate();
    }

    public CameraViewerMath.GridMode getGridMode() {
        return gridMode;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gridMode == CameraViewerMath.GridMode.OFF) {
            return;
        }

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float x1 = w / 3.0f;
        float x2 = (2.0f * w) / 3.0f;
        float y1 = h / 3.0f;
        float y2 = (2.0f * h) / 3.0f;

        Paint gridPaint = (gridMode == CameraViewerMath.GridMode.CROSSHAIR) ? faintThirdsPaint : thirdsPaint;
        canvas.drawLine(x1, 0.0f, x1, h, gridPaint);
        canvas.drawLine(x2, 0.0f, x2, h, gridPaint);
        canvas.drawLine(0.0f, y1, w, y1, gridPaint);
        canvas.drawLine(0.0f, y2, w, y2, gridPaint);

        if (gridMode == CameraViewerMath.GridMode.CROSSHAIR) {
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            canvas.drawLine(cx - crosshairArmPx, cy, cx + crosshairArmPx, cy, crosshairPaint);
            canvas.drawLine(cx, cy - crosshairArmPx, cx, cy + crosshairArmPx, crosshairPaint);
        }
    }
}
