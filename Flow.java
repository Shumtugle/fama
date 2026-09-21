package io.github.shumtugle.fama;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Small things set one after another like words in a line, and the line
 * broken where the next one would not fit: the words of a list, as capsules.
 */
final class Flow extends ViewGroup {

    private final int gap;

    Flow(Context context, int gapPx) {
        super(context);
        gap = gapPx;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int x = 0;
        int y = 0;
        int line = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View one = getChildAt(i);
            if (one.getVisibility() == GONE) {
                continue;
            }
            one.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = one.getMeasuredWidth();
            if (x > 0 && x + w > width) {
                x = 0;
                y += line + gap;
                line = 0;
            }
            x += w + gap;
            line = Math.max(line, one.getMeasuredHeight());
        }
        setMeasuredDimension(width, y + line);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int x = 0;
        int y = 0;
        int line = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View one = getChildAt(i);
            if (one.getVisibility() == GONE) {
                continue;
            }
            int w = one.getMeasuredWidth();
            int h = one.getMeasuredHeight();
            if (x > 0 && x + w > width) {
                x = 0;
                y += line + gap;
                line = 0;
            }
            one.layout(x, y, x + w, y + h);
            x += w + gap;
            line = Math.max(line, h);
        }
    }
}
