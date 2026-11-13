package com.ahmad.micrographics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;

/**
 * Draws a filled speech-bubble pill with the text cut out, in the style of Nothing's weather
 * labels ("(SUNNY)"), for text that isn't one of those fixed drawables. The end caps are traced
 * from micrographics_weather_desc_*.xml, whose grid is 73.04 units tall.
 */
final class BubblePill {
    private static final float GRID_HEIGHT = 73.04f;
    /** Width of each end cap on the grid; the text sits between them. */
    private static final float CAP_WIDTH = 42.61f;
    /** Space between a cap and the text on the grid. */
    private static final float TEXT_GAP = 3f;
    /** Cap height of the text as a share of the pill height, as in the weather labels. */
    private static final float CAP_SHARE = 0.65f;

    private BubblePill() {}

    /** Width in px of the pill for {@code text} at {@code heightPx}, without drawing it. */
    static float width(Context c, String text, int heightPx) {
        Paint paint = textPaint(c, heightPx);
        return (2 * (CAP_WIDTH + TEXT_GAP)) * heightPx / GRID_HEIGHT + paint.measureText(text);
    }

    static Bitmap draw(Context c, String text, int color) {
        int h = Math.round(c.getResources().getDimension(R.dimen.mg_icon));
        float unit = h / GRID_HEIGHT;
        Paint textPaint = textPaint(c, h);
        float textWidth = textPaint.measureText(text);
        float middle = 2 * TEXT_GAP * unit + textWidth;
        int w = Math.round(2 * CAP_WIDTH * unit + middle);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);

        canvas.save();
        canvas.scale(unit, unit);
        float rightCapStart = CAP_WIDTH + middle / unit;
        canvas.drawPath(leftCap(), fill);
        canvas.drawRect(CAP_WIDTH - 0.5f, 0, rightCapStart + 0.5f, GRID_HEIGHT, fill);
        canvas.translate(rightCapStart, 0);
        canvas.drawPath(rightCap(), fill);
        canvas.restore();

        textPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        Rect caps = new Rect();
        textPaint.getTextBounds("H", 0, 1, caps);
        float baseline = (h + caps.height()) / 2f - caps.bottom;
        canvas.drawText(text, (CAP_WIDTH + TEXT_GAP) * unit, baseline, textPaint);
        return bmp;
    }

    private static Paint textPaint(Context c, int heightPx) {
        Typeface font = TextBitmap.geistMono(c);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(font);
        paint.setTextSize(100);
        Rect caps = new Rect();
        paint.getTextBounds("H", 0, 1, caps);
        paint.setTextSize(100f * CAP_SHARE * heightPx / caps.height());
        return paint;
    }

    /** The rounded left end with the speech-bubble tail at the bottom. */
    private static Path leftCap() {
        Path p = new Path();
        p.moveTo(11.8f, 9.6f);
        p.cubicTo(19.37f, 3.27f, 30.02f, 0f, 42.61f, 0f);
        p.lineTo(42.61f, 73.04f);
        p.cubicTo(35.74f, 73.04f, 28.12f, 72.38f, 21.37f, 69.3f);
        p.cubicTo(21.28f, 69.36f, 21.19f, 69.41f, 21.09f, 69.46f);
        p.cubicTo(19.96f, 70.11f, 18.34f, 70.93f, 16.41f, 71.61f);
        p.cubicTo(12.59f, 72.94f, 7.14f, 73.83f, 1.73f, 71.26f);
        p.cubicTo(0.91f, 70.87f, 0.3f, 70.12f, 0.09f, 69.23f);
        p.cubicTo(-0.13f, 68.34f, 0.06f, 67.41f, 0.61f, 66.68f);
        p.cubicTo(6.46f, 58.92f, 4.73f, 54.18f, 4.66f, 53.98f);
        p.lineTo(4.55f, 53.73f);
        p.cubicTo(4.24f, 52.98f, 3.13f, 50.24f, 2.09f, 46.97f);
        p.cubicTo(1.07f, 43.8f, 0f, 39.75f, 0f, 36.52f);
        p.cubicTo(0f, 25.25f, 4.18f, 15.99f, 11.8f, 9.6f);
        p.close();
        return p;
    }

    /** The plain rounded right end, starting at x = 0. */
    private static Path rightCap() {
        Path p = new Path();
        p.moveTo(0f, 0f);
        p.cubicTo(12.58f, 0f, 23.24f, 3.27f, 30.8f, 9.6f);
        p.cubicTo(38.43f, 15.99f, 42.61f, 25.25f, 42.61f, 36.52f);
        p.cubicTo(42.61f, 47.79f, 38.43f, 57.05f, 30.8f, 63.44f);
        p.cubicTo(23.24f, 69.78f, 12.58f, 73.04f, 0f, 73.04f);
        p.close();
        return p;
    }
}
