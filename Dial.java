package io.github.shumtugle.fama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * A number chosen by sliding along a track that shows what it chooses.
 *
 * The track is painted with the colours the value will give — the whole
 * circle of hues, or one hue from grey to its fullest — so the choice is
 * made by eye and not by reading a number. The handle is a narrow upright
 * bar with a gap of ground on either side, and it thins while it is held,
 * so the finger does not hide the very colour it is choosing.
 *
 * Every twentieth of the way the phone ticks under the finger.
 */
final class Dial extends View {

    interface Moved {
        void moved(float value, boolean done);
    }

    private static final int NOTCHES = 20;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Moved moved;
    private int[] colours = {0xFF808080, 0xFF808080};
    private float value;
    private int notch = -1;
    private boolean held;

    Dial(Context context, float start, Moved listener) {
        super(context);
        value = clamp(start);
        moved = listener;
        handle.setStyle(Paint.Style.FILL);
        track.setStyle(Paint.Style.FILL);
    }

    void colours(int[] stops) {
        colours = stops;
        shade();
        invalidate();
    }

    void ink(int colour) {
        handle.setColor(colour);
        invalidate();
    }

    void value(float v) {
        value = clamp(v);
        invalidate();
    }

    float value() {
        return value;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private float inset() {
        return Round.px(12f);
    }

    private void shade() {
        float from = inset();
        float to = Math.max(from + 1f, getWidth() - inset());
        track.setShader(new LinearGradient(from, 0f, to, 0f, colours, null,
            Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shade();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Round.dp(48));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                held = true;
                follow(event.getX(), false);
                return true;
            case MotionEvent.ACTION_MOVE:
                follow(event.getX(), false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                held = false;
                follow(event.getX(), true);
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void follow(float x, boolean done) {
        float span = getWidth() - 2f * inset();
        value = clamp((x - inset()) / Math.max(1f, span));
        int now = Math.round(value * NOTCHES);
        if (now != notch) {
            if (notch >= 0 && !done) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            notch = now;
        }
        if (done) {
            notch = -1;
        }
        invalidate();
        if (moved != null) {
            moved.moved(value, done);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float from = inset();
        float to = getWidth() - inset();
        float x = from + (to - from) * value;
        float tall = Round.px(16f);
        float gap = Round.px(6f);
        float wide = Round.px(held ? 2f : 4f);
        float top = (h - tall) / 2f;

        /* The track in two pieces, with ground between them and the handle. */
        float leftEnd = x - wide / 2f - gap;
        if (leftEnd > from) {
            bar.set(from, top, leftEnd, top + tall);
            canvas.drawRoundRect(bar, Round.px(8f), Round.px(8f), track);
        }
        float rightStart = x + wide / 2f + gap;
        if (rightStart < to) {
            bar.set(rightStart, top, to, top + tall);
            canvas.drawRoundRect(bar, Round.px(8f), Round.px(8f), track);
        }

        float handleTall = Round.px(44f);
        float handleTop = (h - handleTall) / 2f;
        bar.set(x - wide / 2f, handleTop, x + wide / 2f, handleTop + handleTall);
        canvas.drawRoundRect(bar, wide / 2f, wide / 2f, handle);
    }
}
