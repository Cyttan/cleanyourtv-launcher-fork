package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The optional weather line under the clock (config.xml show_weather, weather_city): "12° 晴" for one city,
 * from Open-Meteo, which needs no account or key. Off unless a city is set, so a Home without it reads nothing
 * and makes no request. The last answer is kept in preferences: Home shows it at once on the way back, and only
 * asks again once it is weatherRefreshMinutes old.
 */
final class WeatherLine {

    private static final String PREFS = "weather";
    /** Bumped when the shape of the cached line changes, so an old reading is not shown in the new shape. */
    private static final int FORMAT = 2;

    private WeatherLine() {}

    /** The cached line, "" when there is none yet. */
    static String cached(Context context) {
        return prefs(context).getString("text", "");
    }

    /**
     * Asks Open-Meteo for the weather on a background thread and answers the line on the main thread.
     * A failed request answers "" only when nothing was cached yet, so a stale reading survives a bad network.
     * With latitude and longitude set they are used as they are; otherwise the city is geocoded once.
     */
    static void refresh(Context context, String city, double lat, double lon, long ttlMs, Handler ui,
                        Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        long age = System.currentTimeMillis() - prefs.getLong("at", 0L);
        // Format 2: the line carries the place name. A cache written before that is refreshed at once.
        boolean currentFormat = prefs.getInt("format", 0) == FORMAT;
        if (age < ttlMs && currentFormat && !cached(app).isEmpty()) return;
        final String wanted = city == null ? "" : city.trim();
        if (wanted.isEmpty() && (lat == 0d || lon == 0d)) return;
        new Thread(() -> {
            String line = fetch(app, prefs, wanted, lat, lon);
            if (line == null) line = cached(app);
            final String answer = line;
            if (callback != null) ui.post(() -> callback.onLine(answer));
        }, "weather").start();
    }

    interface Callback {
        void onLine(String line);
    }

    /** One request: the coordinates from config, or the city geocoded once, then the current weather. */
    private static String fetch(Context context, SharedPreferences prefs, String city, double lat, double lon) {
        String label = city;
        try {
            if (lat == 0d || lon == 0d) {
                if (city.equals(prefs.getString("city", "")) && prefs.contains("lat")) {
                    lat = Double.longBitsToDouble(prefs.getLong("lat", 0L));
                    lon = Double.longBitsToDouble(prefs.getLong("lon", 0L));
                } else {
                    JSONObject found = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=zh&format=json&name="
                            + URLEncoder.encode(city, "UTF-8"));
                    JSONArray results = found.optJSONArray("results");
                    if (results == null || results.length() == 0) return null;
                    JSONObject place = results.getJSONObject(0);
                    lat = place.getDouble("latitude");
                    lon = place.getDouble("longitude");
                    // The geocoder's own name for the place is the one to show ("Yuncheng" for 运城).
                    label = place.optString("name", city);
                    prefs.edit().putString("city", city).putLong("lat", Double.doubleToRawLongBits(lat))
                            .putLong("lon", Double.doubleToRawLongBits(lon)).apply();
                }
            }
            JSONObject forecast = get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code"
                    + "&timezone=auto&latitude=" + lat + "&longitude=" + lon);
            JSONObject current = forecast.optJSONObject("current");
            if (current == null) return null;
            int temp = (int) Math.round(current.getDouble("temperature_2m"));
            int code = current.optInt("weather_code", -1);
            String line = (label == null || label.trim().isEmpty() ? "" : label.trim() + " ")
                    + temp + "° " + condition(code);
            prefs.edit().putString("text", line).putLong("at", System.currentTimeMillis())
                    .putInt("format", FORMAT).apply();
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    /** WMO weather code from Open-Meteo, in the words the rest of Home uses. */
    private static String condition(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 65) return code == 61 ? "小雨" : code == 63 ? "中雨" : "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code >= 71 && code <= 77) return code == 71 ? "小雪" : code == 73 ? "中雪" : "大雪";
        if (code >= 80 && code <= 82) return code == 80 ? "阵雨" : "强阵雨";
        if (code == 85 || code == 86) return "阵雪";
        if (code >= 95) return "雷阵雨";
        return "";
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "cleanyourtv-weather");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream();
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) body.append(line);
                return new JSONObject(body.toString());
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
