package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Thin progress ring with a big green percentage in the middle,
 * like the reference app's speed-test screen.
 */
public class SpeedCircleView extends View {

    private float progress; // 0..1
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pctPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedCircleView(Context c) { super(c); init(); }
    public SpeedCircleView(Context c, AttributeSet a) { super(c, a); init(); }
    public SpeedCircleView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(10f);
        trackPaint.setColor(0xFF1E2A42);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(10f);
        arcPaint.setColor(0xFF43D14E);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setColor(0xFF43D14E);
        textPaint.setTextAlign(Paint.Align.CENTER);
        pctPaint.setColor(0xFF43D14E);
        pctPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 14f;
        canvas.drawCircle(cx, cy, r, trackPaint);
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, progress * 360f, false, arcPaint);

        int pct = Math.round(progress * 100f);
        String num = String.valueOf(pct);
        textPaint.setTextSize(r * 0.62f);
        pctPaint.setTextSize(r * 0.30f);
        float numW = textPaint.measureText(num);
        float pctW = pctPaint.measureText("%");
        float total = numW + pctW + 8f;
        canvas.drawText(num, cx - total / 2f + numW / 2f, cy + textPaint.getTextSize() * 0.35f, textPaint);
        canvas.drawText("%", cx - total / 2f + numW + 8f, cy + pctPaint.getTextSize() * 0.35f, pctPaint);
    }
}
