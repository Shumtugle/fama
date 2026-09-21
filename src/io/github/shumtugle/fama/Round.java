package io.github.shumtugle.fama;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/**
 * Shape as a short scale, not as numbers chosen on the spot.
 *
 * Seven steps, from square to a full capsule. A small thing that is touched
 * gets a small radius, a large surface a large one, and anything that is a
 * button is a capsule whatever its size. A corner that is not on the scale
 * is a corner someone forgot to think about.
 *
 * Press feedback lives here as well, because it is part of the shape: it is
 * clipped to the thing that was pressed and drawn in the ink that thing
 * already wears, thinned, so a press is seen rather than shouted.
 */
public final class Round {

    public static final float NONE = 0f;
    public static final float XS = 4f;
    public static final float S = 8f;
    public static final float M = 12f;
    public static final float L = 16f;
    public static final float XL = 28f;
    /** Larger than anything is tall: the drawing clamps it to half the height. */
    public static final float FULL = 999f;

    /** How much of the ink a press shows. */
    private static final float PRESS = 0.12f;

    private static float density = 1f;

    private Round() {
    }

    public static void measure(Context context) {
        density = context.getResources().getDisplayMetrics().density;
    }

    public static int dp(float value) {
        return Math.round(density * value);
    }

    public static float px(float value) {
        return density * value;
    }

    /** A container: one fill, one step of the scale. */
    public static GradientDrawable box(int fill, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(fill);
        shape.setCornerRadius(px(radius));
        return shape;
    }

    /** The same container drawn as an outline, for what is quieter than a fill. */
    public static GradientDrawable ring(int fill, float radius, int edge) {
        GradientDrawable shape = box(fill, radius);
        shape.setStroke(Math.max(1, dp(1f)), edge);
        return shape;
    }

    /** A sheet rising from the bottom edge keeps its lower corners square. */
    public static GradientDrawable sheet(int fill, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(fill);
        float r = px(radius);
        shape.setCornerRadii(new float[] {r, r, r, r, 0f, 0f, 0f, 0f});
        return shape;
    }

    /**
     * A press over what the control already wears, in its own ink. A control
     * with no fill of its own gets a mask of the given step, so the press
     * still has the control's shape and not a square.
     */
    public static RippleDrawable touch(Drawable under, int ink, float radius) {
        GradientDrawable mask = null;
        if (under == null) {
            mask = box(0xFFFFFFFF, radius);
        }
        int wash = (Math.round(255f * PRESS) << 24) | (ink & 0x00FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(wash), under, mask);
    }

    /** A round press for the controls that draw themselves as discs. */
    public static RippleDrawable disc(int ink) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        int wash = (Math.round(255f * PRESS) << 24) | (ink & 0x00FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(wash), null, mask);
    }
}
