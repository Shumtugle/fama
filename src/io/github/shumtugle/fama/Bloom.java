package io.github.shumtugle.fama;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * What can be done with the thing under the finger, grown out of the finger.
 *
 * Held, a thing opens this where it was held: a card of rows that grows from
 * that point as a leaf from its bud, to whichever side there is room, never
 * past an edge and never over the finger. The rows stand in groups with a
 * small gap between them — what is about this place, what is about the
 * whole, and apart, what cannot be undone, in the colour of an error — so
 * that seven rows still read at a glance.
 *
 * The finger that held need not lift: drawn onto a row it lights it, with a
 * tick for each, and let go there it does it. Let go without having moved,
 * the card stays for a touch; moved and let go on nothing, it goes. The row
 * done flashes, and the card folds back into the point it came from.
 */
final class Bloom extends FrameLayout {

    /** One row: its mark, its words, a quiet word at its end, and what it does. */
    static final class Row {
        final int mark;
        String words;
        final String end;
        final boolean danger;
        final Act act;

        Row(int mark, String words, String end, boolean danger, Act act) {
            this.mark = mark;
            this.words = words;
            this.end = end;
            this.danger = danger;
            this.act = act;
        }
    }

    /** What a row does; answers whether the card may go, or should stay for another touch. */
    interface Act {
        boolean act(Row row, Bloom bloom);
    }

    interface Gone {
        void gone();
    }

    private final LinearLayout card;
    private final ScrollView holder;
    /** The room behind, dimmed while the card stands: what is held is the one thing lit. */
    private final View scrim;
    private final ArrayList<View> rowViews = new ArrayList<View>();
    private final ArrayList<Row> rows = new ArrayList<Row>();
    private final float anchorX;
    private final float anchorY;
    private final Gone gone;
    private int lit = -1;
    private boolean moved;
    private boolean leaving;
    private float downX;
    private float downY;
    private final int slop;

