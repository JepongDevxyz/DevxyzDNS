package com.jepongdevxyz.devxyzdns;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Dotted-globe like the welcome screen of the reference app:
 * orthographic projection of a lat/long dot grid, front hemisphere only.
 */
public class GlobeView extends View {

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float tilt = 0.35f;

    public GlobeView(Context c) { super(c); init(); }
    public GlobeView(Context c, AttributeSet a) { super(c, a); init(); }
    public GlobeView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        dotPaint.setColor(0xFF5A6B8C);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float R = Math.min(cx, cy) * 0.94f;

        for (int lat = -75; lat <= 75; lat += 6) {
            double latR = Math.toRadians(lat);
            double cosLat = Math.cos(latR);
            for (int lon = -180; lon < 180; lon += 6) {
                double lonR = Math.toRadians(lon + tilt * 57.3);
                // orthographic: visible when cos(lon)*cos(lat) > 0
                double z = Math.cos(lonR) * cosLat;
                if (z <= 0.02) continue;
                float x = (float) (cx + R * Math.sin(lonR) * cosLat);
                float y = (float) (cy - R * Math.sin(latR));
                float dotR = (float) (2.6 * (0.55 + 0.45 * z));
                canvas.drawCircle(x, y, dotR, dotPaint);
            }
        }
    }
}
