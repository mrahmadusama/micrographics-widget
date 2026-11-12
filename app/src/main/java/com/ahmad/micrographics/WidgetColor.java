package com.ahmad.micrographics;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;

/**
 * The widgets' foreground colour: light on dark wallpapers, dark on light ones. In AUTO mode it
 * follows the home screen wallpaper's "supports dark text" hint, the same signal the launcher
 * uses to darken its own icon labels; it is checked on every redraw, so a wallpaper change shows
 * up within a minute.
 */
final class WidgetColor {
    static final int AUTO = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private WidgetColor() {}

    static int foreground(Context c) {
        boolean dark;
        switch (Prefs.widgetColor(c)) {
            case LIGHT:
                dark = false;
                break;
            case DARK:
                dark = true;
                break;
            default:
                dark = wallpaperIsLight(c);
        }
        return c.getColor(dark ? R.color.mg_fg_dark : R.color.mg_fg);
    }

    private static boolean wallpaperIsLight(Context c) {
        WallpaperManager wm = c.getSystemService(WallpaperManager.class);
        if (wm == null) return false;
        try {
            WallpaperColors colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            return colors != null && (colors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
