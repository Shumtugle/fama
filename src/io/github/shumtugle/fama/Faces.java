package io.github.shumtugle.fama;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.io.File;
import java.util.HashMap;

/**
 * The faces of the channels, as discs.
 *
 * A channel's own picture where one has been fetched; until then, and for
 * a channel that has none, the first letter of its name in the book face on
 * a disc of one of the seed's containers, the same container for the same
 * name every time, so a channel is known by its colour before it is read.
 */
final class Faces {

    private static final HashMap<String, Bitmap> KEPT = new HashMap<String, Bitmap>();

    private Faces() {
    }

    static synchronized Bitmap of(Context context, String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (KEPT.containsKey(key)) {
            return KEPT.get(key);
        }
        File file = Board.face(context, name);
        Bitmap made = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        KEPT.put(key, made);
        return made;
    }

    /** A picture fetched anew: the disc of that name draws it next time. */
    static synchronized void forget(String name) {
        KEPT.remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** One face. */
    static final class Disc extends View {

        private static final int[][] GROUNDS = {
            {Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER},
            {Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER},
            {Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER},
            {Tone.SURFACE_HIGHEST, Tone.PRIMARY},
        };

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix fit = new Matrix();
        private final float size;
        private final String initial;
        private final int[] ground;
        private final Bitmap picture;

        Disc(Context context, String name, String title, float sizePx) {
            super(context);
            size = sizePx;
            String t = title == null || title.trim().length() == 0 ? name : title.trim();
            int first = 0;
            while (first < t.length() && !Character.isLetterOrDigit(t.charAt(first))) {
                first++;
            }
            initial = first < t.length() ? t.substring(first, first + 1).toUpperCase() : "\u00B7";
            ground = GROUNDS[(name.toLowerCase(java.util.Locale.ROOT).hashCode() & 0x7FFFFFFF)
                % GROUNDS.length];
            picture = name.length() == 0 ? null : Faces.of(context, name);
            letter.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            letter.setTextAlign(Paint.Align.CENTER);
            letter.setTextSize(sizePx * 0.46f);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(Math.round(size), Math.round(size));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = size / 2f;
            if (picture != null) {
                BitmapShader shader = new BitmapShader(picture, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
                float scale = size / Math.min(picture.getWidth(), picture.getHeight());
                fit.setScale(scale, scale);
                shader.setLocalMatrix(fit);
                paint.setShader(shader);
                canvas.drawCircle(r, r, r, paint);
                return;
            }
            paint.setShader(null);
            paint.setColor(Tone.of(ground[0]));
            canvas.drawCircle(r, r, r, paint);
            letter.setColor(Tone.of(ground[1]));
            Paint.FontMetrics m = letter.getFontMetrics();
            canvas.drawText(initial, r, r - (m.ascent + m.descent) / 2f, letter);
        }
    }
}
