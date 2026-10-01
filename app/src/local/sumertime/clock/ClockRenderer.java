package local.sumertime.clock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.io.File;
import java.util.Calendar;

public final class ClockRenderer {
    private static Typeface font;
    private static Bitmap background;
    private static long backgroundVersion = -1;
    // Tall and wide signs, so every reading centres on the same baseline.
    private static final String REFERENCE = "𒐝𒐐𒐫";

    public static Calendar now() { return Calendar.getInstance(); }

    /**
     * Draw the clock centred on (cx, cy). Slow fields use their natural width; fast fields get
     * a fixed, left-aligned slot sized for their widest value. Nothing to the left of the
     * ticking field moves, and the group never gets the wide gaps of right-aligned slots.
     */
    public static void drawClock(Context context, Canvas canvas, float cx, float cy, float em, float maxWidth, Settings s, Calendar time) {
        if (font == null) font = Typeface.createFromAsset(context.getAssets(), "cuneiform.ttf");
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(font);
        paint.setColor(s.textColor());
        paint.setTextSize(em);
        CuneiformClock.Reading r = CuneiformClock.read(s, time);
        float slot = 0;
        for (int n = 0; n < 60; n++)
            slot = Math.max(slot, paint.measureText(r.absolute ? CuneiformClock.small(n) : CuneiformClock.numeral(n, s.zeroSign)));
        // Shrink to fit using the widest possible reading, so the size never pulses.
        float worst = 0;
        // The first field of the ŠAR count can hold several signs at once.
        for (int i = 0; i < r.fields.length; i++) worst += r.reserved[i] || !r.absolute ? slot : paint.measureText("𒐭𒐫𒐢𒐝");
        for (String sep : r.separators) worst += paint.measureText(sep);
        if (worst > maxWidth) {
            float scale = maxWidth / worst;
            em *= scale;slot *= scale;
            paint.setTextSize(em);
        }
        paint.setShadowLayer(Math.max(1, em * 0.06f), 0, em * 0.02f, s.shadowColor());
        float total = 0;
        float[] widths = new float[r.fields.length];
        for (int i = 0; i < widths.length; i++) total += widths[i] = r.reserved[i] ? slot : paint.measureText(r.fields[i]);
        for (String sep : r.separators) total += paint.measureText(sep);
        Rect bounds = new Rect();
        paint.getTextBounds(REFERENCE, 0, REFERENCE.length(), bounds);
        float y = cy - (bounds.top + bounds.bottom) * 0.5f;
        float x = cx - total * 0.5f;
        for (int i = 0; i < r.fields.length; i++) {
            canvas.drawText(r.fields[i], x, y, paint);
            x += widths[i];
            if (i < r.separators.length) {
                canvas.drawText(r.separators[i], x, y, paint);
                x += paint.measureText(r.separators[i]);
            }
        }
    }
    /** Full-screen wallpaper: background, then the clock wherever the user placed it. */
    public static void drawWallpaper(Context context, Canvas canvas, int width, int height, Settings s, Calendar time, boolean showClock) {
        Bitmap image = s.imageBackground ? background(context, s) : null;
        if (image == null) canvas.drawColor(s.backgroundColor);
        else {
            canvas.drawColor(Color.BLACK);
            float scale = Math.max((float)width / image.getWidth(), (float)height / image.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((width - image.getWidth() * scale) * 0.5f, (height - image.getHeight() * scale) * 0.5f);
            canvas.drawBitmap(image, matrix, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        }
        if (!showClock) return;
        float em = Math.min(width, height) * s.size / 1000f;
        drawClock(context, canvas, width * 0.5f, height * s.position / 100f, em, width * 0.92f, s, time);
    }

    public static File backgroundFile(Context context) { return new File(context.getFilesDir(), "background.png"); }
    private static synchronized Bitmap background(Context context, Settings s) {
        if (backgroundVersion != s.backgroundVersion) {
            backgroundVersion = s.backgroundVersion;
            File file = backgroundFile(context);
            background = file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
        }
        return background;
    }

    /** Transparent widget image: just the numerals, centred in the widget's own bounds. */
    public static Bitmap widget(Context context, int width, int height, Calendar time) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float density = context.getResources().getDisplayMetrics().density;
        drawClock(context, new Canvas(bitmap), width * 0.5f, height * 0.5f,
            Math.min(72 * density, height * 0.57f), width - 16 * density, Settings.load(context), time);
        return bitmap;
    }
    private ClockRenderer() {}
}
