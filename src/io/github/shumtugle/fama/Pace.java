package io.github.shumtugle.fama;

import android.view.animation.PathInterpolator;

/**
 * Movement as a handful of times and three curves.
 *
 * A block arrives in four hundred and sixty milliseconds on a curve that
 * starts fast and settles slowly, and the blocks of one screen come one
 * after another, sixty apart, so the eye is led down the page instead of
 * being handed all of it at once. What leaves goes in two hundred, slow to
 * start and gone at speed, the mirror of an arrival. A press answers in a
 * hundred and forty.
 *
 * Nothing moves faster than a press or slower than an arrival. Anything
 * quicker is not seen; anything slower is waited for.
 */
public final class Pace {

    public static final long PRESS = 140L;
    public static final long SHEET = 170L;
    public static final long LEAVE = 200L;
    public static final long GROW = 420L;
    public static final long ARRIVE = 460L;
    public static final long STAGGER = 60L;

    /** Fast out of the gate, a long quiet landing: every arrival. */
    public static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** The mirror of an arrival: slow to start, gone at speed. */
    public static final PathInterpolator AWAY = new PathInterpolator(0.3f, 0f, 1f, 1f);
    /** For what should be felt as well as seen: most of the way at once, then a long settle. */
    public static final PathInterpolator EMPHASIS = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private Pace() {
    }
}
