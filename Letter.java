package io.github.shumtugle.fama;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Type as a ladder of fifteen rungs.
 *
 * Five kinds of words, three sizes each: display for the name of a room,
 * headline for the name of a screen, title for the name of a thing, body
 * for what is said about it, label for what is pressed. Each rung fixes a
 * size, the height of a line, a weight and the air between letters, so a
 * word set on a rung looks the same wherever it stands.
 *
 * The two upper kinds are set in the platform's serif. A board of meetings
 * is a printed thing, a bill pasted on a wall, and it names its days in a
 * book face; everything a finger touches stays in the plain sans, where it
 * is read fastest.
 */
public final class Letter {

    public static final int DISPLAY_L = 0;
    public static final int DISPLAY_M = 1;
    public static final int DISPLAY_S = 2;
    public static final int HEADLINE_L = 3;
    public static final int HEADLINE_M = 4;
    public static final int HEADLINE_S = 5;
    public static final int TITLE_L = 6;
    public static final int TITLE_M = 7;
    public static final int TITLE_S = 8;
    public static final int BODY_L = 9;
    public static final int BODY_M = 10;
    public static final int BODY_S = 11;
    public static final int LABEL_L = 12;
    public static final int LABEL_M = 13;
    public static final int LABEL_S = 14;

    /** Size, line height, weight, tracking; sizes in scaled pixels. */
    private static final float[][] RUNGS = {
        {57f, 64f, 400f, -0.25f},
        {45f, 52f, 400f, 0f},
        {36f, 44f, 400f, 0f},
        {32f, 40f, 400f, 0f},
        {28f, 36f, 400f, 0f},
        {24f, 32f, 400f, 0f},
        {22f, 28f, 400f, 0f},
        {16f, 24f, 500f, 0.15f},
        {14f, 20f, 500f, 0.1f},
        {16f, 24f, 400f, 0.5f},
        {14f, 20f, 400f, 0.25f},
        {12f, 16f, 400f, 0.4f},
        {14f, 20f, 500f, 0.1f},
        {12f, 16f, 500f, 0.5f},
        {11f, 16f, 500f, 0.5f},
    };

    private static Typeface serif;
    private static Typeface plain;
    private static Typeface medium;

    private Letter() {
    }

    private static Typeface face(int rung) {
        if (serif == null) {
            serif = Typeface.create("serif", Typeface.NORMAL);
            plain = Typeface.create("sans-serif", Typeface.NORMAL);
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
        if (rung <= HEADLINE_S) {
            return serif;
        }
        return RUNGS[rung][2] >= 500f ? medium : plain;
    }

    /** Sets the words of a view on a rung, and gives the view back. */
    public static <T extends TextView> T set(T view, int rung) {
        float[] r = RUNGS[rung];
        view.setIncludeFontPadding(false);
        view.setTypeface(face(rung));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, r[0]);
        view.setLetterSpacing(r[3] / r[0]);
        float scaled = view.getResources().getDisplayMetrics().scaledDensity;
        int line = Math.round(r[1] * scaled);
        if (Build.VERSION.SDK_INT >= 28) {
            view.setLineHeight(line);
        } else {
            Paint.FontMetricsInt metrics = view.getPaint().getFontMetricsInt();
            int natural = metrics.descent - metrics.ascent;
            view.setLineSpacing(Math.max(0, line - natural), 1f);
        }
        return view;
    }

    /** Words on a lower rung set in the book face all the same: hours, the masthead, titles of books. */
    public static <T extends TextView> T serif(T view) {
        face(DISPLAY_L);
        view.setTypeface(serif);
        return view;
    }

    /** The size of a rung in scaled pixels, for what draws its own words. */
    public static float size(int rung) {
        return RUNGS[rung][0];
    }

    /**
     * A name that comes from outside set on the highest rung it fits on.
     *
     * A room's own name is written here and always fits; a folder's name is
     * written by whoever made the folder, in whatever writing and at
     * whatever length, and a rung nailed down pays for its steadiness with
     * a word cut through the middle. So the rung gives way instead: the
     * name is measured against the width the view was given, and takes the
     * first rung of the ladder on which it stands in one line. It is a
     * ladder and not a slope — three sizes the type already has, not a
     * size computed for this one name — so a plate still looks like the
     * other plates. Below the last rung the name takes two lines and ends
     * in an ellipsis rather than pushing what is under it off the screen.
     *
     * The line it turns on is the name's own: it is aligned by the writing
     * it is in and not by the writing of the application, so a name that
     * runs the other way stands at the other edge.
     *
     * Measured again at every change of width, set only when the rung
     * changes — setting asks for a new layout, and a rung that answered
     * would ask again.
     */
    public static void fit(final TextView view, final int[] ladder, final boolean book) {
        view.setMaxLines(2);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setTextAlignment(android.view.View.TEXT_ALIGNMENT_TEXT_START);
        view.addOnLayoutChangeListener(new android.view.View.OnLayoutChangeListener() {
            private int at = -1;
            private int had = -1;
            private CharSequence said;

            public void onLayoutChange(android.view.View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                int room = r - l - view.getPaddingLeft() - view.getPaddingRight();
                CharSequence text = view.getText();
                if (room <= 0 || text == null || text.length() == 0) {
                    return;
                }
                if (at >= 0 && room == had && text.equals(said)) {
                    return;
                }
                had = room;
                int want = ladder[ladder.length - 1];
                for (int i = 0; i < ladder.length; i++) {
                    if (width(view, ladder[i], book, text) <= room) {
                        want = ladder[i];
                        break;
                    }
                }
                said = text;
                if (want == at) {
                    return;
                }
                at = want;
                set(view, want);
                if (book) {
                    serif(view);
                }
            }
        });
    }

    /** What the words would take on a rung, without setting the view to it. */
    private static float width(TextView view, int rung, boolean book, CharSequence text) {
        float[] r = RUNGS[rung];
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        Typeface chosen = face(rung);
        paint.setTypeface(book ? serif : chosen);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, r[0],
            view.getResources().getDisplayMetrics()));
        paint.setLetterSpacing(r[3] / r[0]);
        return paint.measureText(text, 0, text.length());
    }
}
