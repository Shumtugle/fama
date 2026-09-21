package io.github.shumtugle.fama;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings that belong to the owner of the phone: the seed of the colours,
 * whether it follows the wallpaper, and when the channels were last read.
 */
public final class Keep {

    private Keep() {
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("keep", Context.MODE_PRIVATE);
    }

    /** Hue in degrees and richness from nought to one. */
    public static float[] look(Context context) {
        SharedPreferences kept = store(context);
        return new float[] {kept.getFloat("hue", Tone.HUE), kept.getFloat("rich", Tone.RICH)};
    }

    /**
     * Whether the seed follows the wallpaper. It does not until asked: the
     * board has a colour of its own, the one on its icon.
     */
    public static boolean wall(Context context) {
        return store(context).getBoolean("wall", false);
    }

    public static void saveLook(Context context, float hue, float rich, boolean wall) {
        store(context).edit().putFloat("hue", hue).putFloat("rich", rich)
            .putBoolean("wall", wall).apply();
    }

    /** The reader's own layer over the lexicon, as its lines. */
    public static String words(Context context) {
        return store(context).getString("words", "");
    }

    public static void saveWords(Context context, String layer) {
        store(context).edit().putString("words", layer).apply();
    }

    /** What the words found at a channel's last reading. */
    public static void saveTally(Context context, String channel, String tally) {
        context.getSharedPreferences("tally", Context.MODE_PRIVATE).edit()
            .putString(channel.toLowerCase(java.util.Locale.ROOT), tally).apply();
    }

    /** What the words found at the last reading of each of these channels, together. */
    public static Sift.Tally tally(Context context, java.util.List<String> channels) {
        SharedPreferences kept = context.getSharedPreferences("tally", Context.MODE_PRIVATE);
        Sift.Tally all = new Sift.Tally();
        for (int i = 0; i < channels.size(); i++) {
            all.read(kept.getString(channels.get(i).toLowerCase(java.util.Locale.ROOT), ""));
        }
        return all;
    }

    /** When every channel was last read through, or nought. */
    public static long readAt(Context context) {
        return store(context).getLong("readAt", 0L);
    }

    public static void saveReadAt(Context context, long when) {
        store(context).edit().putLong("readAt", when).apply();
    }
}
