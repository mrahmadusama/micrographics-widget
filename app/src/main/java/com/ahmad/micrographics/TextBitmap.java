package com.ahmad.micrographics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Renders text to a bitmap. The launcher draws widget TextViews with system fonts only and
 * silently ignores an app's own fonts, so every piece of widget text goes through here instead.
 */
final class TextBitmap {
    private TextBitmap() {}

    static Typeface geistMono(Context c) {
        return c.getResources().getFont(R.font.geist_mono_mg);
    }

    static Typeface gooey(Context c) {
        return c.getResources().getFont(R.font.gooey_clock);
    }

    /**
     * Draws {@code text} at {@code sizeSp} in {@code color}. The bitmap spans the
     * font's full ascent and descent, so caps sit centred and rows of mixed text line up. Its
     * density matches the screen, so an ImageView shows it at exactly that size.
     */
    static Bitmap draw(Context c, String text, Typeface font, float sizeSp, int color) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return drawPx(c, text, font, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm), color);
    }

    static Bitmap drawPx(Context c, String text, Typeface font, float sizePx, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(font);
        paint.setTextSize(sizePx);
        paint.setColor(color);
        Paint.FontMetrics fm = paint.getFontMetrics();
        int w = Math.max(1, (int) Math.ceil(paint.measureText(text)));
        int h = Math.max(1, (int) Math.ceil(fm.descent - fm.ascent));
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        new Canvas(bmp).drawText(text, 0, -fm.ascent, paint);
        return bmp;
    }
}
