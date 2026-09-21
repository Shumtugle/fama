package io.github.shumtugle.fama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * The mark of the application, standing in the upper corner of the board.
 *
 * It is the front layer of the home screen's icon, taken whole: a herald's
 * trumpet raised, a gold ball on its shaft, a banner hung under it. On the
 * home screen it lies on Pompeian red; here it lies on a disc of the seed's own
 * container, and changes with the look of the whole. The disc shows what a
 * round mask on the home screen would show: the middle two thirds of the
 * layer.
 *
 * It is the way into the settings. Pressed, it gives a little under the
 * finger, as the round button does.
 */
final class Crest extends View {

    /** The layer is a square of this many units, of which the middle SEEN are shown. */
    private static final float SQUARE = 108f;
    private static final float SEEN = 72f;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable mark;
    private final Path round = new Path();
    private final float size;

    Crest(Context context, float sizePx) {
        super(context);
        size = sizePx;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, Round.px(0.5f)));
        mark = context.getDrawable(R.drawable.ic_fg);
        float unit = sizePx / SEEN;
        int whole = Math.round(SQUARE * unit);
        int left = Math.round(sizePx / 2f - whole / 2f);
        mark.setBounds(left, left, left + whole, left + whole);
        round.addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW);
        setClickable(true);
        tint();
    }

    void tint() {
        disc.setColor(Tone.of(Tone.PRIMARY_CONTAINER));
        edge.setColor(Tone.of(Tone.OUTLINE_VARIANT));
        setBackground(Round.disc(Tone.of(Tone.ON_SURFACE)));
        invalidate();
    }

    @Override
    public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        if (was == pressed) {
            return;
        }
        float to = pressed ? 0.9f : 1f;
        animate().scaleX(to).scaleY(to).setStartDelay(0L).setDuration(Pace.PRESS)
            .setInterpolator(Pace.STANDARD).start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(size), Math.round(size));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float middle = size / 2f;
        canvas.drawCircle(middle, middle, middle, disc);
        canvas.drawCircle(middle, middle, middle - edge.getStrokeWidth(), edge);
        canvas.save();
        canvas.clipPath(round);
        mark.draw(canvas);
        canvas.restore();
    }
}
