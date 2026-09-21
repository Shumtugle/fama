package io.github.shumtugle.fama;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * The one round thing in the bar. On the board it reads the channels again,
 * and it wears the mark of that: most of a circle and the arrow closing it.
 * While the channels are being read the circle turns, slowly and without
 * end, the one sign that something is at work behind the board.
 *
 * On a screen of ours the mark inside is one gesture, an arrow whose shaft
 * runs on into a tick, and it writes itself as the screen arrives. The shape
 * stays a circle: a button is a full capsule, and emphasis is carried by
 * colour and motion, not by a new outline. Pressed, the button gives a
 * little; let go, it leaves to the left, as if the screen had taken the work
 * and let you go, and whatever comes next grows in where it stood.
 */
public final class Blob extends View {

    /** The curve every block in the application arrives on. */
    private static final PathInterpolator ARRIVE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** The mirror of an arrival: slow to start, gone at speed. */
    private static final PathInterpolator LEAVE = new PathInterpolator(0.3f, 0f, 1f, 1f);

    private static final long GROW = 460L;
    private static final long GIVE = 140L;
    private static final long GO = 200L;
    private static final float SQUEEZE = 0.9f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path gesture = new Path();
    private final Path written = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float gestureLength;
    private final float size;

    private boolean leaving;
    /** How far the circle has turned while the channels are read, and what turns it. */
    private float turn;
    private ValueAnimator turning;
    private final android.graphics.RectF ring = new android.graphics.RectF();
    private boolean departing;
    /** How much of the gesture has been written. */
    private float ink = 1f;
    private ValueAnimator writing;

    public Blob(Context context, float sizePx, int face, int glyph) {
        super(context);
        size = sizePx;
        fill.setColor(face);
        mark.setColor(glyph);
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeWidth(sizePx * 0.09f);
        line.setColor(glyph);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(sizePx * 0.09f);

        /* On the grid of twenty four, centred. The pen goes down the head,
           back up to its point, along the shaft and on into the tick, so the
           whole mark is written in one stroke and fits inside the circle. */
        float u = sizePx / 24f;
        float dy = 0.5f;
        gesture.moveTo(8.5f * u, (7.5f + dy) * u);
        gesture.lineTo(4.5f * u, (11.5f + dy) * u);
        gesture.lineTo(8.5f * u, (15.5f + dy) * u);
        gesture.lineTo(4.5f * u, (11.5f + dy) * u);
        gesture.lineTo(12f * u, (11.5f + dy) * u);
        gesture.lineTo(14.8f * u, (14.3f + dy) * u);
        gesture.lineTo(19.8f * u, (8.8f + dy) * u);
        measure.setPath(gesture, false);
        gestureLength = measure.getLength();
    }

    public void tint(int face, int glyph) {
        fill.setColor(face);
        mark.setColor(glyph);
        line.setColor(glyph);
        invalidate();
    }

    /**
     * On a screen of ours the button is the way back to the page, and it
     * says so: an arrow returning, with the tick that means the thing is
     * done. A cross would say something else — that the work is thrown away.
     */
    public void leaving(boolean going) {
        if (leaving == going) {
            return;
        }
        leaving = going;
        /* Mid departure the old mark stays as it is; the new state is taken
           up when the button grows back in. */
        if (departing) {
            return;
        }
        if (going) {
            write();
        } else {
            invalidate();
        }
    }

    /** The channels are being read, or have been: the circle turns, or comes to rest. */
    public void busy(boolean on) {
        if (on) {
            if (turning != null) {
                return;
            }
            turning = ValueAnimator.ofFloat(0f, 360f);
            turning.setDuration(1400L);
            turning.setRepeatCount(ValueAnimator.INFINITE);
            turning.setInterpolator(new android.view.animation.LinearInterpolator());
            turning.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    turn = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            turning.start();
            return;
        }
        if (turning == null) {
            return;
        }
        turning.cancel();
        turning = null;
        /* It does not stop dead: it finishes its way round, and settles. */
        ValueAnimator settle = ValueAnimator.ofFloat(turn, 360f);
        settle.setDuration(Math.max(120L, (long) ((360f - turn) / 360f * 900f)));
        settle.setInterpolator(ARRIVE);
        settle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                turn = (Float) animation.getAnimatedValue() % 360f;
                invalidate();
            }
        });
        settle.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (turning != null) {
            turning.cancel();
            turning = null;
        }
        super.onDetachedFromWindow();
    }

    /**
     * The screen is finished with. Called before the screen is taken down,
     * whichever way it was asked for: the button and the system back are one
     * door, so they close it the same way.
     */
    public void depart() {
        if (departing) {
            return;
        }
        departing = true;
        if (writing != null) {
            writing.cancel();
        }
        ink = 1f;
        invalidate();
        animate().cancel();
        animate().translationX(-size * 0.8f).scaleX(0.84f).scaleY(0.84f).alpha(0f)
            .setDuration(GO).setInterpolator(LEAVE)
            .withEndAction(new Runnable() {
                public void run() {
                    arrive();
                }
            })
            .start();
    }

    private void arrive() {
        departing = false;
        setTranslationX(0f);
        setScaleX(0.6f);
        setScaleY(0.6f);
        animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(GROW).setInterpolator(ARRIVE).start();
        if (leaving) {
            write();
        } else {
            invalidate();
        }
    }

    /** The gesture writes itself, on the same curve and time as any arrival. */
    private void write() {
        if (writing != null) {
            writing.cancel();
        }
        ink = 0f;
        writing = ValueAnimator.ofFloat(0f, 1f);
        writing.setDuration(GROW);
        writing.setInterpolator(ARRIVE);
        writing.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                ink = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        writing.start();
    }

    /** A little give under the finger, and back when it lifts. */
    @Override
    public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        if (was == pressed || departing) {
            return;
        }
        float to = pressed ? SQUEEZE : 1f;
        animate().scaleX(to).scaleY(to).setDuration(GIVE).setInterpolator(ARRIVE).start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(size), Math.round(size));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float centre = size / 2f;
        canvas.drawCircle(centre, centre, centre, fill);

        if (leaving || departing) {
            if (ink >= 1f) {
                canvas.drawPath(gesture, line);
            } else if (ink > 0f) {
                written.reset();
                measure.getSegment(0f, gestureLength * ink, written, true);
                canvas.drawPath(written, line);
            }
            return;
        }

        /* Most of a circle and the arrow that closes it, turned while at work. */
        float radius = size * 0.2f;
        canvas.save();
        canvas.rotate(turn, centre, centre);
        ring.set(centre - radius, centre - radius, centre + radius, centre + radius);
        canvas.drawArc(ring, -60f, 290f, false, line);
        /* The head at the arc's end, pointing on round the way the circle turns. */
        double at = Math.toRadians(230f);
        float tipX = centre + (float) (radius * Math.cos(at));
        float tipY = centre + (float) (radius * Math.sin(at));
        double backX = Math.sin(at);
        double backY = -Math.cos(at);
        float head = size * 0.11f;
        for (int side = -1; side <= 1; side += 2) {
            double a = Math.toRadians(40f * side);
            float armX = (float) (backX * Math.cos(a) - backY * Math.sin(a));
            float armY = (float) (backX * Math.sin(a) + backY * Math.cos(a));
            canvas.drawLine(tipX, tipY, tipX + armX * head, tipY + armY * head, line);
        }
        canvas.restore();
    }
}
