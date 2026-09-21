package io.github.shumtugle.fama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * The marks the application wears, drawn rather than imported.
 *
 * Each one is built on the same square of twenty four units with the same
 * two unit stroke, so they carry equal optical weight side by side. Where
 * shapes would cross, the upper one is knocked out of the lower with the
 * colour underneath instead of being drawn across it: crossed strokes read
 * as a mistake.
 *
 * A mark can stand in a disc of its own or bare on whatever is behind it.
 * The drawing itself is open to other views, so a large shape can carry
 * the same mark a button carries, at its own size.
 */
public final class Glyph extends View {

    public static final int SETTINGS = 0;
    public static final int BACK = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;
    public static final int PICTURE = 4;
    public static final int FILM = 5;
    public static final int NOTE = 6;
    public static final int BOOK = 7;
    public static final int LOOK = 8;
    public static final int LANGUAGE = 9;
    public static final int SPARK = 10;
    public static final int TICK = 11;
    public static final int PLAY = 12;
    public static final int PAUSE = 13;
    public static final int PREV = 14;
    public static final int NEXT = 15;
    public static final int CROSS = 16;
    public static final int REWIND = 17;
    public static final int SKIP = 18;
    public static final int CARET = 19;
    public static final int RIBBON = 20;
    public static final int SHARE = 21;
    public static final int EDIT = 22;
    public static final int TURN = 23;
    public static final int CAMERA = 24;
    public static final int BOARD = 25;
    public static final int CHANNEL = 26;
    public static final int TAG = 27;
    public static final int OPEN = 28;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float size;
    private final float unit;
    private int kind;

