package io.github.shumtugle.fama;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The words a board is read by, as a cloud whose sizes are true.
 *
 * A word is as large as the number of announcements it found at the last
 * reading, and as saturated: the words that do the work stand large and in
 * the board's colour, the ones that found nothing stand small and pale, and
 * the ones switched off are crossed through. The words that turn a post away
 * wear the third accent. A word of one's own is set in the italic of the
 * book face.
 *
 * The cloud moves only twice. When the screen opens, the words come forward
 * from the middle, the largest first, and settle; after that they stand
 * still. A touch lifts a word, and its neighbours step aside to give it room;
 * a second touch on the same word opens what can be done with it.
 */
final class Cloud extends View {

    /** One word in the cloud. */
    static final class Word {
        final String kind;
        final String key;
        final String label;
        final boolean own;
        final boolean off;
        final int count;

        float size;
        final RectF box = new RectF();
        float cx;
        float cy;
        float baseline;

        Word(String kind, String key, String label, boolean own, boolean off, int count) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.own = own;
            this.off = off;
            this.count = count;
        }
    }

    interface Touch {
        void open(Word word);
    }

    private static final float SMALLEST = 13f;
    private static final float LARGEST = 34f;

    private final ArrayList<Word> words;
    private final Touch touch;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface book;
    private final Typeface bookItalic;
    private final float pad;
    private int laidFor = -1;
    private int height;
    private int most = 1;

    /** How far the arrival has gone, nought to one; one when it is over or not wanted. */
    private float arrived;
    private ValueAnimator arriving;
    private Word lifted;
    private float lift;
    private ValueAnimator lifting;

    Cloud(Context context, List<Word> given, boolean arrive, Touch touch) {
        super(context);
        this.touch = touch;
        words = new ArrayList<Word>(given);
        book = Typeface.create("serif", Typeface.NORMAL);
        bookItalic = Typeface.create("serif", Typeface.ITALIC);
        pad = Round.px(5f);
        ink.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < words.size(); i++) {
            most = Math.max(most, words.get(i).count);
        }
        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            float t = w.off || w.count == 0 ? 0f : (float) Math.sqrt(w.count / (double) most);
            w.size = Round.px(SMALLEST + (LARGEST - SMALLEST) * t);
        }
        Collections.sort(words, new Comparator<Word>() {
            public int compare(Word a, Word b) {
                if (a.size != b.size) {
                    return a.size > b.size ? -1 : 1;
                }
                return a.label.compareTo(b.label);
            }
        });
        arrived = arrive ? 0f : 1f;
        setClickable(true);
        StringBuilder said = new StringBuilder();
        for (int i = 0; i < words.size() && i < 8; i++) {
            said.append(i > 0 ? ", " : "").append(words.get(i).label);
        }
        setContentDescription(said.toString());
    }

    // ------------------------------------------------------------------ laying out

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width != laidFor && width > 0) {
            lay(width);
            laidFor = width;
        }
        setMeasuredDimension(width, height);
    }

    /**
     * The words set on a widening oval spiral from the middle, the largest
     * first, each at the first place where it touches nobody. If the words
     * do not fit, the cloud is made taller and laid again.
     */
    private void lay(int width) {
        float h = width * 0.62f;
        for (int tries = 0; tries < 6; tries++) {
            if (layInto(width, h)) {
                break;
            }
            h *= 1.25f;
        }
        height = Math.round(h);
    }

    private boolean layInto(int width, float h) {
        float cx = width / 2f;
        float cy = h / 2f;
        ArrayList<RectF> taken = new ArrayList<RectF>();
        boolean all = true;
        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            face(w);
            float tw = ink.measureText(w.label) + pad * 2f;
            float th = w.size * 1.18f + pad;
            boolean placed = false;
            for (float t = 0f; t < 90f && !placed; t += 0.22f) {
                float r = Round.px(3.2f) * t;
                float x = cx + (float) Math.cos(t) * r * 1.55f;
                float y = cy + (float) Math.sin(t) * r * 0.85f;
                RectF box = new RectF(x - tw / 2f, y - th / 2f, x + tw / 2f, y + th / 2f);
                if (box.left < 0f || box.right > width || box.top < 0f || box.bottom > h) {
                    continue;
                }
                boolean free = true;
                for (int k = 0; k < taken.size() && free; k++) {
                    free = !RectF.intersects(box, taken.get(k));
                }
                if (free) {
                    taken.add(box);
                    w.box.set(box);
                    w.cx = x;
                    w.cy = y;
                    Paint.FontMetrics fm = ink.getFontMetrics();
                    w.baseline = y - (fm.ascent + fm.descent) / 2f;
                    placed = true;
                }
            }
            all = all && placed;
            if (!placed) {
                w.box.setEmpty();
            }
        }
        return all;
    }

    private void face(Word w) {
        ink.setTypeface(w.own ? bookItalic : book);
        ink.setTextSize(w.size);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (arrived < 1f && arriving == null) {
            arriving = ValueAnimator.ofFloat(0f, 1f);
            arriving.setDuration(Pace.ARRIVE + Math.min(words.size(), 24) * 28L);
            arriving.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    arrived = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            arriving.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (arriving != null) {
            arriving.cancel();
        }
        if (lifting != null) {
            lifting.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float mx = getWidth() / 2f;
        float my = getHeight() / 2f;
        int n = words.size();
        /* The smallest are drawn first, so the large ones stand in front. */
        for (int i = n - 1; i >= 0; i--) {
            Word w = words.get(i);
            if (w.box.isEmpty()) {
                continue;
            }
            float step = n <= 1 ? 0f : 0.55f / n;
            float local = clamp((arrived - i * step) / 0.45f);
            float come = Pace.STANDARD.getInterpolation(local);
            float x = mx + (w.cx - mx) * (0.35f + 0.65f * come);
            float y = my + (w.cy - my) * (0.35f + 0.65f * come);
            float[] aside = aside(w);
            x += aside[0];
            y += aside[1];
            float scale = 0.7f + 0.3f * come;
            boolean up = w == lifted;
            if (up) {
                scale *= 1f + 0.35f * lift;
            }
            face(w);
            ink.setColor(colour(w, up));
            ink.setAlpha(Math.round(alphaOf(w, up) * 255f * come));
            ink.setStrikeThruText(w.off);
            canvas.save();
            canvas.scale(scale, scale, x, y);
            canvas.drawText(w.label, x, w.baseline - w.cy + y, ink);
            canvas.restore();
        }
        ink.setStrikeThruText(false);
    }

    /** How far a word has stepped aside for the lifted one. */
    private float[] aside(Word w) {
        if (lifted == null || w == lifted || lift <= 0f) {
            return new float[] {0f, 0f};
        }
        float dx = w.cx - lifted.cx;
        float dy = w.cy - lifted.cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float reach = Math.max(lifted.box.width(), lifted.box.height()) * 1.2f;
        if (d <= 1f || d > reach) {
            return new float[] {0f, 0f};
        }
        float push = (1f - d / reach) * Round.px(16f) * lift;
        return new float[] {dx / d * push, dy / d * push};
    }

    /** The word's colour: the board's for the words that find, the third accent for those that turn away. */
    private int colour(Word w, boolean up) {
        if (w.off) {
            return Tone.of(Tone.OUTLINE);
        }
        if (Lex.AWAY.equals(w.kind)) {
            return Tone.of(Tone.TERTIARY);
        }
        if (up) {
            return Tone.of(Tone.PRIMARY);
        }
        float t = w.count == 0 ? 0f : (float) Math.sqrt(w.count / (double) most);
        return t >= 0.5f ? Tone.of(Tone.PRIMARY) : t > 0f ? Tone.of(Tone.ON_SURFACE) : Tone.of(Tone.ON_SURFACE_VARIANT);
    }

    private float alphaOf(Word w, boolean up) {
        if (up) {
            return 1f;
        }
        if (w.off) {
            return 0.55f;
        }
        if (w.count == 0) {
            return 0.5f;
        }
        return 0.75f + 0.25f * (float) Math.sqrt(w.count / (double) most);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            Word hit = at(e.getX(), e.getY());
            if (hit != null && hit == lifted) {
                performClick();
                touch.open(hit);
            } else {
                raise(hit);
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** The word under a finger: the smallest box that holds the point, since it stands in front there. */
    private Word at(float x, float y) {
        Word best = null;
        float slack = Round.px(6f);
        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            if (w.box.isEmpty()) {
                continue;
            }
            float[] a = aside(w);
            RectF box = new RectF(w.box);
            box.offset(a[0], a[1]);
            box.inset(-slack, -slack);
            if (box.contains(x, y) && (best == null || w.size <= best.size)) {
                best = w;
            }
        }
        return best;
    }

    /** One word lifted, or none: the old one settles, the new one rises. */
    private void raise(final Word next) {
        if (lifting != null) {
            lifting.cancel();
        }
        if (next == null && lifted == null) {
            return;
        }
        if (next != null && next != lifted) {
            lifted = next;
            lift = 0f;
        }
        lifting = ValueAnimator.ofFloat(lift, next == null ? 0f : 1f);
        lifting.setDuration(Pace.GROW);
        lifting.setInterpolator(Pace.STANDARD);
        lifting.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                lift = (Float) a.getAnimatedValue();
                if (next == null && a.getAnimatedFraction() >= 1f) {
                    lifted = null;
                }
                invalidate();
            }
        });
        lifting.start();
    }
}
