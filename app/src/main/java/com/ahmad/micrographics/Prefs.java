package com.ahmad.micrographics;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings and the last weather reading, shared by the widget and the setup screen. */
final class Prefs {
    private static final String FILE = "micrographics";
    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final String PLACE = "place";
    private static final String FAHRENHEIT = "fahrenheit";
    private static final String INK = "ink";
    private static final String ACCENTS = "accents";
    private static final String SHOW = "show_";
    private static final String TEMP = "weather_temp";
    private static final String CODE = "weather_code";
    private static final String IS_DAY = "weather_is_day";
    private static final String WIND = "weather_wind";
    private static final String FETCHED_AT = "weather_fetched_at";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean hasLocation(Context c) {
        return sp(c).contains(LAT);
    }

    static double lat(Context c) {
        return Double.longBitsToDouble(sp(c).getLong(LAT, 0));
    }

    static double lon(Context c) {
        return Double.longBitsToDouble(sp(c).getLong(LON, 0));
    }

    static String place(Context c) {
        return sp(c).getString(PLACE, null);
    }

    /** Storing a new location also drops the cached weather so the next refresh fetches it. */
    static void setLocation(Context c, double lat, double lon, String place) {
        sp(c).edit()
                .putLong(LAT, Double.doubleToRawLongBits(lat))
                .putLong(LON, Double.doubleToRawLongBits(lon))
                .putString(PLACE, place)
                .remove(FETCHED_AT)
                .apply();
    }

    static boolean fahrenheit(Context c) {
        return sp(c).getBoolean(FAHRENHEIT, false);
    }

    static void setFahrenheit(Context c, boolean value) {
        sp(c).edit().putBoolean(FAHRENHEIT, value).remove(FETCHED_AT).apply();
    }

    /**
     * Widget colour: white glyphs for dark wallpapers, black for light ones, follow the system
     * theme, or pick from the wallpaper's brightness (the default).
     */
    static final int INK_LIGHT = 0;
    static final int INK_DARK = 1;
    static final int INK_SYSTEM = 2;
    static final int INK_AUTO = 3;

    static int ink(Context c) {
        return sp(c).getInt(INK, INK_AUTO);
    }

    /** Colour the pills and live values with the Material You palette. */
    static boolean accents(Context c) {
        return sp(c).getBoolean(ACCENTS, true);
    }

    static void setAccents(Context c, boolean value) {
        sp(c).edit().putBoolean(ACCENTS, value).apply();
    }

    /** Optional rows, each switchable in the setup screen. All are on by default. */
    static final String ROW_WEATHER = "weather";
    static final String ROW_MEETING = "meeting";
    static final String ROW_BIRTHDAY = "birthday";
    static final String ROW_ALARM = "alarm";
    static final String ROW_CHARGING = "charging";

    static boolean show(Context c, String row) {
        return sp(c).getBoolean(SHOW + row, true);
    }

    static void setShow(Context c, String row, boolean value) {
        sp(c).edit().putBoolean(SHOW + row, value).apply();
    }

    static void setInk(Context c, int value) {
        sp(c).edit().putInt(INK, value).apply();
    }

    static Weather weather(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains(FETCHED_AT)) return null;
        return new Weather(p.getFloat(TEMP, 0), p.getInt(CODE, -1), p.getBoolean(IS_DAY, true),
                p.getFloat(WIND, 0), p.getLong(FETCHED_AT, 0));
    }

    static void setWeather(Context c, Weather w) {
        sp(c).edit()
                .putFloat(TEMP, (float) w.temperature)
                .putInt(CODE, w.code)
                .putBoolean(IS_DAY, w.isDay)
                .putFloat(WIND, (float) w.windKmh)
                .putLong(FETCHED_AT, w.fetchedAt)
                .apply();
    }
}
