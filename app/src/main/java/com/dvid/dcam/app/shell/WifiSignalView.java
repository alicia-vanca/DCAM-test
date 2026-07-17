package com.dvid.dcam.app.shell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Four-level Wi-Fi indicator for managed kiosk status. */
public final class WifiSignalView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int level;
    private boolean connected;

    public WifiSignalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setSignal(boolean connected, int level) {
        this.connected = connected;
        this.level = Math.max(0, Math.min(4, level));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        paint.setStrokeWidth(2f * density);
        float centerX = getWidth() / 2f;
        float bottom = getHeight() / 2f + 7f * density;
        for (int ring = 3; ring >= 1; ring--) {
            float radius = ring * 4.5f * density;
            arc.set(centerX - radius, bottom - radius, centerX + radius, bottom + radius);
            paint.setColor(segmentColor(ring + 1));
            canvas.drawArc(arc, 225f, 90f, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(segmentColor(1));
        canvas.drawCircle(centerX, bottom, 2f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
    }

    private int segmentColor(int segment) {
        return connected && level >= segment ? Color.WHITE : Color.rgb(90, 96, 104);
    }
}
