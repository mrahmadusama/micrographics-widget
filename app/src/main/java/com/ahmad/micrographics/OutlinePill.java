package com.ahmad.micrographics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Draws text in an outlined pill like the month labels ("(NOV)"): a thin, fully rounded
 * outline with the text inside in the same colour. Proportions come from
 * micrographics_month_*.xml, whose grid is 73.04 units tall.
 */
final class OutlinePill {
    private static final float GRID_HEIGHT = 73.04f;
    private static final float STROKE = 1.52f;
    /** Space between the outline and the text, on the grid. */
    private static final float SIDE_PADDING = 34.99f;
    private static final float CAP_SHARE = 0.65f;

    private OutlinePill() {}

    static Bitmap draw(Context c, String text, int color) {
        int h = Math.round(c.getResources().getDimension(R.dimen.mg_icon));
        float unit = h / GRID_HEIGHT;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(TextBitmap.geistMono(c));
        textPaint.setTextSize(100);
        Rect caps = new Rect();
        textPaint.getTextBounds("H", 0, 1, caps);
        textPaint.setTextSize(100f * CAP_SHARE * h / caps.height());
        textPaint.setColor(color);
        float textWidth = textPaint.measureText(text);
        int w = Math.round(textWidth + 2 * SIDE_PADDING * unit);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bmp);
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(STROKE * unit);
        outline.setColor(color);
        float inset = STROKE * unit / 2;
        RectF r = new RectF(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, outline);

        textPaint.getTextBounds("H", 0, 1, caps);
        float baseline = (h + caps.height()) / 2f - caps.bottom;
        canvas.drawText(text, (w - textWidth) / 2f, baseline, textPaint);
        return bmp;
    }
}
