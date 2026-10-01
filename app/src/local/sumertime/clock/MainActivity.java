package local.sumertime.clock;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View preview;
    private LinearLayout root;
    private final Runnable redraw = new Runnable() {
        @Override public void run() { preview.invalidate();handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000); }
    };
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String text, int size) {
        TextView view = new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(Color.rgb(25,25,25));return view;
    }
    private void heading(String title) {
        TextView view = text(title, 20);view.setPadding(0,dp(28),0,dp(6));root.addView(view);
    }
    private SharedPreferences prefs() { return Settings.prefs(this); }
    private void changed() {
        preview.invalidate();
        ClockWidget.updateAll(this);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Settings s = Settings.load(this);
        ScrollView scroll = new ScrollView(this);scroll.setBackgroundColor(Color.WHITE);
        root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32),dp(40),dp(32),dp(32));
        scroll.addView(root);
        root.addView(text("Sumerian Clock", 30));
        TextView description = text("Your local time, in cuneiform, on the lockscreen.", 18);
        description.setPadding(0,dp(12),0,dp(20));root.addView(description);

        // A small copy of the screen, so position and size can be judged against the real clock.
        DisplayMetrics screen = getResources().getDisplayMetrics();
        int previewWidth = dp(220), previewHeight = previewWidth * screen.heightPixels / screen.widthPixels;
        preview = new View(this) {
            @Override public void onDraw(Canvas canvas) {
                ClockRenderer.drawWallpaper(MainActivity.this, canvas, getWidth(), getHeight(), Settings.load(MainActivity.this), ClockRenderer.now(), true);
            }
        };
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14)); }
        });
        preview.setClipToOutline(true);preview.setElevation(dp(4));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(previewWidth, previewHeight);
        previewParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(preview, previewParams);

        Button apply = new Button(this);apply.setText("Set as lockscreen wallpaper");
        apply.setOnClickListener(v -> applyWallpaper());
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(-1, -2);applyParams.topMargin = dp(20);
        root.addView(apply, applyParams);
        TextView applyHelp = text("In the wallpaper preview, choose to apply it to the lock screen (or home and lock screens). The clock is drawn below Samsung's own lockscreen clock, updates every second while the lockscreen is visible, and stops when the screen is off. On the home screen only the background shows unless you turn that on below.", 15);
        applyHelp.setPadding(0,dp(8),0,0);root.addView(applyHelp);

        heading("Numeral system");
        RadioGroup systems = new RadioGroup(this);
        for (int i = 0; i < CuneiformClock.NAMES.length; i++) {
            RadioButton option = new RadioButton(this);option.setId(100 + i);
            option.setText(CuneiformClock.NAMES[i] + "\n" + CuneiformClock.DESCRIPTIONS[i]);
            option.setTextSize(15);option.setPadding(dp(8),dp(8),0,dp(8));
            systems.addView(option);
        }
        systems.check(100 + s.system);
        systems.setOnCheckedChangeListener((group, id) -> { prefs().edit().putInt("system", id - 100).apply();changed(); });
        root.addView(systems);

        heading("Options");
        root.addView(check("Show seconds (or the finest unit)", "seconds", s.seconds));
        root.addView(check("Write zero with the Late Babylonian placeholder 𒑲 instead of a blank", "zeroSign", s.zeroSign));
        root.addView(check("Begin the day at sunset (≈18:00) for bēru and day fractions, as Babylonian astronomers did", "sunset", s.sunset));
        root.addView(check("Also show the clock on the home screen wallpaper", "homeScreen", s.homeScreen));

        heading("Colour");
        RadioGroup colors = new RadioGroup(this);colors.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Settings.COLORS.length; i++) {
            RadioButton option = new RadioButton(this);option.setId(200 + i);option.setText(Settings.COLOR_NAMES[i]);
            option.setPadding(0,0,dp(20),0);colors.addView(option);
        }
        colors.check(200 + s.color);
        colors.setOnCheckedChangeListener((group, id) -> { prefs().edit().putInt("color", id - 200).apply();changed(); });
        root.addView(colors);

        heading("Size");
        root.addView(slider("size", s.size, 30, 140));
        heading("Height on screen");
        root.addView(slider("position", s.position, 10, 90));

        heading("Background");
        Button current = new Button(this);current.setText("Use current wallpaper");
        current.setOnClickListener(v -> new Thread(() -> {
            boolean ok = captureCurrentWallpaper();
            runOnUiThread(() -> Toast.makeText(this, ok ? "Copied the current wallpaper" : "Couldn't read the current wallpaper; choose an image instead", Toast.LENGTH_LONG).show());
        }).start());
        root.addView(current);
        Button pick = new Button(this);pick.setText("Choose image…");
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK_IMAGE));
        root.addView(pick);
        Button black = new Button(this);black.setText("Plain black");
        black.setOnClickListener(v -> { prefs().edit().putBoolean("imageBackground", false).putInt("backgroundColor", Color.BLACK).apply();changed(); });
        root.addView(black);

        heading("Widget");
        TextView widget = text("A transparent Sumerian Clock widget is still available for the home screen or other widget hosts. It uses the same numeral system and colour.", 15);
        root.addView(widget);
        Button start = new Button(this);start.setText("Enable widget updates");start.setOnClickListener(v -> ClockService.start(this));root.addView(start);

        Button website = new Button(this);website.setText("Original clock by Oisín Moran");
        website.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://oisinmoran.com/sumertime"))));
        LinearLayout.LayoutParams websiteParams = new LinearLayout.LayoutParams(-1, -2);websiteParams.topMargin = dp(28);
        root.addView(website, websiteParams);
        root.addView(text("Typeface: Noto Sans Cuneiform · SIL Open Font License", 13));
        setContentView(scroll);
    }
    private CheckBox check(String label, String key, boolean value) {
        CheckBox box = new CheckBox(this);box.setText(label);box.setTextSize(15);box.setChecked(value);
        box.setPadding(dp(8),dp(6),0,dp(6));
        box.setOnCheckedChangeListener((view, checked) -> { prefs().edit().putBoolean(key, checked).apply();changed(); });
        return box;
    }
    private SeekBar slider(String key, int value, int min, int max) {
        SeekBar bar = new SeekBar(this);bar.setMin(min);bar.setMax(max);bar.setProgress(value);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean user) {
                if (user) { prefs().edit().putInt(key, progress).apply();preview.invalidate(); }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return bar;
    }

    private void applyWallpaper() {
        new Thread(() -> {
            // The static wallpaper is the natural background; copy it before replacing it.
            if (!Settings.load(this).imageBackground) captureCurrentWallpaper();
            runOnUiThread(() -> {
                Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, ClockWallpaper.class));
                try { startActivity(intent); }
                catch (RuntimeException e) { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
            });
        }).start();
    }

    /**
     * Lock wallpaper first. Samsung's preloaded wallpapers are resources, and the public API
     * then returns the stock AOSP image, so read the system's copy with root before trying it.
     */
    private boolean captureCurrentWallpaper() {
        Bitmap bitmap = null;
        for (String path : new String[]{"/data/system/users/0/wallpaper_lock_images/wallpaper_lock", "/data/system/users/0/wallpaper_lock", "/data/system/users/0/wallpaper"}) {
            if (bitmap != null) break;
            bitmap = decode(rootRead(path));
        }
        WallpaperManager manager = WallpaperManager.getInstance(this);
        for (int which : new int[]{WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM}) {
            if (bitmap != null) break;
            try (ParcelFileDescriptor file = manager.getWallpaperFile(which)) {
                if (file != null) bitmap = BitmapFactory.decodeFileDescriptor(file.getFileDescriptor());
            } catch (SecurityException | IOException e) { /* choose an image instead */ }
        }
        return bitmap != null && saveBackground(bitmap);
    }
    private static byte[] rootRead(String path) {
        try {
            Process su = new ProcessBuilder("su", "-c", "cat '" + path + "'").redirectErrorStream(false).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = su.getInputStream()) {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
            }
            return su.waitFor() == 0 ? out.toByteArray() : null;
        } catch (IOException | InterruptedException e) { return null; }
    }
    private static Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2400) options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
    private boolean saveBackground(Bitmap bitmap) {
        try (FileOutputStream out = new FileOutputStream(ClockRenderer.backgroundFile(this))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false;
        } catch (IOException e) { return false; }
        prefs().edit().putBoolean("imageBackground", true).putLong("backgroundVersion", System.currentTimeMillis()).apply();
        runOnUiThread(this::changed);
        return true;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            byte[] bytes = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
                bytes = out.toByteArray();
            } catch (IOException | RuntimeException e) { /* reported below */ }
            Bitmap bitmap = decode(bytes);
            boolean ok = bitmap != null && saveBackground(bitmap);
            if (!ok) runOnUiThread(() -> Toast.makeText(this, "Couldn't open that image", Toast.LENGTH_LONG).show());
        }).start();
    }
    @Override public void onResume() { super.onResume();handler.post(redraw);if (ClockWidget.ids(this).length > 0) ClockService.start(this); }
    @Override public void onPause() { handler.removeCallbacks(redraw);super.onPause(); }
}
