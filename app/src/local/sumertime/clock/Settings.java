package local.sumertime.clock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** User choices shared by the app, the wallpaper and the widget. */
public final class Settings {
    public static final int SEXAGESIMAL = 0, BERU = 1, DAY_FRACTION = 2, SHAR = 3;
    public static final int[] COLORS = {Color.WHITE, Color.rgb(20, 20, 20), Color.rgb(232, 196, 120)};
    public static final String[] COLOR_NAMES = {"Light", "Dark", "Gold"};

    public int system, color, size, position, backgroundColor;
    public boolean seconds, zeroSign, sunset, homeScreen, imageBackground;
    public long backgroundVersion;

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("clock", Context.MODE_PRIVATE);
    }
    public static Settings load(Context context) {
        SharedPreferences p = prefs(context);
        Settings s = new Settings();
        s.system = p.getInt("system", SEXAGESIMAL);
        s.seconds = p.getBoolean("seconds", true);
        s.zeroSign = p.getBoolean("zeroSign", false);
        s.sunset = p.getBoolean("sunset", false);
        s.homeScreen = p.getBoolean("homeScreen", false);
        s.color = Math.max(0, Math.min(COLORS.length - 1, p.getInt("color", 0)));
        // Size is the text height in thousandths of the screen's short side;
        // position is the clock's centre in percent of the screen height.
        s.size = p.getInt("size", 70);
        s.position = p.getInt("position", 36);
        s.imageBackground = p.getBoolean("imageBackground", false);
        s.backgroundColor = p.getInt("backgroundColor", Color.BLACK);
        s.backgroundVersion = p.getLong("backgroundVersion", 0);
        return s;
    }
    public int textColor() { return COLORS[color]; }
    public int shadowColor() { return color == 1 ? Color.argb(150, 255, 255, 255) : Color.argb(170, 0, 0, 0); }
    private Settings() {}
}
