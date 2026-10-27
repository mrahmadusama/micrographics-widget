package com.ahmad.micrographics;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Current conditions from Open-Meteo (free, no API key), mapped onto the Micrographics icons. */
final class Weather {
    static final long MAX_AGE_MS = 30 * 60 * 1000L;
    private static final int TIMEOUT_MS = 8000;
    private static final double WINDY_KMH = 40;

    final double temperature;
    /** WMO weather interpretation code, or -1 when unknown. */
    final int code;
    final boolean isDay;
    final double windKmh;
    final long fetchedAt;

    Weather(double temperature, int code, boolean isDay, double windKmh, long fetchedAt) {
        this.temperature = temperature;
        this.code = code;
        this.isDay = isDay;
        this.windKmh = windKmh;
        this.fetchedAt = fetchedAt;
    }

    boolean isStale() {
        return System.currentTimeMillis() - fetchedAt > MAX_AGE_MS;
    }

    /** Fetches and caches new weather when the cached reading is missing or old. Blocks; call off the main thread. */
    static void refreshIfStale(Context c) {
        if (!Prefs.hasLocation(c)) return;
        Weather cached = Prefs.weather(c);
        if (cached != null && !cached.isStale()) return;
        try {
            Prefs.setWeather(c, fetch(Prefs.lat(c), Prefs.lon(c), Prefs.fahrenheit(c)));
        } catch (IOException | JSONException e) {
            // Offline or API hiccup: keep showing the last reading and try again next refresh.
        }
    }

    static Weather fetch(double lat, double lon, boolean fahrenheit) throws IOException, JSONException {
        Uri uri = Uri.parse("https://api.open-meteo.com/v1/forecast").buildUpon()
                .appendQueryParameter("latitude", String.valueOf(lat))
                .appendQueryParameter("longitude", String.valueOf(lon))
                .appendQueryParameter("current", "temperature_2m,weather_code,is_day,wind_speed_10m")
                .appendQueryParameter("temperature_unit", fahrenheit ? "fahrenheit" : "celsius")
                .appendQueryParameter("wind_speed_unit", "kmh")
                .build();
        JSONObject current = new JSONObject(get(uri.toString())).getJSONObject("current");
        return new Weather(current.getDouble("temperature_2m"), current.getInt("weather_code"),
                current.getInt("is_day") == 1, current.optDouble("wind_speed_10m", 0),
                System.currentTimeMillis());
    }

    /** Looks up a city name. Returns {lat, lon} and writes the display name into {@code nameOut[0]}, or null. */
    static double[] geocode(String city, String[] nameOut) throws IOException, JSONException {
        Uri uri = Uri.parse("https://geocoding-api.open-meteo.com/v1/search").buildUpon()
                .appendQueryParameter("name", city)
                .appendQueryParameter("count", "1")
                .build();
        JSONArray results = new JSONObject(get(uri.toString())).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        String country = r.optString("country", "");
        nameOut[0] = country.isEmpty() ? r.getString("name") : r.getString("name") + ", " + country;
        return new double[] {r.getDouble("latitude"), r.getDouble("longitude")};
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            conn.disconnect();
        }
    }

    /** Micrographics condition name, used to pick micrographics_weather_icon_* and _desc_*. */
    String condition() {
        if (code < 0) return "unavailable";
        if (code >= 95) return "thunder";
        if (code == 66 || code == 67 || code == 56 || code == 57) return "sleet";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snowy";
        if ((code >= 51 && code <= 65) || (code >= 80 && code <= 82)) return "rainy";
        if (code == 45 || code == 48) return "foggy";
        if (windKmh >= WINDY_KMH) return "windy";
        switch (code) {
            case 0:
                return isDay ? "sunny" : "clear_night";
            case 1:
            case 2:
                return isDay ? "partly_sunny" : "cloudy_night";
            case 3:
                return "overcast";
            default:
                return isDay ? "cloudy" : "cloudy_night";
        }
    }

    static int iconRes(String condition) {
        switch (condition) {
            case "sunny": return R.drawable.micrographics_weather_icon_sunny;
            case "clear_night": return R.drawable.micrographics_weather_icon_clear_night;
            case "partly_sunny": return R.drawable.micrographics_weather_icon_partly_sunny;
            case "cloudy": return R.drawable.micrographics_weather_icon_cloudy;
            case "cloudy_night": return R.drawable.micrographics_weather_icon_cloudy_night;
            case "overcast": return R.drawable.micrographics_weather_icon_overcast;
            case "foggy": return R.drawable.micrographics_weather_icon_foggy;
            case "rainy": return R.drawable.micrographics_weather_icon_rainy;
            case "sleet": return R.drawable.micrographics_weather_icon_sleet;
            case "snowy": return R.drawable.micrographics_weather_icon_snowy;
            case "thunder": return R.drawable.micrographics_weather_icon_thunder;
            case "windy": return R.drawable.micrographics_weather_icon_windy;
            default: return R.drawable.micrographics_weather_icon_unavailable;
        }
    }

    static int descRes(String condition) {
        switch (condition) {
            case "sunny": return R.drawable.micrographics_weather_desc_sunny;
            case "clear_night": return R.drawable.micrographics_weather_desc_clear_night;
            case "partly_sunny": return R.drawable.micrographics_weather_desc_partly_sunny;
            case "cloudy": return R.drawable.micrographics_weather_desc_cloudy;
            case "cloudy_night": return R.drawable.micrographics_weather_desc_cloudy_night;
            case "overcast": return R.drawable.micrographics_weather_desc_overcast;
            case "foggy": return R.drawable.micrographics_weather_desc_foggy;
            case "rainy": return R.drawable.micrographics_weather_desc_rainy;
            case "sleet": return R.drawable.micrographics_weather_desc_sleet;
            case "snowy": return R.drawable.micrographics_weather_desc_snowy;
            case "thunder": return R.drawable.micrographics_weather_desc_thunder;
            case "windy": return R.drawable.micrographics_weather_desc_windy;
            default: return R.drawable.micrographics_weather_desc_unavailable;
        }
    }
}