    Bloom(Context c, float x, float y, ArrayList<ArrayList<Row>> groups, Gone gone) {
        super(c);
        this.anchorX = x;
        this.anchorY = y;
        this.gone = gone;
        this.slop = Round.dp(10);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });
        scrim = new View(c);
        scrim.setBackgroundColor(SCRIM);
        scrim.setAlpha(0f);
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setElevation(Round.px(8f));
        int error = error();
        for (int g = 0; g < groups.size(); g++) {
            ArrayList<Row> group = groups.get(g);
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(0, Round.dp(4), 0, Round.dp(4));
            box.setBackground(shape(g == 0, g == groups.size() - 1));
            box.setClipToOutline(true);
            for (int i = 0; i < group.size(); i++) {
                final Row row = group.get(i);
                final int index = rows.size();
                View made = rowView(c, row, row.danger ? error : Tone.of(Tone.ON_SURFACE));
                made.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        choose(index);
                    }
                });
                box.addView(made);
                rows.add(row);
                rowViews.add(made);
            }
            LinearLayout.LayoutParams boxAt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (g > 0) {
                boxAt.topMargin = Round.dp(3);
            }
            card.addView(box, boxAt);
        }
        holder = new ScrollView(c);
        holder.setVerticalScrollBarEnabled(false);
        holder.setOverScrollMode(OVER_SCROLL_NEVER);
        holder.setClipToPadding(false);
        holder.addView(card);
        addView(holder, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.setAlpha(0f);
    }

    /**
     * How far the room darkens behind the card. On a dark ground a shadow
     * cannot be seen, so the card is lifted by tone and by the room going
     * quieter around it, and the gaps between its groups show the quiet
     * room rather than words cut in half.
     */
    private static final int SCRIM = 0x73000000;

    /** The error's colour, for whatever else must say that something is off. */
    static int errorInk() {
        return error();
    }

    /** The error's colour for the scheme of the moment, dark or light. */
    private static int error() {
        int ground = Tone.of(Tone.SURFACE);
        float light = (Color.red(ground) * 0.299f + Color.green(ground) * 0.587f
            + Color.blue(ground) * 0.114f) / 255f;
        return light < 0.5f ? 0xFFF2B8B5 : 0xFFB3261E;
    }

    /** A group's container: round at the card's outer ends, gently round where groups meet. */
    private static GradientDrawable shape(boolean first, boolean last) {
        float big = Round.px(20f);
        float small = Round.px(8f);
        float top = first ? big : small;
        float bottom = last ? big : small;
        GradientDrawable d = new GradientDrawable();
        /* The highest tone of the surface: above every row and card of a room,
           so the menu is never a hole in what it stands over. */
        d.setColor(Tone.of(Tone.SURFACE_HIGHEST));
        d.setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
        return d;
    }

    private static View rowView(Context c, Row row, int ink) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(Round.dp(48));
        line.setPadding(Round.dp(16), 0, Round.dp(20), 0);
        line.setBackground(Round.touch(Round.box(0x00000000, Round.px(12f)), Tone.of(Tone.ON_SURFACE),
            Round.px(12f)));
        View mark = new Glyph(c, row.mark, Round.px(24f), 0.92f, 0x00000000, 0x00000000, ink);
        LinearLayout.LayoutParams markAt = new LinearLayout.LayoutParams(Round.dp(24), Round.dp(24));
        markAt.rightMargin = Round.dp(14);
        line.addView(mark, markAt);
        TextView said = Letter.set(new TextView(c), Letter.BODY_L);
        said.setText(row.words);
        said.setTextColor(ink);
        said.setSingleLine(true);
        line.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (row.end != null && row.end.length() > 0) {
            TextView end = Letter.set(new TextView(c), Letter.LABEL_L);
            end.setText(row.end);
            end.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
            LinearLayout.LayoutParams endAt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            endAt.leftMargin = Round.dp(16);
            line.addView(end, endAt);
        }
        return line;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int most = Math.min(Round.dp(300), w - Round.dp(24));
        holder.measure(MeasureSpec.makeMeasureSpec(most, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(Math.max(Round.dp(120), h - Round.dp(24)), MeasureSpec.AT_MOST));
        int cw = Math.max(Round.dp(208), holder.getMeasuredWidth());
        if (cw != holder.getMeasuredWidth()) {
            holder.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(holder.getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
        scrim.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    /**
     * The card beside the point, to the side with room: right and below if it
     * fits, else left, else above; always a little off the finger, so the
     * finger does not hide the first row.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        scrim.layout(0, 0, w, h);
        int cw = holder.getMeasuredWidth();
        int ch = holder.getMeasuredHeight();
        int margin = Round.dp(12);
        int off = Round.dp(8);
        int x = Math.round(anchorX) + off;
        if (x + cw > w - margin) {
            x = Math.round(anchorX) - off - cw;
        }
        x = Math.max(margin, Math.min(w - margin - cw, x));
        int y = Math.round(anchorY) + off;
        if (y + ch > h - margin) {
            y = Math.round(anchorY) - off - ch;
        }
        y = Math.max(margin, Math.min(h - margin - ch, y));
        holder.layout(x, y, x + cw, y + ch);
        holder.setPivotX(anchorX - x);
        holder.setPivotY(anchorY - y);
    }

    /** It grows out of the point. */
    void open() {
        scrim.animate().alpha(1f).setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
        holder.setScaleX(0.55f);
        holder.setScaleY(0.55f);
        holder.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.EMPHASIS).start();
        for (int i = 0; i < rowViews.size(); i++) {
            View v = rowViews.get(i);
            v.setAlpha(0f);
            v.setTranslationY(-Round.px(6f));
            v.animate().alpha(1f).translationY(0f).setStartDelay(30L + i * 18L)
                .setDuration(Pace.PRESS + 60L).setInterpolator(Pace.STANDARD).start();
        }
    }

    /** The finger that held, still down: where it began, to tell a still finger from a moving one. */
    void heldAt(float rawX, float rawY) {
        downX = rawX;
        downY = rawY;
    }

    /** The finger moves over the card without having lifted: the row under it lights. */
    void follow(float rawX, float rawY) {
        if (Math.abs(rawX - downX) + Math.abs(rawY - downY) > slop) {
            moved = true;
        }
        light(rowAt(rawX, rawY));
    }

    /** The finger lifts: on a row, it is done; still, the card stays; moved onto nothing, it goes. */
    void release(float rawX, float rawY) {
        int at = rowAt(rawX, rawY);
        if (at >= 0 && moved) {
            choose(at);
        } else if (moved) {
            dismiss();
        } else {
            light(-1);
        }
    }

    private int rowAt(float rawX, float rawY) {
        int[] where = new int[2];
        for (int i = 0; i < rowViews.size(); i++) {
            View v = rowViews.get(i);
            v.getLocationOnScreen(where);
            if (rawX >= where[0] && rawX <= where[0] + v.getWidth()
                && rawY >= where[1] && rawY <= where[1] + v.getHeight()) {
                return i;
            }
        }
        return -1;
    }

    private void light(int at) {
        if (at == lit) {
            return;
        }
        if (lit >= 0) {
            rowViews.get(lit).setBackground(Round.touch(Round.box(0x00000000, Round.px(12f)),
                Tone.of(Tone.ON_SURFACE), Round.px(12f)));
        }
        lit = at;
        if (at >= 0) {
            rowViews.get(at).setBackground(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.px(12f)));
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void choose(int at) {
        if (leaving || at < 0 || at >= rows.size()) {
            return;
        }
        final Row row = rows.get(at);
        light(at);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        boolean close = row.act == null || row.act.act(row, this);
        if (close) {
            /* The chosen row flashes a moment before the card folds. */
            View v = rowViews.get(at);
            v.animate().alpha(0.4f).setDuration(70L).withEndAction(new Runnable() {
                public void run() {
                    dismiss();
                }
            }).start();
        } else {
            moved = false;
        }
    }

    /** A row's words changed where it stands: a question asked once more. */
    void say(Row row, String words) {
        int at = rows.indexOf(row);
        if (at < 0) {
            return;
        }
        row.words = words;
        LinearLayout line = (LinearLayout) rowViews.get(at);
        ((TextView) line.getChildAt(1)).setText(words);
        line.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /** It folds back into the point it came from. */
    void dismiss() {
        if (leaving) {
            return;
        }
        leaving = true;
        scrim.animate().alpha(0f).setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).start();
        holder.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(Bloom.this);
                    }
                    if (gone != null) {
                        gone.gone();
                    }
                }
            }).start();
    }

    boolean leaving() {
        return leaving;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return false;
    }
}
