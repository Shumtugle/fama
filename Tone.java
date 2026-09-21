package io.github.shumtugle.fama;

import android.content.Context;
import android.os.Build;

/**
 * Colour as roles, grown from one seed.
 *
 * A seed is a hue and a richness. Five palettes come out of it: the seed's
 * own; a quieter one of the same hue; a neighbour a sixth of the circle
 * away; and two near-greys that carry a trace of the seed so the ground
 * never looks like grey furniture around a coloured button. Every role in
 * the application is one tone of one of those palettes, and nothing else.
 *
 * A tone here is lightness as the eye measures it, from black at zero to
 * white at a hundred. Two colours of equal tone read as equally light
 * whatever their hue, so the contrast between a role and the ink on it is
 * decided by tone alone and holds for every seed. When the screen cannot
 * show a colour, richness is given up, never tone and never hue.
 *
 * There is one scheme, the dark one. Depth is told by neighbouring tones of
 * the ground rather than by shadows.
 */
public final class Tone {

    public static final int PRIMARY = 0;
    public static final int ON_PRIMARY = 1;
    public static final int PRIMARY_CONTAINER = 2;
    public static final int ON_PRIMARY_CONTAINER = 3;
    public static final int SECONDARY = 4;
    public static final int ON_SECONDARY = 5;
    public static final int SECONDARY_CONTAINER = 6;
    public static final int ON_SECONDARY_CONTAINER = 7;
    public static final int TERTIARY = 8;
    public static final int ON_TERTIARY = 9;
    public static final int TERTIARY_CONTAINER = 10;
    public static final int ON_TERTIARY_CONTAINER = 11;
    public static final int SURFACE = 12;
    public static final int SURFACE_LOWEST = 13;
    public static final int SURFACE_LOW = 14;
    public static final int SURFACE_CONTAINER = 15;
    public static final int SURFACE_HIGH = 16;
    public static final int SURFACE_HIGHEST = 17;
    public static final int SURFACE_BRIGHT = 18;
    public static final int ON_SURFACE = 19;
    public static final int ON_SURFACE_VARIANT = 20;
    public static final int OUTLINE = 21;
    public static final int OUTLINE_VARIANT = 22;
    public static final int INVERSE_SURFACE = 23;
    public static final int INVERSE_ON_SURFACE = 24;
    public static final int INVERSE_PRIMARY = 25;
    private static final int ROLES = 26;

    /** The seed when nothing else is known: the red of Pompeian walls, the ground of the mark. */
    public static final float HUE = 36f;
    public static final float RICH = 0.62f;

    private static final int[] roles = new int[ROLES];
    private static float hue = HUE;
    private static float rich = RICH;

    private Tone() {
    }

    // ------------------------------------------------------------------ seed

    /**
     * Reads the seed and grows every role from it. From Android 12 the seed
     * can follow the wallpaper: the platform keeps the colour it drew from
     * it, and it is read here as an ordinary resource, then taken apart
     * into a hue and a richness like any seed chosen by hand.
     */
    public static void read(Context context) {
        float[] look = Keep.look(context);
        hue = look[0];
        rich = look[1];
        if (Keep.wall(context)) {
            float[] seed = wallpaper(context);
            if (seed != null) {
                hue = seed[0];
                rich = seed[1];
            }
        }
        grow();
    }

