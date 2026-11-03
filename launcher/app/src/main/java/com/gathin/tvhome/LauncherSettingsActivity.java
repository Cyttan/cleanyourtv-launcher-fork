package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.Manifest;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    /** Started from a tile's long-press menu: pick a picture for that one app straight away. */
    static final String EXTRA_ICON_PACKAGE = "com.gathin.tvhome.extra.ICON_PACKAGE";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;
    private TextView lunarChoice;
    private TextView screensaverToggle;
    private TextView iconPickerChoice;
    /** The package waiting for READ_MEDIA_IMAGES before the file browser opens. */
    private String pendingIconPkg;
    private static final int REQUEST_READ_IMAGES = 41;
    /** Minutes an idle Home waits before the clock screensaver; 0 is off. */
    private int idleMinutes;
    private boolean lunar;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        lunar = config.lunarDate;
        idleMinutes = config.homeIdleClockMinutes;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        screensaverToggle = findViewById(R.id.screensaverToggle);
        screensaverToggle.setOnClickListener(v -> showScreensaverPicker());
        iconPickerChoice = findViewById(R.id.iconPickerChoice);
        iconPickerChoice.setOnClickListener(v -> showIconAppPicker());
        String iconPkg = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_ICON_PACKAGE);
        if (iconPkg != null) iconPickerChoice.post(() -> showIconImagePicker(iconPkg));
        lunarChoice = findViewById(R.id.lunarChoice);
        lunarChoice.setOnClickListener(v -> {
            lunar = !lunar;
            choices.edit().putBoolean(Config.LUNAR_KEY, lunar).apply();
            changed();
            refresh();
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.LUNAR_KEY).remove(Config.IDLE_CLOCK_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            lunar = current.lunarDate;
            idleMinutes = current.homeIdleClockMinutes;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    /** Off, or minutes: the same row reads "Screensaver: off" or "Screensaver: on (15 min)". */
    private void showScreensaverPicker() {
        final int[] values = {0, 5, 15, 30};
        String[] labels = new String[values.length];
        labels[0] = getString(R.string.off);
        for (int i = 1; i < values.length; i++) labels[i] = getString(R.string.minutes_value, values[i]);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == idleMinutes) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    idleMinutes = values[which];
                    choices.edit().putInt(Config.IDLE_CLOCK_KEY, idleMinutes).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Custom app icons: pick the app, then a picture from icon-picker/ in the launcher's files dir. */
    private void showIconAppPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            apps.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        for (String pkg : LibraryApps.load(this)) {
            if (!seen.add(pkg)) continue;
            try {
                apps.add(new String[]{String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))), pkg});
            } catch (PackageManager.NameNotFoundException ignored) {
                // uninstalled since it was added to Home
            }
        }
        if (apps.isEmpty()) return;
        apps.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i)[0];
        new AlertDialog.Builder(this)
                .setTitle(R.string.icon_apps_label)
                .setSingleChoiceItems(labels, -1, (dialog, which) -> {
                    dialog.dismiss();
                    showIconImagePicker(apps.get(which)[1]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showIconImagePicker(String pkg) {
        List<File> images = iconPickerImages();
        if (images.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.icon_picker_label)
                    .setMessage(R.string.icon_picker_empty)
                    .setPositiveButton(R.string.icon_from_files, (dialog, which) -> chooseIconFromFiles(pkg))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (File f : images) labels.add(f.getName());
        showThumbList(getString(R.string.icon_images_label) + "  " + appLabel(pkg), images, labels,
                file -> applyIcon(pkg, file),
                hasCustomIcon(pkg) ? getString(R.string.icon_restore) : null,
                () -> restoreIcon(pkg),
                getString(R.string.icon_from_files),
                () -> chooseIconFromFiles(pkg));
    }

    private void chooseIconFromFiles(String pkg) {
        pendingIconPkg = pkg;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_READ_IMAGES);
            return;
        }
        browseFiles(pkg, browseStart());
    }

    /** Pictures first (that is where icon candidates usually are), the storage root otherwise. */
    private File browseStart() {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return pictures != null && pictures.isDirectory() ? pictures : Environment.getExternalStorageDirectory();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQUEST_READ_IMAGES) return;
        String pkg = pendingIconPkg;
        pendingIconPkg = null;
        if (pkg == null) return;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.icon_files_denied, Toast.LENGTH_LONG).show();
        }
        browseFiles(pkg, browseStart());
    }

    /** A plain folder browser: the remote moves through folders and pictures, one dialog per level. */
    private void browseFiles(final String pkg, File dir) {
        File[] all = dir == null ? null : dir.listFiles();
        List<File> folders = new ArrayList<>();
        List<File> pictures = new ArrayList<>();
        if (all != null) {
            for (File f : all) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) folders.add(f);
                else if (isImage(f.getName())) pictures.add(f);
            }
        }
        folders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        pictures.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        File parent = dir == null ? null : dir.getParentFile();
        final List<File> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        if (parent != null) {
            entries.add(parent);
            labels.add(".. (" + parent.getName() + ")");
        }
        entries.addAll(folders);
        for (File f : folders) labels.add(f.getName() + "/");
        entries.addAll(pictures);
        for (File f : pictures) labels.add(f.getName());
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.icon_files_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        showThumbList(dir == null ? "/" : dir.getAbsolutePath(), entries, labels,
                file -> {
                    if (file.isDirectory()) browseFiles(pkg, file);
                    else applyIcon(pkg, file);
                },
                null, null, null, null);
    }

    /** A dialog list with a thumbnail per picture and a folder tile, so the remote picks by sight. */
    private void showThumbList(String title, final List<File> files, final List<String> labels,
                               final java.util.function.Consumer<File> onPick,
                               String neutral, final Runnable onNeutral,
                               String positive, final Runnable onPositive) {
        final float density = getResources().getDisplayMetrics().density;
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return files.size();
            }

            @Override
            public Object getItem(int position) {
                return files.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                File file = files.get(position);
                LinearLayout row = new LinearLayout(LauncherSettingsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (10 * density);
                row.setPadding(pad, (int) (6 * density), pad, (int) (6 * density));
                ImageView picture = new ImageView(LauncherSettingsActivity.this);
                LinearLayout.LayoutParams frame = new LinearLayout.LayoutParams(
                        (int) (104 * density), (int) (58 * density));
                frame.rightMargin = (int) (14 * density);
                picture.setLayoutParams(frame);
                picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Drawable thumb = thumbnail(file);
                picture.setImageDrawable(thumb);
                row.addView(picture);
                TextView name = new TextView(LauncherSettingsActivity.this);
                name.setText(labels.get(position));
                name.setTextSize(17);
                name.setTextColor(0xFFFFFFFF);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                row.addView(name);
                return row;
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setAdapter(adapter, (dialog, which) -> {
                    dialog.dismiss();
                    onPick.accept(files.get(which));
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (neutral != null) builder.setNeutralButton(neutral, (dialog, which) -> onNeutral.run());
        if (positive != null) builder.setPositiveButton(positive, (dialog, which) -> onPositive.run());
        builder.show();
    }

    private final Map<String, Bitmap> thumbnails = new HashMap<>();

    /** A small picture for the list; folders get a plain tile. */
    private Drawable thumbnail(File file) {
        if (file.isDirectory()) return getResources().getDrawable(R.drawable.thumb_folder, getTheme());
        String key = file.getAbsolutePath() + ':' + file.lastModified();
        Bitmap cached = thumbnails.get(key);
        if (cached == null) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 208) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            cached = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (cached == null) return null;
            if (thumbnails.size() > 200) thumbnails.clear();
            thumbnails.put(key, cached);
        }
        return new android.graphics.drawable.BitmapDrawable(getResources(), cached);
    }

    private List<File> iconPickerImages() {
        File dir = Config.dir(this);
        File pick = dir == null ? null : new File(dir, "icon-picker");
        File[] files = pick == null ? null : pick.listFiles();
        List<File> images = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isImage(f.getName())) images.add(f);
            }
        }
        images.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return images;
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    private boolean hasCustomIcon(String pkg) {
        File dir = Config.dir(this);
        File[] files = dir == null ? null : new File(dir, IconOverrides.DIR).listFiles(
                (d, name) -> name.startsWith(pkg + ".") && isImage(name));
        return files != null && files.length > 0;
    }

    /** Copies the picked picture in as icons/&lt;package&gt;.&lt;ext&gt;, replacing whatever that app had. */
    private void applyIcon(String pkg, File image) {
        File dir = Config.dir(this);
        if (dir == null) return;
        File icons = new File(dir, IconOverrides.DIR);
        if (!icons.isDirectory() && !icons.mkdirs()) return;
        String name = image.getName();
        String ext = name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
        File[] old = icons.listFiles((d, n) -> n.startsWith(pkg + ".") && isImage(n));
        if (old != null) for (File f : old) f.delete();
        try (java.io.InputStream in = new java.io.FileInputStream(image);
             java.io.OutputStream out = new java.io.FileOutputStream(new File(icons, pkg + ext))) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (Exception e) {
            Toast.makeText(this, R.string.icon_failed, Toast.LENGTH_LONG).show();
            return;
        }
        changed();
        Toast.makeText(this, R.string.icon_applied, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void restoreIcon(String pkg) {
        File dir = Config.dir(this);
        File[] files = dir == null ? null : new File(dir, IconOverrides.DIR).listFiles(
                (d, name) -> name.startsWith(pkg + ".") && isImage(name));
        if (files != null) for (File f : files) f.delete();
        changed();
        Toast.makeText(this, R.string.icon_applied, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        screensaverToggle.setText(idleMinutes > 0
                ? getString(R.string.screensaver_on_state, idleMinutes)
                : getString(R.string.screensaver_off_state));
        lunarChoice.setText(getString(R.string.lunar_state,
                getString(lunar ? R.string.on : R.string.off)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY) || choices.contains(Config.LUNAR_KEY) || choices.contains(Config.IDLE_CLOCK_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
