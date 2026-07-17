package com.dvid.dcam.app.shell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Compact battery icon with percentage text for managed kiosk status. */
public final class BatteryStatusView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private int percent = -1;

    public BatteryStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPercent(int percent) {
        this.percent = Math.max(-1, Math.min(100, percent));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float stroke = 1.5f * density;
        float terminalWidth = 3f * density;
        float right = getWidth() - terminalWidth - stroke;
        float iconHeight = 16f * density;
        float top = (getHeight() - iconHeight) / 2f;
        body.set(stroke, top, right, top + iconHeight);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(body, 3f * density, 3f * density, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(right, body.centerY() - 3f * density,
                getWidth(), body.centerY() + 3f * density,
                density, density, paint);
        if (percent >= 0) {
            float inset = 2.5f * density;
            float fillRight = body.left + inset
                    + (body.width() - inset * 2f) * percent / 100f;
            paint.setColor(percent <= 20
                    ? Color.rgb(255, 82, 82) : Color.rgb(123, 216, 143));
            canvas.drawRoundRect(body.left + inset, body.top + inset, fillRight,
                    body.bottom - inset, density, density, paint);
        }

        paint.setColor(Color.WHITE);
        paint.setTextSize(9f * density);
        String text = percent < 0 ? "--" : Integer.toString(percent);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = body.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, body.centerX(), baseline, paint);
    }
}
