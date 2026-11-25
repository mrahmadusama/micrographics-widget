package com.ahmad.micrographics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * The Shadow setting: a soft shadow under every glyph, for legibility on bright or busy
 * wallpapers. RemoteViews can't draw shadows, so with Shadow on every text, pill, icon and
 * countdown bracket is drawn here as a bitmap with {@link #PAD_DP} of room on each side for the
 * blur. WidgetUpdater grows the fixed-size views by the same amount so nothing shrinks.
 */
final class Shade {
    /** Room around each glyph for the shadow, a side. */
    static final float PAD_DP = 2f;
    private static final float BLUR_DP = 1.8f;
    private static final float DROP_DP = 0.5f;

    private Shade() {}

    static boolean on(Palette p) {
        return p.shadow != 0;
    }

    /** {@code src} with the shadow under it and padding around, or {@code src} itself when off. */
    static Bitmap apply(Context c, Bitmap src, Palette p) {
        if (!on(p)) return src;
        float d = density(c);
        int pad = Math.round(PAD_DP * d);
        Bitmap out = Bitmap.createBitmap(src.getWidth() + 2 * pad, src.getHeight() + 2 * pad, Bitmap.Config.ARGB_8888);
        out.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(out);
        drawShadowed(canvas, src, pad, pad, d, p.shadow);
        return out;
    }

    /** Text in {@code color}, with the shadow when it's on. */
    static Bitmap text(Context c, String text, Typeface font, float sizePx, int color, Palette p) {
        return apply(c, TextBitmap.drawPx(c, text, font, sizePx, color), p);
    }

    /**
     * A vector drawable in {@code color}, fitted into a box of {@code boxWdp} by {@code boxHdp}
     * ({@code boxWdp} 0 = as wide as its shape needs at that height), padded and shadowed. With
     * {@code fillHoles}, the cut-out shapes inside a filled pill (letters, arrow) are filled
     * with {@link Palette#hole} so they don't show the wallpaper.
     */
    static Bitmap icon(Context c, int res, int color, float boxWdp, float boxHdp, boolean fillHoles, Palette p) {
        float d = density(c);
        Drawable dr = c.getDrawable(res).mutate();
        float iw = dr.getIntrinsicWidth(), ih = dr.getIntrinsicHeight();
        float boxW = boxWdp > 0 ? boxWdp * d : Float.MAX_VALUE, boxH = boxHdp * d;
        float scale = Math.min(boxW / iw, boxH / ih);
        int w = Math.max(1, Math.round(iw * scale)), h = Math.max(1, Math.round(ih * scale));
        int bw = boxWdp > 0 ? Math.round(boxW) : w, bh = Math.round(boxH);

        int left = (bw - w) / 2, top = (bh - h) / 2;
        // The tint is applied to everything on the canvas the vector is drawn on, so the vector
        // gets a canvas of its own and is laid over the hole fill afterwards.
        Bitmap shape = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        dr.setTint(color);
        dr.setBounds(left, top, left + w, top + h);
        dr.draw(new Canvas(shape));

        Bitmap glyph = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(glyph);
        if (fillHoles && p.hole != 0) {
            // Under the pill's flat middle only, so the fill never shows past its rounded ends.
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(p.hole);
            g.drawRect(left + 0.3f * h, top + 0.1f * h, left + w - 0.3f * h, top + 0.9f * h, fill);
        }
        g.drawBitmap(shape, 0, 0, null);
        glyph.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        return apply(c, glyph, p);
    }

    /**
     * A whole "╰3MIN" bracket in one bitmap: the rounded corner, the underline and the text,
     * matching mg_count_corner and mg_count_line (2.4dp stroke on a 26dp-tall row).
     */
    static Bitmap bracket(Context c, String text, Typeface font, float textPx, int color, Palette p) {
        float d = density(c);
        Bitmap label = TextBitmap.drawPx(c, text, font, textPx, color);
        int w = Math.round(10 * d) + label.getWidth() + Math.round(6 * d);
        int h = Math.round(26 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bmp);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f * d);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(color);
        Path corner = new Path();
        corner.moveTo(1.8f * d, 1.2f * d);
        corner.lineTo(1.8f * d, 16.3f * d);
        corner.cubicTo(1.8f * d, 21f * d, 5.3f * d, 24.8f * d, 10f * d, 24.8f * d);
        corner.lineTo(w - 1.2f * d, 24.8f * d);
        canvas.drawPath(corner, stroke);
        float textTop = (23 * d - label.getHeight()) / 2f;
        canvas.drawBitmap(label, 10 * d, textTop, null);
        return apply(c, bmp, p);
    }

    private static void drawShadowed(Canvas canvas, Bitmap src, float x, float y, float d, int shadowColor) {
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(BLUR_DP * d, BlurMaskFilter.Blur.NORMAL));
        int[] offset = new int[2];
        Bitmap alpha = src.extractAlpha(blur, offset);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(shadowColor);
        canvas.drawBitmap(alpha, x + offset[0], y + offset[1] + DROP_DP * d, shadow);
        canvas.drawBitmap(src, x, y, null);
    }

    private static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }
}
