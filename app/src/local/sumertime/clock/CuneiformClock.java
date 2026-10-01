package local.sumertime.clock;

import java.util.Calendar;

/** Local time written in several cuneiform numeral systems. */
public final class CuneiformClock {
    // Babylonian place-value digits: GEŠ2 units and U (Winkelhaken) tens.
    private static final String[] UNITS = {"", "𒐕", "𒐖", "𒐗", "𒐘", "𒐙", "𒐚", "𒐛", "𒐜", "𒐝"};
    // Match the original page's Android substitution: twenty is two tens.
    private static final String[] TENS = {"", "𒌋", "𒌋𒌋", "𒌍", "𒐏", "𒐐"};
    // Sumerian absolute (non place-value) system: each power has its own sign.
    private static final String[] DISH = {"", "𒁹", "𒈫", "𒐈", "𒐉", "𒐊", "𒐋", "𒐌", "𒐍", "𒐎"};
    private static final String[] GESH2 = UNITS;
    private static final String[] GESHU = {"", "𒐞", "𒐟", "𒐠", "𒐡", "𒐢"};
    private static final String[] SHAR2 = {"", "𒊹", "𒐣", "𒐤", "𒐦", "𒐧", "𒐨", "𒐩", "𒐪", "𒐫"};
    private static final String[] SHARU = {"", "𒐬", "𒐭", "𒐮", "𒐰", "𒐱"};
    /** Late Babylonian placeholder: two slanted wedges. */
    public static final String ZERO = "𒑲";
    private static final String BLANK = "  ";

    public static final String[] NAMES = {
        "Sexagesimal hours · minutes · seconds",
        "Bēru · UŠ · NINDA",
        "Fraction of the day",
        "Sumerian ŠAR count"};
    public static final String[] DESCRIPTIONS = {
        "Modern 24-hour time in Babylonian base-60 digits, as on oisinmoran.com/sumertime.",
        "Babylonian astronomical time: 12 bēru (double hours) a day, 30 UŠ (4 minutes) a bēru, 60 NINDA (4 seconds) an UŠ.",
        "How much of the day has passed, as sexagesimal fractions: 1/60 day = 24 minutes, 1/3600 = 24 seconds.",
        "Seconds since midnight (or minutes, with seconds off) in the older additive system, with separate signs for 1, 10, 60, 600, 3600 and 36000. It was used for counting rather than for telling time."};

    /** One reading of the clock: numeral groups and the separators drawn between them. */
    public static final class Reading {
        public final String[] fields, separators;
        /** Fields that change faster than once a minute get a fixed-width slot. */
        public final boolean[] reserved;
        /** Fixed-width fields hold Sumerian DIŠ/U numbers rather than place-value digits. */
        public final boolean absolute;
        Reading(String[] fields, String[] separators, boolean[] reserved, boolean absolute) {
            this.fields = fields;this.separators = separators;this.reserved = reserved;this.absolute = absolute;
        }
    }

    public static String numeral(int number, boolean zeroSign) {
        if (number < 0 || number > 59) throw new IllegalArgumentException("Outside base-60 digit");
        if (number == 0) return zeroSign ? ZERO : BLANK;
        return TENS[number / 10] + UNITS[number % 10];
    }
    /** A number below sixty in the absolute system: U tens and DIŠ units. */
    public static String small(int number) { return TENS[number / 10] + DISH[number % 10]; }

    public static Reading read(Settings s, Calendar time) {
        int h = time.get(Calendar.HOUR_OF_DAY), m = time.get(Calendar.MINUTE), sec = time.get(Calendar.SECOND);
        long ms = ((h * 60L + m) * 60 + sec) * 1000 + time.get(Calendar.MILLISECOND);
        // Babylonian astronomers began the day at sunset; approximate it as 18:00.
        long dayMs = 86400000L, babylonian = s.sunset ? (ms + 6 * 3600000L) % dayMs : ms;
        boolean z = s.zeroSign;
        switch (s.system) {
            case Settings.BERU: {
                long t = babylonian / 1000;
                String[] f = {numeral((int)(t / 7200), z), numeral((int)(t % 7200 / 240), z), numeral((int)(t % 240 / 4), z)};
                return trim(f, new String[]{" ", " "}, new boolean[]{false, false, true}, false, s.seconds);
            }
            case Settings.DAY_FRACTION: {
                long third = babylonian * 216000 / dayMs; // in units of 1/216000 day (0.4 s)
                String[] f = {numeral((int)(third / 3600), z), numeral((int)(third / 60 % 60), z), numeral((int)(third % 60), z)};
                return trim(f, new String[]{" ", " "}, new boolean[]{false, true, true}, false, s.seconds);
            }
            case Settings.SHAR: {
                int n = s.seconds ? (h * 60 + m) * 60 + sec : h * 60 + m;
                String big = SHARU[n / 36000] + SHAR2[n / 3600 % 10] + GESHU[n / 600 % 6] + GESH2[n / 60 % 10];
                return new Reading(new String[]{big, small(n % 60)}, new String[]{""}, new boolean[]{false, true}, true);
            }
            default: {
                String[] f = {numeral(h, z), numeral(m, z), numeral(sec, z)};
                return trim(f, new String[]{":", ":"}, new boolean[]{false, false, true}, false, s.seconds);
            }
        }
    }
    private static Reading trim(String[] f, String[] sep, boolean[] reserved, boolean absolute, boolean seconds) {
        if (seconds) return new Reading(f, sep, reserved, absolute);
        // Without the fastest field, keep fixed slots only where something still ticks within the minute.
        boolean[] r = new boolean[f.length - 1];
        for (int i = 0; i < r.length; i++) r[i] = reserved[i];
        String[] g = new String[f.length - 1], s = new String[sep.length - 1];
        System.arraycopy(f, 0, g, 0, g.length);System.arraycopy(sep, 0, s, 0, s.length);
        return new Reading(g, s, r, absolute);
    }
    private CuneiformClock() {}
}
