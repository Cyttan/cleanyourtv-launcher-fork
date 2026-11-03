package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The owner's own app icons: a picture named after the package in `icons/` inside the launcher's files
 * directory (icons/com.example.app.png), drawn as that app's tile artwork instead of its banner or icon.
 * An app without a picture keeps its normal tile. The folder is read again whenever it changes, so dropping
 * a file in and coming back to Home is enough.
 */
final class IconOverrides {

    static final String DIR = "icons";
    private static final String[] EXTS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final String PKG = "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+";

    private static Map<String, Drawable> cache = Collections.emptyMap();
    private static String stamp = "";

    private IconOverrides() {}

    /** The folder's state: its file count and the newest change, "" when there is no folder. */
    private static String stampOf(File dir) {
        if (dir == null || !dir.isDirectory()) return "";
        File[] files = dir.listFiles();
        if (files == null) return "";
        long newest = dir.lastModified();
        int n = 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            n++;
            if (f.lastModified() > newest) newest = f.lastModified();
        }
        return n + ":" + newest;
    }

    /** True when the folder changed since the last read, so Home has to rebuild its tiles. */
    static synchronized boolean changed(Context context) {
        return !stampOf(dir(context)).equals(stamp);
    }

    /** The owner's picture for this package, or null for the app's ordinary tile. */
    static synchronized Drawable drawable(Context context, String pkg) {
        File dir = dir(context);
        String now = stampOf(dir);
        if (!now.equals(stamp)) read(context, dir, now);
        return cache.get(pkg);
    }

    private static void read(Context context, File dir, String now) {
        stamp = now;
        Map<String, Drawable> map = new HashMap<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            Resources res = context.getResources();
            for (File f : files) {
                String pkg = packageOf(f.getName());
                if (pkg == null) continue;
                Drawable d = decode(f, res);
                if (d != null) map.put(pkg, d);
            }
        }
        cache = map;
    }

    private static File dir(Context context) {
        File dir = Config.dir(context);
        return dir == null ? null : new File(dir, DIR);
    }

    /** "com.example.app" from "com.example.app.png"; null for any other file. */
    private static String packageOf(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String ext : EXTS) {
            if (lower.length() > ext.length() && lower.endsWith(ext)) {
                String pkg = name.substring(0, name.length() - ext.length());
                return pkg.matches(PKG) ? pkg : null;
            }
        }
        return null;
    }

    /** Decoded down to a little over tile size: a 4K picture would otherwise cost the TV a lot of memory. */
    private static Drawable decode(File file, Resources res) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 640) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) return null;
        return new BitmapDrawable(res, bitmap);
    }
}
