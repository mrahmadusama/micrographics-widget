package com.ahmad.micrographics;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;

/**
 * The widget's three colours. {@code ink} draws text and outline icons; {@code primary} fills
 * the pills; {@code tertiary} marks the things that change (weather icon, countdowns). With
 * Material You accents off all three are the plain ink, as on the Nothing lock screen.
 */
final class Palette {
    final int ink;
    final int primary;
    final int tertiary;

    private Palette(int ink, int primary, int tertiary) {
        this.ink = ink;
        this.primary = primary;
        this.tertiary = tertiary;
    }

    static Palette resolve(Context c) {
        boolean darkInk = darkInk(c);
        if (!Prefs.accents(c)) {
            int ink = c.getColor(darkInk ? R.color.mg_fg_dark : R.color.mg_fg);
            return new Palette(ink, ink, ink);
        }
        // Material 3 Expressive pairing from the wallpaper-based system palette: near-neutral
        // text, a saturated primary for the pills and the tertiary hue for live values.
        if (darkInk) {
            return new Palette(c.getColor(android.R.color.system_neutral1_900),
                    c.getColor(android.R.color.system_accent1_700),
                    c.getColor(android.R.color.system_accent3_600));
        }
        return new Palette(c.getColor(android.R.color.system_neutral1_50),
                c.getColor(android.R.color.system_accent1_200),
                c.getColor(android.R.color.system_accent3_200));
    }

    /** True when the widget should be drawn dark, for a light wallpaper. */
    private static boolean darkInk(Context c) {
        switch (Prefs.ink(c)) {
            case Prefs.INK_LIGHT:
                return false;
            case Prefs.INK_DARK:
                return true;
            case Prefs.INK_AUTO:
                WallpaperColors colors = null;
                try {
                    colors = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                } catch (RuntimeException e) {
                    // Some live wallpapers can't report colours; fall back to the theme.
                }
                if (colors != null) {
                    return (colors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
                }
                return !nightMode(c);
            default: // INK_SYSTEM: white in dark theme (the launcher dims the wallpaper), black in light.
                return !nightMode(c);
        }
    }

    private static boolean nightMode(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
