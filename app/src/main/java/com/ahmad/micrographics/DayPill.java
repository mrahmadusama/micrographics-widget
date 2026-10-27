package com.ahmad.micrographics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws the filled "(25)" pill with the day number cut out, so the wallpaper shows through
 * the digits as it does on the Nothing lock screen. RemoteViews can't do knock-out text.
 */
final class DayPill {
    private static final float HEIGHT_DP = 24;
    /** Cap height of the digits as a share of pill height, matching the month pills. */
    private static final float CAP_SHARE = 0.60f;
    private static final float SIDE_PADDING_SHARE = 0.42f;

    private DayPill() {}

    static Bitmap draw(Context c, int day) {
        float density = c.getResources().getDisplayMetrics().density;
        int h = Math.round(HEIGHT_DP * density);
        Typeface font = c.getResources().getFont(R.font.geist_mono_mg);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(font);
        text.setTextSize(100);
        Rect capBounds = new Rect();
        text.getTextBounds("H", 0, 1, capBounds);
        text.setTextSize(100f * CAP_SHARE * h / capBounds.height());

        String label = String.valueOf(day);
        float textWidth = text.measureText(label);
        int w = Math.round(textWidth + 2 * SIDE_PADDING_SHARE * h);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(c.getColor(R.color.mg_fg));
        canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, fill);

        text.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        text.getTextBounds(label, 0, label.length(), capBounds);
        float baseline = (h + capBounds.height()) / 2f - capBounds.bottom;
        canvas.drawText(label, (w - textWidth) / 2f, baseline, text);
        return bmp;
    }
}
