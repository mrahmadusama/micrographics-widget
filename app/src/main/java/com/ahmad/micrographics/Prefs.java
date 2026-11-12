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
    private static final String WIDGET_COLOR = "widget_color";
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

    /** One of WidgetColor.AUTO, LIGHT or DARK. */
    static int widgetColor(Context c) {
        return sp(c).getInt(WIDGET_COLOR, WidgetColor.AUTO);
    }

    static void setWidgetColor(Context c, int value) {
        sp(c).edit().putInt(WIDGET_COLOR, value).apply();
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
