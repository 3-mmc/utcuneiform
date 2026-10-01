package local.sumertime.clock;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * Live wallpaper that paints the clock straight onto the lockscreen, so no widget host
 * (LockStar) is needed. It ticks only while visible and the clock is showing.
 */
public class ClockWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine() { return new ClockEngine(); }

    private class ClockEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private KeyguardManager keyguard;
        private Settings settings;
        private boolean visible;
        private int width, height;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                boolean clock = showClock();
                draw(clock);
                if (visible && clock) handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
            }
        };
        private final BroadcastReceiver events = new BroadcastReceiver() {
            // Unlocking hides the clock on the home screen; screen on/time changes redraw it at once.
            @Override public void onReceive(Context context, Intent intent) { restart(); }
        };

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(false);
            keyguard = getSystemService(KeyguardManager.class);
            settings = Settings.load(ClockWallpaper.this);
            Settings.prefs(ClockWallpaper.this).registerOnSharedPreferenceChangeListener(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(events, filter);
        }
        @Override public void onDestroy() {
            handler.removeCallbacks(tick);
            unregisterReceiver(events);
            Settings.prefs(ClockWallpaper.this).unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            settings = Settings.load(ClockWallpaper.this);
            restart();
        }
        @Override public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) restart(); else handler.removeCallbacks(tick);
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;this.height = height;
            restart();
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            handler.removeCallbacks(tick);
            super.onSurfaceDestroyed(holder);
        }
        private void restart() {
            handler.removeCallbacks(tick);
            if (width > 0) handler.post(tick);
        }
        private boolean showClock() { return isPreview() || settings.homeScreen || keyguard.isKeyguardLocked(); }
        private void draw(boolean clock) {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas != null) ClockRenderer.drawWallpaper(ClockWallpaper.this, canvas, width, height, settings, ClockRenderer.now(), clock);
            } catch (IllegalStateException | IllegalArgumentException e) {
                android.util.Log.w("SumerianClock", "Wallpaper surface unavailable", e);
                canvas = null;
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
