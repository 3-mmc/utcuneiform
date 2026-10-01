package local.sumertime.clock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Locale;

public class ClockWidget extends AppWidgetProvider {
    private static final android.os.Handler resizeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Runnable resized = new Runnable() {
        @Override public void run() { if (resizeContext != null) updateAll(resizeContext); }
    };
    private static Context resizeContext;
    public static int[] ids(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, ClockWidget.class));
    }
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Calendar time = ClockRenderer.now();
        float density = context.getResources().getDisplayMetrics().density;
        for (int id : ids(context)) {
            Bundle options = manager.getAppWidgetOptions(id);
            int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 600);
            int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 128);
            width = Math.max(220, Math.min(1000, width));
            height = Math.max(72, Math.min(240, height));
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clock_widget);
            views.setViewVisibility(R.id.preview_text, View.GONE);
            views.setImageViewBitmap(R.id.clock_image, ClockRenderer.widget(context,
                Math.min(1600, (int)(width * density)), Math.min(480, (int)(height * density)), time));
            views.setContentDescription(R.id.clock_image, String.format(Locale.ROOT,
                "Sumerian time %02d:%02d:%02d", time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), time.get(Calendar.SECOND)));
            PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.clock_image, open);
            manager.updateAppWidget(id, views);
        }
    }
    @Override public void onEnabled(Context context) { ClockService.start(context); }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
        ClockService.start(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        // Updating RemoteViews during a host's resize (e.g. LockStar) replaces the view
        // being dragged. Render once resizing settles, preserving the active gesture.
        resizeContext = context.getApplicationContext();
        resizeHandler.removeCallbacks(resized);
        resizeHandler.postDelayed(resized, 1200);
    }
    @Override public void onDisabled(Context context) { context.stopService(new Intent(context, ClockService.class)); }
}