    /**
     * A mark in a disc. A clear fill leaves the disc out, a clear line its
     * edge. The span is how much of the view the grid of twenty four takes:
     * a little under half inside a disc, nearly all of it bare.
     */
    public Glyph(Context context, int which, float sizePx, float span,
                 int fill, int line, int ink) {
        super(context);
        kind = which;
        size = sizePx;
        unit = sizePx * span / 24f;
        disc.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, sizePx * 0.012f));
        cut.setStyle(Paint.Style.FILL);
        pens(pen, solid, unit);
        tint(fill, line, ink);
    }

    /** Sets up the two paints a mark is drawn with, for a grid unit of a given size. */
    public static void pens(Paint stroke, Paint fill, float unit) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(unit * 2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    /** New colours for the same mark, when the seed changes under it. */
    public void tint(int fill, int line, int ink) {
        disc.setColor(fill);
        cut.setColor(fill);
        edge.setColor(line);
        pen.setColor(ink);
        solid.setColor(ink);
        invalidate();
    }

    /** The same place can change what is drawn in it. */
    public void kind(int what) {
        if (kind != what) {
            kind = what;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(size), Math.round(size));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float middle = size / 2f;
        if ((disc.getColor() >>> 24) != 0) {
            canvas.drawCircle(middle, middle, middle, disc);
        }
        if ((edge.getColor() >>> 24) != 0) {
            canvas.drawCircle(middle, middle, middle - edge.getStrokeWidth(), edge);
        }
        mark(canvas, kind, middle, middle, unit, pen, solid, cut);
    }

    /**
     * Draws a mark centred on a point, the grid unit given in pixels. The
     * knock-out paint should carry the colour the mark stands on.
     */
    public static void mark(Canvas canvas, int kind, float x, float y, float u,
                            Paint pen, Paint solid, Paint cut) {
        Grid g = new Grid(x, y, u);
        switch (kind) {
            case BACK:
                canvas.drawLine(g.x(3.5f), g.y(-7f), g.x(-3.5f), g.y(0f), pen);
                canvas.drawLine(g.x(-3.5f), g.y(0f), g.x(3.5f), g.y(7f), pen);
                break;

            case UP:
                canvas.drawLine(g.x(-6f), g.y(3f), g.x(0f), g.y(-3.5f), pen);
                canvas.drawLine(g.x(0f), g.y(-3.5f), g.x(6f), g.y(3f), pen);
                break;

            case DOWN:
                canvas.drawLine(g.x(-6f), g.y(-3.5f), g.x(0f), g.y(3f), pen);
                canvas.drawLine(g.x(0f), g.y(3f), g.x(6f), g.y(-3.5f), pen);
                break;

            case TICK:
                canvas.drawLine(g.x(-6.5f), g.y(0.5f), g.x(-2f), g.y(5f), pen);
                canvas.drawLine(g.x(-2f), g.y(5f), g.x(7f), g.y(-5f), pen);
                break;

            case PLAY: {
                /* Rounded at every corner, and set a little right of centre,
                   where the eye puts the middle of a triangle. */
                Path play = new Path();
                play.moveTo(g.x(-4f), g.y(-7f));
                play.lineTo(g.x(7.5f), g.y(0f));
                play.lineTo(g.x(-4f), g.y(7f));
                play.close();
                canvas.drawPath(play, solid);
                canvas.drawPath(play, pen);
                break;
            }

            case PAUSE: {
                float r = u * 1.4f;
                canvas.drawRoundRect(g.box(-6f, -7f, -1.8f, 7f), r, r, solid);
                canvas.drawRoundRect(g.box(1.8f, -7f, 6f, 7f), r, r, solid);
                break;
            }

            case PREV: {
                Path back = new Path();
                back.moveTo(g.x(6f), g.y(-6f));
                back.lineTo(g.x(-2.5f), g.y(0f));
                back.lineTo(g.x(6f), g.y(6f));
                back.close();
                canvas.drawPath(back, solid);
                canvas.drawPath(back, pen);
                canvas.drawLine(g.x(-6f), g.y(-6.5f), g.x(-6f), g.y(6.5f), pen);
                break;
            }

            case NEXT: {
                Path on = new Path();
                on.moveTo(g.x(-6f), g.y(-6f));
                on.lineTo(g.x(2.5f), g.y(0f));
                on.lineTo(g.x(-6f), g.y(6f));
                on.close();
                canvas.drawPath(on, solid);
                canvas.drawPath(on, pen);
                canvas.drawLine(g.x(6f), g.y(-6.5f), g.x(6f), g.y(6.5f), pen);
                break;
            }

            case REWIND:
            case SKIP: {
                /* A turning arrow with the seconds it jumps inside it: back
                   turns against the clock, on turns with it. */
                canvas.save();
                if (kind == SKIP) {
                    canvas.scale(-1f, 1f, x, y);
                }
                float ring = 8f;
                float start = -60f;
                canvas.drawArc(g.box(-ring, -ring, ring, ring), start, 290f, false, pen);
                double turn = Math.toRadians(start);
                float tipX = (float) (ring * Math.cos(turn));
                float tipY = (float) (ring * Math.sin(turn));
                float alongX = (float) -Math.sin(turn);
                float alongY = (float) Math.cos(turn);
                float acrossX = -alongY;
                float acrossY = alongX;
                Path head = new Path();
                head.moveTo(g.x(tipX - alongX * 3.4f), g.y(tipY - alongY * 3.4f));
                head.lineTo(g.x(tipX + acrossX * 2.6f), g.y(tipY + acrossY * 2.6f));
                head.lineTo(g.x(tipX - acrossX * 2.6f), g.y(tipY - acrossY * 2.6f));
                head.close();
                canvas.drawPath(head, solid);
                canvas.restore();
                Paint figure = new Paint(solid);
                figure.setTextAlign(Paint.Align.CENTER);
                figure.setTextSize(u * 7.2f);
                figure.setFakeBoldText(true);
                Paint.FontMetrics fm = figure.getFontMetrics();
                canvas.drawText(kind == REWIND ? "15" : "30", x, y - (fm.ascent + fm.descent) / 2f,
                    figure);
                break;
            }

            case CARET:
                /* The cursor a finger picks words with: a stem and its two feet. */
                canvas.drawLine(g.x(0f), g.y(-7.5f), g.x(0f), g.y(7.5f), pen);
                canvas.drawLine(g.x(-3.5f), g.y(-8.5f), g.x(-0.8f), g.y(-7.5f), pen);
                canvas.drawLine(g.x(3.5f), g.y(-8.5f), g.x(0.8f), g.y(-7.5f), pen);
                canvas.drawLine(g.x(-3.5f), g.y(8.5f), g.x(-0.8f), g.y(7.5f), pen);
                canvas.drawLine(g.x(3.5f), g.y(8.5f), g.x(0.8f), g.y(7.5f), pen);
                break;

            case RIBBON: {
                /* A ribbon left between the pages. */
                Path flag = new Path();
                flag.moveTo(g.x(-5.5f), g.y(-8.5f));
                flag.lineTo(g.x(5.5f), g.y(-8.5f));
                flag.lineTo(g.x(5.5f), g.y(8.5f));
                flag.lineTo(g.x(0f), g.y(4f));
                flag.lineTo(g.x(-5.5f), g.y(8.5f));
                flag.close();
                canvas.drawPath(flag, pen);
                break;
            }

            case EDIT: {
                /* A pencil, its point to the lower left. */
                Path pencil = new Path();
                pencil.moveTo(g.x(-7f), g.y(7f));
                pencil.lineTo(g.x(-6f), g.y(3f));
                pencil.lineTo(g.x(4f), g.y(-7f));
                pencil.lineTo(g.x(7f), g.y(-4f));
                pencil.lineTo(g.x(-3f), g.y(6f));
                pencil.close();
                canvas.drawPath(pencil, pen);
                canvas.drawLine(g.x(1.5f), g.y(-4.5f), g.x(4.5f), g.y(-1.5f), pen);
                break;
            }

            case CAMERA: {
                /* A body, the bump the finder sits in, and the lens: a camera, and the door to one. */
                canvas.drawRoundRect(g.box(-9f, -4.5f, 9f, 7.5f), u * 2.2f, u * 2.2f, pen);
                canvas.drawLine(g.x(-3.5f), g.y(-4.5f), g.x(-2f), g.y(-7.5f), pen);
                canvas.drawLine(g.x(-2f), g.y(-7.5f), g.x(2f), g.y(-7.5f), pen);
                canvas.drawLine(g.x(2f), g.y(-7.5f), g.x(3.5f), g.y(-4.5f), pen);
                canvas.drawCircle(g.x(0f), g.y(1.5f), u * 3.6f, pen);
                break;
            }

            case TURN: {
                /* Most of a circle, and the arrow that closes it: a quarter turn. */
                RectF ring = g.box(-7f, -7f, 7f, 7f);
                canvas.drawArc(ring, -60f, 290f, false, pen);
                float ax = g.x(3.5f);
                float ay = g.y(-6.1f);
                canvas.drawLine(ax, ay, g.x(7.5f), g.y(-8f), pen);
                canvas.drawLine(ax, ay, g.x(5.2f), g.y(-2f), pen);
                break;
            }

            case SHARE: {
                /* Three rounds joined: one thing going out to two others. */
                float r = u * 2.6f;
                canvas.drawLine(g.x(-4.5f), g.y(0f), g.x(4.5f), g.y(-6f), pen);
                canvas.drawLine(g.x(-4.5f), g.y(0f), g.x(4.5f), g.y(6f), pen);
                canvas.drawCircle(g.x(-4.5f), g.y(0f), r, pen);
                canvas.drawCircle(g.x(4.5f), g.y(-6f), r, pen);
                canvas.drawCircle(g.x(4.5f), g.y(6f), r, pen);
                break;
            }

            case CROSS:
                canvas.drawLine(g.x(-5.5f), g.y(-5.5f), g.x(5.5f), g.y(5.5f), pen);
                canvas.drawLine(g.x(5.5f), g.y(-5.5f), g.x(-5.5f), g.y(5.5f), pen);
                break;

            case PICTURE: {
                /* A frame, a ridge of hills meeting its sides, and the sun. */
                float r = u * 2.5f;
                canvas.drawRoundRect(g.box(-9f, -7f, 9f, 7f), r, r, pen);
                Path ridge = new Path();
                ridge.moveTo(g.x(-9f), g.y(4.4f));
                ridge.lineTo(g.x(-4f), g.y(-0.4f));
                ridge.lineTo(g.x(0f), g.y(3.4f));
                ridge.lineTo(g.x(3.4f), g.y(0.4f));
                ridge.lineTo(g.x(9f), g.y(4.4f));
                canvas.drawPath(ridge, pen);
                canvas.drawCircle(g.x(3.6f), g.y(-3.2f), u * 1.7f, solid);
                break;
            }

            case FILM: {
                float r = u * 2.5f;
                canvas.drawRoundRect(g.box(-9f, -7f, 9f, 7f), r, r, pen);
                Path play = new Path();
                play.moveTo(g.x(-2.4f), g.y(-3.6f));
                play.lineTo(g.x(4.2f), g.y(0f));
                play.lineTo(g.x(-2.4f), g.y(3.6f));
                play.close();
                canvas.drawPath(play, pen);
                break;
            }

            case NOTE: {
                /* A stem with its flag, and a head leaning into the beat. */
                canvas.drawLine(g.x(3.2f), g.y(4.2f), g.x(3.2f), g.y(-8f), pen);
                Path flag = new Path();
                flag.moveTo(g.x(3.2f), g.y(-8f));
                flag.quadTo(g.x(8.4f), g.y(-6.2f), g.x(7.6f), g.y(-1.8f));
                canvas.drawPath(flag, pen);
                canvas.save();
                canvas.rotate(-24f, g.x(0.2f), g.y(4.6f));
                canvas.drawOval(g.box(-3.3f, 2.2f, 3.7f, 7f), solid);
                canvas.restore();
                break;
            }

            case BOOK: {
                /* Open at the middle: two pages sagging from the spine. */
                Path pages = new Path();
                pages.moveTo(g.x(0f), g.y(-5f));
                pages.quadTo(g.x(-4.2f), g.y(-7.8f), g.x(-9f), g.y(-6.4f));
                pages.lineTo(g.x(-9f), g.y(6f));
                pages.quadTo(g.x(-4.2f), g.y(4.6f), g.x(0f), g.y(7.4f));
                pages.quadTo(g.x(4.2f), g.y(4.6f), g.x(9f), g.y(6f));
                pages.lineTo(g.x(9f), g.y(-6.4f));
                pages.quadTo(g.x(4.2f), g.y(-7.8f), g.x(0f), g.y(-5f));
                canvas.drawPath(pages, pen);
                canvas.drawLine(g.x(0f), g.y(-5f), g.x(0f), g.y(7.4f), pen);
                break;
            }

            case LOOK: {
                /* A drop of colour. */
                Path drop = new Path();
                drop.moveTo(g.x(0f), g.y(-9f));
                drop.cubicTo(g.x(3.6f), g.y(-4.6f), g.x(7f), g.y(-1f), g.x(7f), g.y(2.6f));
                drop.cubicTo(g.x(7f), g.y(6.4f), g.x(3.9f), g.y(9f), g.x(0f), g.y(9f));
                drop.cubicTo(g.x(-3.9f), g.y(9f), g.x(-7f), g.y(6.4f), g.x(-7f), g.y(2.6f));
                drop.cubicTo(g.x(-7f), g.y(-1f), g.x(-3.6f), g.y(-4.6f), g.x(0f), g.y(-9f));
                drop.close();
                canvas.drawPath(drop, pen);
                RectF shine = g.box(-3.6f, 0.2f, 3.6f, 7.4f);
                canvas.drawArc(shine, 150f, 70f, false, pen);
                break;
            }

            case LANGUAGE: {
                /* Something said, in two lines. */
                Path bubble = new Path();
                bubble.moveTo(g.x(-5f), g.y(-7f));
                bubble.lineTo(g.x(5f), g.y(-7f));
                bubble.quadTo(g.x(9f), g.y(-7f), g.x(9f), g.y(-3f));
                bubble.lineTo(g.x(9f), g.y(0.5f));
                bubble.quadTo(g.x(9f), g.y(4.5f), g.x(5f), g.y(4.5f));
                bubble.lineTo(g.x(-1f), g.y(4.5f));
                bubble.lineTo(g.x(-5f), g.y(8.5f));
                bubble.lineTo(g.x(-5f), g.y(4.5f));
                bubble.quadTo(g.x(-9f), g.y(4.5f), g.x(-9f), g.y(0.5f));
                bubble.lineTo(g.x(-9f), g.y(-3f));
                bubble.quadTo(g.x(-9f), g.y(-7f), g.x(-5f), g.y(-7f));
                bubble.close();
                canvas.drawPath(bubble, pen);
                canvas.drawLine(g.x(-4.5f), g.y(-2.6f), g.x(4.5f), g.y(-2.6f), pen);
                canvas.drawLine(g.x(-4.5f), g.y(0.6f), g.x(1.5f), g.y(0.6f), pen);
                break;
            }

            case SPARK: {
                /* Four points and four hollows: the mark of something found. */
                Path star = new Path();
                float k = 1.6f;
                star.moveTo(g.x(0f), g.y(-9f));
                star.quadTo(g.x(k), g.y(-k), g.x(9f), g.y(0f));
                star.quadTo(g.x(k), g.y(k), g.x(0f), g.y(9f));
                star.quadTo(g.x(-k), g.y(k), g.x(-9f), g.y(0f));
                star.quadTo(g.x(-k), g.y(-k), g.x(0f), g.y(-9f));
                star.close();
                canvas.drawPath(star, pen);
                break;
            }

            case BOARD: {
                /* A leaf of a calendar on its rings, and one day on it found. */
                float r = u * 2.4f;
                canvas.drawRoundRect(g.box(-8.5f, -6.5f, 8.5f, 8.5f), r, r, pen);
                canvas.drawLine(g.x(-8.5f), g.y(-2f), g.x(8.5f), g.y(-2f), pen);
                canvas.drawLine(g.x(-4f), g.y(-9f), g.x(-4f), g.y(-5f), pen);
                canvas.drawLine(g.x(4f), g.y(-9f), g.x(4f), g.y(-5f), pen);
                canvas.drawCircle(g.x(3.2f), g.y(3.6f), u * 2.1f, solid);
                break;
            }

            case CHANNEL: {
                /* A voice and the rings it sends out. */
                canvas.drawCircle(g.x(-5f), g.y(5f), u * 1.9f, solid);
                canvas.drawArc(g.box(-11f, -1f, 1f, 11f), -90f, 90f, false, pen);
                canvas.drawArc(g.box(-16.5f, -6.5f, 6.5f, 16.5f), -90f, 90f, false, pen);
                break;
            }

            case TAG:
                /* The sign a channel marks its posts with. */
                canvas.drawLine(g.x(-2f), g.y(-8f), g.x(-4.2f), g.y(8f), pen);
                canvas.drawLine(g.x(4.8f), g.y(-8f), g.x(2.6f), g.y(8f), pen);
                canvas.drawLine(g.x(-7.5f), g.y(-3f), g.x(8f), g.y(-3f), pen);
                canvas.drawLine(g.x(-8.2f), g.y(3.2f), g.x(7.3f), g.y(3.2f), pen);
                break;

            case OPEN: {
                /* A frame with an arrow leaving it: to go out to where it came from. */
                Path frame = new Path();
                frame.moveTo(g.x(-0.5f), g.y(-7.5f));
                frame.lineTo(g.x(-5.5f), g.y(-7.5f));
                frame.quadTo(g.x(-8f), g.y(-7.5f), g.x(-8f), g.y(-5f));
                frame.lineTo(g.x(-8f), g.y(5.5f));
                frame.quadTo(g.x(-8f), g.y(8f), g.x(-5.5f), g.y(8f));
                frame.lineTo(g.x(5f), g.y(8f));
                frame.quadTo(g.x(7.5f), g.y(8f), g.x(7.5f), g.y(5.5f));
                frame.lineTo(g.x(7.5f), g.y(0.5f));
                canvas.drawPath(frame, pen);
                canvas.drawLine(g.x(-1f), g.y(1f), g.x(8f), g.y(-8f), pen);
                canvas.drawLine(g.x(8f), g.y(-8f), g.x(2.5f), g.y(-8f), pen);
                canvas.drawLine(g.x(8f), g.y(-8f), g.x(8f), g.y(-2.5f), pen);
                break;
            }

            default: {
                float knob = 2.4f;
                float[] rows = {-6.5f, 0f, 6.5f};
                float[] stops = {3.5f, -3f, 2f};
                for (int i = 0; i < rows.length; i++) {
                    canvas.drawLine(g.x(-8f), g.y(rows[i]), g.x(8f), g.y(rows[i]), pen);
                    canvas.drawCircle(g.x(stops[i]), g.y(rows[i]), u * knob, cut);
                    canvas.drawCircle(g.x(stops[i]), g.y(rows[i]), u * knob, pen);
                }
                break;
            }
        }
    }

    /** A mark alone, as a picture of a given side and ink, for what cannot hold a view. */
    public static android.graphics.Bitmap picture(int kind, int ink, int side, float span) {
        android.graphics.Bitmap made = android.graphics.Bitmap.createBitmap(side, side,
            android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = side * span / 24f;
        pens(pen, solid, unit);
        pen.setColor(ink);
        solid.setColor(ink);
        cut.setColor(0x00000000);
        mark(canvas, kind, side / 2f, side / 2f, unit, pen, solid, cut);
        return made;
    }

    /** Grid units, counted from the centre, turned into pixels. */
    private static final class Grid {
        private final float cx;
        private final float cy;
        private final float u;

        Grid(float cx, float cy, float u) {
            this.cx = cx;
            this.cy = cy;
            this.u = u;
        }

        float x(float units) {
            return cx + u * units;
        }

        float y(float units) {
            return cy + u * units;
        }

        RectF box(float left, float top, float right, float bottom) {
            return new RectF(x(left), y(top), x(right), y(bottom));
        }
    }
}
