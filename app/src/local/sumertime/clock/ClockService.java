package local.sumertime.clock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class ClockService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager power;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!power.isInteractive() || ClockWidget.ids(ClockService.this).length == 0) return;
            ClockWidget.updateAll(ClockService.this);
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };
    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(tick);
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
            ClockWidget.updateAll(context);
            handler.postDelayed(tick, 100);
        }
    };
    public static void start(Context context) {
        try { context.startForegroundService(new Intent(context, ClockService.class)); }
        catch (RuntimeException e) { android.util.Log.w("SumerianClock", "Open the clock app to resume updating", e); }
    }
    @Override public void onCreate() {
        super.onCreate();
        power = getSystemService(PowerManager.class);
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("clock", "Sumerian clock", NotificationManager.IMPORTANCE_MIN);
        channel.setDescription("Keeps the clock widget current while the screen is on.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "clock")
            .setSmallIcon(R.drawable.clock_icon).setContentTitle("Sumerian clock")
            .setContentText("Updates the widget; pauses when the screen is off")
            .setContentIntent(open).setOngoing(true).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET).build();
        startForeground(1, notification);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(events, filter);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        ClockWidget.updateAll(this);
        handler.removeCallbacks(tick);handler.post(tick);
        return START_STICKY;
    }
    @Override public void onDestroy() { handler.removeCallbacks(tick);unregisterReceiver(events);super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