    /** The seed the wallpaper gives, as hue and richness, or nothing before Android 12. */
    public static float[] wallpaper(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return null;
        }
        int colour = context.getColor(android.R.color.system_accent1_500);
        double[] lab = lab(colour);
        double chroma = Math.hypot(lab[1], lab[2]);
        double angle = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        if (angle < 0) {
            angle += 360;
        }
        float richness = (float) ((chroma - 12.0) / 60.0);
        return new float[] {(float) angle, Math.max(0f, Math.min(1f, richness))};
    }

    public static float hue() {
        return hue;
    }

    public static float rich() {
        return rich;
    }

    private static double chroma(float richness) {
        return 12.0 + 60.0 * richness;
    }

    private static void grow() {
        double c = chroma(rich);
        double quiet = c * 0.34;
        double near = c * 0.62;
        double grey = 2.0 + 4.0 * rich;
        double greyer = 4.0 + 9.0 * rich;
        float beside = (hue + 60f) % 360f;

        roles[PRIMARY] = colour(80, c, hue);
        roles[ON_PRIMARY] = colour(20, c, hue);
        roles[PRIMARY_CONTAINER] = colour(30, c, hue);
        roles[ON_PRIMARY_CONTAINER] = colour(90, c, hue);

        roles[SECONDARY] = colour(80, quiet, hue);
        roles[ON_SECONDARY] = colour(20, quiet, hue);
        roles[SECONDARY_CONTAINER] = colour(30, quiet, hue);
        roles[ON_SECONDARY_CONTAINER] = colour(90, quiet, hue);

        roles[TERTIARY] = colour(80, near, beside);
        roles[ON_TERTIARY] = colour(20, near, beside);
        roles[TERTIARY_CONTAINER] = colour(30, near, beside);
        roles[ON_TERTIARY_CONTAINER] = colour(90, near, beside);

        roles[SURFACE] = colour(6, grey, hue);
        roles[SURFACE_LOWEST] = colour(4, grey, hue);
        roles[SURFACE_LOW] = colour(10, grey, hue);
        roles[SURFACE_CONTAINER] = colour(12, grey, hue);
        roles[SURFACE_HIGH] = colour(17, grey, hue);
        roles[SURFACE_HIGHEST] = colour(22, grey, hue);
        roles[SURFACE_BRIGHT] = colour(24, grey, hue);
        roles[ON_SURFACE] = colour(90, grey, hue);
        roles[ON_SURFACE_VARIANT] = colour(80, greyer, hue);
        roles[OUTLINE] = colour(60, greyer, hue);
        roles[OUTLINE_VARIANT] = colour(30, greyer, hue);
        roles[INVERSE_SURFACE] = colour(90, grey, hue);
        roles[INVERSE_ON_SURFACE] = colour(20, grey, hue);
        roles[INVERSE_PRIMARY] = colour(40, c, hue);
    }

    /** The colour of a role, opaque. */
    public static int of(int role) {
        return roles[role];
    }

    /** The colour of a role with some of the ground showing through. */
    public static int of(int role, float alpha) {
        int a = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (roles[role] & 0x00FFFFFF);
    }

    /** A colour on the seed's own hue at a tone, for tracks and previews. */
    public static int at(float tone, double chroma, float hueAngle) {
        return colour(tone, chroma, hueAngle);
    }

    /** The chroma the current richness gives the seed's palette. */
    public static double seedChroma() {
        return chroma(rich);
    }

    /** The chroma a richness would give, for a track that shows the way there. */
    public static double chromaOf(float richness) {
        return chroma(richness);
    }

    // ------------------------------------------------------------ the maths

    private static final double XN = 0.95047;
    private static final double YN = 1.0;
    private static final double ZN = 1.08883;
    private static final double EPSILON = 216.0 / 24389.0;
    private static final double KAPPA = 24389.0 / 27.0;

    /**
     * The colour at a tone, a chroma and a hue. Chroma the screen cannot
     * show is given up by halving the difference until the colour fits, so
     * the answer is the richest colour of exactly that tone and hue.
     */
    private static int colour(double tone, double chroma, double angle) {
        double radians = Math.toRadians(angle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double[] rgb = linear(tone, chroma * cos, chroma * sin);
        if (!fits(rgb)) {
            double low = 0.0;
            double high = chroma;
            for (int i = 0; i < 18; i++) {
                double middle = (low + high) / 2.0;
                if (fits(linear(tone, middle * cos, middle * sin))) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            rgb = linear(tone, low * cos, low * sin);
        }
        return pack(rgb);
    }

    /** Lightness and two opponent axes, to light as the screen mixes it. */
    private static double[] linear(double l, double a, double b) {
        double fy = (l + 16.0) / 116.0;
        double fx = fy + a / 500.0;
        double fz = fy - b / 200.0;
        double x = XN * unfold(fx);
        double y = YN * (l > KAPPA * EPSILON ? fy * fy * fy : l / KAPPA);
        double z = ZN * unfold(fz);
        return new double[] {
            3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
            -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
            0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
        };
    }

    private static double unfold(double f) {
        double cube = f * f * f;
        return cube > EPSILON ? cube : (116.0 * f - 16.0) / KAPPA;
    }

    private static boolean fits(double[] rgb) {
        for (int i = 0; i < 3; i++) {
            if (rgb[i] < -0.0001 || rgb[i] > 1.0001) {
                return false;
            }
        }
        return true;
    }

    private static int pack(double[] rgb) {
        int packed = 0xFF000000;
        for (int i = 0; i < 3; i++) {
            double v = Math.max(0.0, Math.min(1.0, rgb[i]));
            v = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
            packed |= ((int) Math.round(v * 255.0) & 0xFF) << (16 - 8 * i);
        }
        return packed;
    }

    /** The way back: a screen colour taken apart into lightness and the two axes. */
    private static double[] lab(int colour) {
        double r = open(((colour >> 16) & 0xFF) / 255.0);
        double g = open(((colour >> 8) & 0xFF) / 255.0);
        double b = open((colour & 0xFF) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN;
        double y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN;
        double fx = fold(x);
        double fy = fold(y);
        double fz = fold(z);
        return new double[] {116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    private static double open(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double fold(double t) {
        return t > EPSILON ? Math.cbrt(t) : (KAPPA * t + 16.0) / 116.0;
    }
}
