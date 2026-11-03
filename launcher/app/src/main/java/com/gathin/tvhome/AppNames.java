package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The owner's own names for app tiles, set with Rename on a tile's long-press menu. One entry per package;
 * an empty or missing entry means the app's own label.
 */
final class AppNames {

    private static final String PREFS = "app_names";

    private AppNames() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The owner's name for this package, or null for the app's own label. */
    static String get(Context context, String pkg) {
        String name = prefs(context).getString(pkg, null);
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    /** Saves a name, or clears it when the text is empty. */
    static void set(Context context, String pkg, String name) {
        if (name == null || name.trim().isEmpty()) prefs(context).edit().remove(pkg).apply();
        else prefs(context).edit().putString(pkg, name.trim()).apply();
    }
}
