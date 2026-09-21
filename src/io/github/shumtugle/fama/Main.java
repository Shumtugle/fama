package io.github.shumtugle.fama;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The board, and the screens behind its mark.
 *
 * One window. On the stage stands the board: the day at its head as a
 * masthead, then the meetings ahead, day by day, each a card with its hour
 * in the book face, the club, the book and the place. Under the stage the
 * bar: the field that names what is in front, and the round button, which
 * on the board reads the channels again and on every other screen is the
 * way back. In the corner of the board the mark, the way into the settings:
 * the channels, the colour, the language, the journal.
 *
 * Nothing on the board has to be learned. Channels come to it from outside,
 * shared from the messenger; the reading happens by itself when the board
 * is opened and has not been read for a while, and the cards come in while
 * it is looked at.
 */
public final class Main extends Activity {

    private static final String SETTINGS = "settings";
    private static final String SOURCES = "sources";
    private static final String LOOK = "look";
    private static final String LANGUAGE = "language";
    private static final String LOG = "log";
    private static final String READ = "post";
    private static final String CARRY = "carry";
    private static final String ABOUT = "about";
    private static final String LEXICON = "lexicon";

    private static final int LOAD_MODULE = 11;
    private static final int SAVE_FORM = 12;
    private static final int SAVE_CARRY = 13;
    private static final int LOAD_CARRY = 14;

    private static final int TONAL = 0;
    private static final int OUTLINED = 1;
    private static final int PLAIN = 2;

    /** How long a board may stand unread before opening it reads it again. */
    private static final long STALE = 30L * 60L * 1000L;

    private final Handler clock = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private FrameLayout stage;
    private Crest crest;
    private LinearLayout bar;
    private LinearLayout field;
    private Glyph hereMark;
    private TextView hereWord;
    private EditText ask;
    private Blob blob;
    private View scene;
    private ScrollView scroller;
    private LinearLayout note;

    /** The screens open over the board, the last in front. */
    private final ArrayList<String> trail = new ArrayList<String>();
    /** The meeting being read, and whose it is. */
    private Sift.Bill held;
    private String heldTitle = "";
    /** Where the board stood when a screen was opened over it. */
    private int boardY;

    /** Words that wear a role's colour, to be coloured again when the seed changes. */
    private final ArrayList<TextView> inked = new ArrayList<TextView>();
    private final ArrayList<Integer> inkRoles = new ArrayList<Integer>();
    private final ArrayList<TextView> swatches = new ArrayList<TextView>();
    private final ArrayList<int[]> swatchRoles = new ArrayList<int[]>();
    private Dial hueDial;
    private Dial richDial;
    private LinearLayout wallChip;
    private Glyph wallMark;
    private TextView wallWord;

    /** How the round of the channels goes, as last heard. */
    private boolean reading;
    private int readDone;
    private int readCount;
    /** The meetings already on the board, so that only the new ones are brought in. */
    private final HashSet<String> shown = new HashSet<String>();
    /** The change of the board the board on the screen was drawn from. */
    private int drawnFrom = -1;

    /** The channel whose tag the field is taking, or none. */
    private String asking;
    /** Whether the field is taking a new channel, by its link or its name. */
    private boolean adding;
    /** The field is taking a word of one's own, for this kind; or nothing. */
    private String wording;
    /** The meetings on the board before the words changed, to say afterwards what the change did. */
    private HashSet<String> wordsBefore;
    /** The words changed while a reading was under way: read once more when it ends. */
    private boolean wordsAgain;
    /** The channel armed to be removed by a second touch, and when it was armed. */
    private String arming;
    private long armingAt;

    private Bloom bloom;
    private boolean bloomFollow;
    private boolean fingerDown;
    private float downRawX;
    private float downRawY;

    // ------------------------------------------------------------------ life

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Trace.watch(getApplicationContext());
        Round.measure(this);
        Tone.read(this);
        Words.load(this);
        Ways.ready(this);
        build();
        swap(boardView(false));
        Crier.listen(heard);
        if (Crier.busy()) {
            reading = true;
            blob.busy(true);
        }
        Trace.note("started, " + Board.sources(this).size() + " channels");
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* A look carried in while the board stood behind grows into every colour now. */
        if (Carry.relook) {
            Carry.relook = false;
            Tone.read(this);
            retone();
        }
        /* A channel shared while the board stood behind is on it now. */
        if (trail.isEmpty() && !typing() && bloom == null && scene != null
            && drawnFrom != Board.version()) {
            refreshBoard();
        }
        readIfStale();
    }

    @Override
    protected void onPause() {
        Trace.keep(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Crier.listen(null);
        clock.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void readIfStale() {
        ArrayList<Board.Source> sources = Board.sources(this);
        if (sources.isEmpty() || Crier.busy()) {
            return;
        }
        /* A channel never read, or one that did not answer ten minutes ago, is read now. */
        boolean waiting = false;
        long now = System.currentTimeMillis();
        for (int i = 0; i < sources.size() && !waiting; i++) {
            Board.Source s = sources.get(i);
            waiting = s.state == Board.WAIT
                || s.state == Board.SILENT && now - s.readAt > 10L * 60L * 1000L;
        }
        if (waiting || now - Keep.readAt(this) > STALE) {
            Crier.round(this, null);
        }
    }

    private final Crier.Heard heard = new Crier.Heard() {
        public void began(int count) {
            reading = true;
            readDone = 0;
            readCount = count;
            blob.busy(true);
            plate(false);
        }

        public void read(int done, int count, String name) {
            readDone = done;
            readCount = count;
            plate(false);
            afterReading();
        }

        public void ended(int count) {
            reading = false;
            blob.busy(false);
            plate(true);
            afterReading();
            wordsRead();
        }
    };

    /** A channel read: the screen that shows its fruit is drawn again. */
    private void afterReading() {
        if (trail.isEmpty() && !typing() && bloom == null) {
            refreshBoard();
        } else if (SOURCES.equals(front()) && !typing()) {
            redrawSources();
        }
    }

    // ------------------------------------------------------------------ build

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        stage = new FrameLayout(this);
        stage.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View view, Outline shape) {
                shape.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Round.px(Round.XL));
            }
        });
        stage.setClipToOutline(true);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        stageParams.setMargins(Round.dp(6), Round.dp(4), Round.dp(6), 0);
        root.addView(stage, stageParams);

        crest = new Crest(this, Round.px(56f));
        crest.setContentDescription(Words.s("settings"));
        crest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bloom == null && trail.isEmpty()) {
                    door(SETTINGS);
                }
            }
        });
        FrameLayout.LayoutParams crestParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.END);
        crestParams.setMargins(0, Round.dp(12), 0, 0);
        crestParams.setMarginEnd(Round.dp(12));
        stage.addView(crest, crestParams);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Round.dp(6), Round.dp(8), Round.dp(8), Round.dp(8));

        /* Where an address would stand: what is in front, or how the reading goes. */
        field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(Round.dp(16), 0, Round.dp(8), 0);
        field.setMinimumHeight(Round.dp(56));
        field.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (typing()) {
                    keyboard(true);
                } else if (trail.isEmpty() && scroller != null) {
                    scroller.smoothScrollTo(0, 0);
                }
            }
        });
        hereMark = new Glyph(this, Glyph.BOARD, Round.px(24f), 0.92f,
            0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams hereMarkParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hereMarkParams.rightMargin = Round.dp(14);
        field.addView(hereMark, hereMarkParams);
        hereWord = Letter.set(new TextView(this), Letter.BODY_L);
        hereWord.setSingleLine(true);
        hereWord.setEllipsize(android.text.TextUtils.TruncateAt.END);
        field.addView(hereWord, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        /* The same field takes words: a channel's tag. */
        ask = new EditText(this);
        Letter.set(ask, Letter.BODY_L);
        ask.setSingleLine(true);
        ask.setBackground(null);
        ask.setPadding(0, 0, 0, 0);
        ask.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ask.setVisibility(View.GONE);
        ask.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int action, android.view.KeyEvent e) {
                if (wording != null) {
                    commitWord();
                } else if (adding) {
                    commitAdd();
                } else {
                    commitTag();
                }
                return true;
            }
        });
        field.addView(ask, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(field, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        blob = new Blob(this, Round.px(56f), Tone.of(Tone.PRIMARY_CONTAINER),
            Tone.of(Tone.ON_PRIMARY_CONTAINER));
        blob.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bloom != null) {
                    bloom.dismiss();
                } else if (wording != null) {
                    commitWord();
                } else if (adding) {
                    commitAdd();
                } else if (asking != null) {
                    commitTag();
                } else if (!trail.isEmpty()) {
                    back();
                } else if (!Crier.busy()) {
                    Crier.round(Main.this, null);
                }
            }
        });
        LinearLayout.LayoutParams blobParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blobParams.leftMargin = Round.dp(4);
        bar.addView(blob, blobParams);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.setMargins(Round.dp(8), Round.dp(8), Round.dp(8), Round.dp(10));
        root.addView(bar, barParams);

        retone();
        plate(false);
        setContentView(root);
        fitBars(root);
    }

    /** Keeps the application's own edges clear of the phone's bars. */
    private void fitBars(final View view) {
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
        view.requestApplyInsets();
    }

    /** Every lasting part in the colours of the moment. */
    private void retone() {
        int ground = Tone.of(Tone.SURFACE_LOWEST);
        root.setBackgroundColor(ground);
        getWindow().setStatusBarColor(ground);
        getWindow().setNavigationBarColor(ground);
        stage.setBackground(Round.box(Tone.of(Tone.SURFACE_LOW), Round.XL));
        bar.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        field.setBackground(Round.touch(null, Tone.of(Tone.ON_SURFACE), Round.FULL));
        hereWord.setTextColor(Tone.of(Tone.ON_SURFACE));
        ask.setTextColor(Tone.of(Tone.ON_SURFACE));
        ask.setHintTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        hereMark.tint(0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
        crest.tint();
        blob.tint(Tone.of(Tone.PRIMARY_CONTAINER), Tone.of(Tone.ON_PRIMARY_CONTAINER));
        for (int i = 0; i < inked.size(); i++) {
            inked.get(i).setTextColor(Tone.of(inkRoles.get(i)));
        }
        paintLook();
    }

    // ------------------------------------------------------------------ the field

    /** The field says what is in front: the board, how the reading goes, or a screen's name. */
    private void plate(boolean moving) {
        String screen = front();
        final String word;
        if (screen == null) {
            word = reading && readCount > 0
                ? Words.s("reading_n").replace("{n}", String.valueOf(Math.min(readCount, readDone + 1)))
                    .replace("{m}", String.valueOf(readCount))
                : Words.s("board");
        } else {
            word = Words.s(screen);
        }
        final int kind = screen == null ? Glyph.BOARD : SOURCES.equals(screen) ? Glyph.CHANNEL
            : LOOK.equals(screen) ? Glyph.LOOK : LANGUAGE.equals(screen) ? Glyph.LANGUAGE
            : LOG.equals(screen) ? Glyph.CARET : READ.equals(screen) ? Glyph.BOOK
            : CARRY.equals(screen) ? Glyph.SHARE : ABOUT.equals(screen) ? Glyph.RIBBON
            : LEXICON.equals(screen) ? Glyph.EDIT : Glyph.SETTINGS;
        hereWord.animate().cancel();
        hereMark.animate().cancel();
        if (!moving) {
            hereWord.setText(word);
            hereMark.kind(kind);
            hereWord.setAlpha(1f);
            hereMark.setAlpha(1f);
            hereWord.setTranslationY(0f);
            hereMark.setTranslationY(0f);
            return;
        }
        hereMark.animate().alpha(0f).translationY(-Round.px(8f)).setStartDelay(0L)
            .setDuration(Pace.PRESS).setInterpolator(Pace.AWAY).start();
        hereWord.animate().alpha(0f).translationY(-Round.px(8f)).setStartDelay(0L)
            .setDuration(Pace.PRESS).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    hereWord.setText(word);
                    hereMark.kind(kind);
                    hereWord.setTranslationY(Round.px(10f));
                    hereMark.setTranslationY(Round.px(10f));
                    hereWord.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
                        .setInterpolator(Pace.STANDARD).start();
                    hereMark.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
                        .setInterpolator(Pace.STANDARD).start();
                }
            }).start();
    }

    /** The mark stands over the board only, and steps aside for the screens it leads to. */
    private void placeCrest() {
        final boolean want = trail.isEmpty();
        crest.animate().cancel();
        if (want) {
            crest.setVisibility(View.VISIBLE);
            crest.setAlpha(0f);
            crest.setScaleX(0.8f);
            crest.setScaleY(0.8f);
            crest.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(70L)
                .setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
        } else {
            crest.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setStartDelay(0L)
                .setDuration(Pace.PRESS).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                    public void run() {
                        crest.setVisibility(View.GONE);
                    }
                }).start();
        }
        crest.bringToFront();
    }

    private void keyboard(boolean up) {
        InputMethodManager keys = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keys == null) {
            return;
        }
        if (up) {
            ask.requestFocus();
            keys.showSoftInput(ask, InputMethodManager.SHOW_IMPLICIT);
        } else {
            keys.hideSoftInputFromWindow(ask.getWindowToken(), 0);
            ask.clearFocus();
        }
    }

    // ------------------------------------------------------------------ the board

    /** The channels drawn again where they stand. */
    private void redrawSources() {
        final int y = scroller == null ? 0 : scroller.getScrollY();
        forgetScene();
        replace(sourcesView());
        if (y > 0 && scroller != null) {
            final ScrollView now = scroller;
            now.post(new Runnable() {
                public void run() {
                    now.scrollTo(0, y);
                }
            });
        }
    }

    /** The board drawn again where it stands, keeping its place, bringing in only what is new. */
    private void refreshBoard() {
        final int y = scroller == null ? 0 : scroller.getScrollY();
        forgetScene();
        replace(boardView(true));
        if (y > 0 && scroller != null) {
            final ScrollView now = scroller;
            now.post(new Runnable() {
                public void run() {
                    now.scrollTo(0, y);
                }
            });
        }
    }

    /**
     * The board. At its head the day of today as a masthead in the book face,
     * the board's name, and a line on how much is ahead and when it was read;
     * then the meetings, parted by day.
     */
    private View boardView(boolean live) {
        LinearLayout column = column();
        Calendar now = Calendar.getInstance();
        drawnFrom = Board.version();

        String masthead = (dayName(now) + ", " + now.get(Calendar.DAY_OF_MONTH) + " "
            + Words.s("month_" + (now.get(Calendar.MONTH) + 1))).toUpperCase(Locale.getDefault());
        TextView head = Letter.serif(words(Letter.LABEL_L, masthead, Tone.PRIMARY));
        head.setLetterSpacing(0.14f);
        head.setPadding(0, 0, Round.dp(64), 0);
        column.addView(head);

        TextView name = words(Letter.DISPLAY_S, Words.s("board"), Tone.ON_SURFACE);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(6);
        column.addView(name, nameParams);

        ArrayList<Board.Source> sources = Board.sources(this);
        ArrayList<Sift.Bill> bills = Board.bills(this);
        Map<String, String> titles = Board.titles(this);

        long readAt = Keep.readAt(this);
        String said;
        if (readAt == 0L && reading) {
            said = Words.s("first_read");
        } else {
            said = Words.s("ahead_n").replace("{n}", String.valueOf(bills.size()));
            if (readAt > 0L) {
                said += "  \u00B7  " + Words.s("read_at").replace("{t}", clockOf(readAt));
            }
        }
        if (!sources.isEmpty()) {
            TextView line = words(Letter.BODY_M, said, Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams lineParams = wide();
            lineParams.topMargin = Round.dp(4);
            column.addView(line, lineParams);
        }

        if (sources.isEmpty() || bills.isEmpty() && !(readAt == 0L && reading)) {
            boolean none = sources.isEmpty();
            Crest mark = new Crest(this, Round.px(none ? 176f : 128f));
            mark.setClickable(false);
            mark.setBackground(null);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            markParams.gravity = Gravity.CENTER_HORIZONTAL;
            markParams.topMargin = Round.dp(none ? 56 : 40);
            column.addView(mark, markParams);
            TextView title = Letter.serif(words(Letter.TITLE_L,
                Words.s(none ? "empty_title" : "quiet_title"), Tone.ON_SURFACE));
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams titleParams = wide();
            titleParams.topMargin = Round.dp(32);
            column.addView(title, titleParams);
            TextView what = words(Letter.BODY_M, Words.s(none ? "empty_what" : "quiet_what"),
                Tone.ON_SURFACE_VARIANT);
            what.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams whatParams = wide();
            whatParams.topMargin = Round.dp(8);
            whatParams.leftMargin = Round.dp(12);
            whatParams.rightMargin = Round.dp(12);
            column.addView(what, whatParams);
            if (none) {
                LinearLayout.LayoutParams addParams = buttonPlace(24);
                addParams.gravity = Gravity.CENTER_HORIZONTAL;
                column.addView(button(Words.s("add_channel"), TONAL, new View.OnClickListener() {
                    public void onClick(View v) {
                        door(SOURCES);
                        clock.postDelayed(new Runnable() {
                            public void run() {
                                askAdd();
                            }
                        }, Pace.ARRIVE);
                    }
                }), addParams);
            }
            shown.clear();
            return scroll(column, !live);
        }

        int day = -1;
        ArrayList<View> fresh = new ArrayList<View>();
        HashSet<String> now2 = new HashSet<String>();
        for (int i = 0; i < bills.size(); i++) {
            Sift.Bill b = bills.get(i);
            if (b.day != day) {
                day = b.day;
                LinearLayout.LayoutParams dayParams = wide();
                dayParams.topMargin = Round.dp(i == 0 ? 28 : 32);
                column.addView(dayHead(b.day, now), dayParams);
            }
            String title = b.source.length() == 0 ? Words.s("by_hand")
                : titles.containsKey(b.source) ? titles.get(b.source) : b.source;
            View card = billCard(b, title);
            LinearLayout.LayoutParams cardParams = wide();
            cardParams.topMargin = Round.dp(8);
            column.addView(card, cardParams);
            now2.add(b.key());
            if (live && !shown.contains(b.key())) {
                fresh.add(card);
            }
        }
        shown.clear();
        shown.addAll(now2);
        for (int i = 0; i < fresh.size(); i++) {
            View v = fresh.get(i);
            v.setAlpha(0f);
            v.setTranslationY(Round.px(18f));
            v.setScaleX(0.97f);
            v.setScaleY(0.97f);
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(70L + i * Pace.STAGGER).setDuration(Pace.ARRIVE)
                .setInterpolator(Pace.STANDARD).start();
        }
        return scroll(column, !live);
    }

    /** A day's head: its name in the book face, and the date beside it, quieter. */
    private View dayHead(int stamp, Calendar now) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        Calendar c = Days.calendar(stamp);
        int today = Days.stamp(now);
        Calendar next = (Calendar) now.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        String name = stamp == today ? Words.s("today")
            : stamp == Days.stamp(next) ? Words.s("tomorrow") : dayName(c);
        TextView called = Letter.serif(words(Letter.TITLE_L, name, Tone.ON_SURFACE));
        row.addView(called);
        String date = c.get(Calendar.DAY_OF_MONTH) + " " + Words.s("month_" + (c.get(Calendar.MONTH) + 1));
        if (c.get(Calendar.YEAR) != now.get(Calendar.YEAR)) {
            date += " " + c.get(Calendar.YEAR);
        }
        TextView when = words(Letter.LABEL_L, date, Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whenParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        whenParams.leftMargin = Round.dp(10);
        whenParams.bottomMargin = Round.dp(3);
        row.addView(when, whenParams);
        row.setPadding(Round.dp(4), 0, 0, 0);
        return row;
    }

    private static String dayName(Calendar c) {
        int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        return Words.s("day_" + dow);
    }

    private static String hourOf(int minutes) {
        return minutes / 60 + ":" + (minutes % 60 < 10 ? "0" : "") + minutes % 60;
    }

    private static String clockOf(long when) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        return hourOf(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
    }

    /**
     * One meeting: the hour in the book face at the left, as a timetable sets
     * it; the club, the book in its quotes, the place; the channel's face at
     * the right. A meeting called off is crossed through and quieter, and
     * says so in the colour of an error; a moved one says so in the third
     * accent.
     */
    private View billCard(final Sift.Bill b, final String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(Round.dp(16), Round.dp(14), Round.dp(14), Round.dp(14));
        card.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));

        TextView hour = Letter.serif(words(Letter.TITLE_L,
            b.minutes >= 0 ? hourOf(b.minutes) : "\u2014", b.minutes >= 0 ? Tone.PRIMARY : Tone.OUTLINE));
        hour.setSingleLine(true);
        card.addView(hour, new LinearLayout.LayoutParams(Round.dp(66),
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView who = words(Letter.TITLE_M, title, Tone.ON_SURFACE);
        who.setSingleLine(true);
        who.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(who);
        TextView book = null;
        if (b.book.length() > 0) {
            book = Letter.serif(words(Letter.BODY_L, "\u00AB" + b.book + "\u00BB", Tone.ON_SURFACE));
            book.setMaxLines(2);
            book.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bookParams = wide();
            bookParams.topMargin = Round.dp(4);
            said.addView(book, bookParams);
        } else if (b.line.length() > 0) {
            TextView gist = words(Letter.BODY_M, b.line, Tone.ON_SURFACE_VARIANT);
            gist.setMaxLines(2);
            gist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams gistParams = wide();
            gistParams.topMargin = Round.dp(4);
            said.addView(gist, gistParams);
        }
        if (b.place.length() > 0) {
            TextView place = words(Letter.BODY_S, b.place, Tone.ON_SURFACE_VARIANT);
            place.setSingleLine(true);
            place.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams placeParams = wide();
            placeParams.topMargin = Round.dp(4);
            said.addView(place, placeParams);
        }
        if (b.cancelled || b.moved) {
            TextView flag = Letter.set(new TextView(this), Letter.LABEL_M);
            flag.setText(Words.s(b.cancelled ? "cancelled" : "moved"));
            flag.setTextColor(b.cancelled ? Bloom.errorInk() : Tone.of(Tone.TERTIARY));
            LinearLayout.LayoutParams flagParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flagParams.topMargin = Round.dp(6);
            said.addView(flag, flagParams);
        }
        if (b.cancelled) {
            who.setPaintFlags(who.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            if (book != null) {
                book.setPaintFlags(book.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
            hour.setPaintFlags(hour.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            hour.setAlpha(0.6f);
            said.setAlpha(0.72f);
        }
        card.addView(said, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Faces.Disc face = new Faces.Disc(this, b.source, title, Round.px(36f));
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        faceParams.leftMargin = Round.dp(12);
        card.addView(face, faceParams);

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (bloom == null && trail.isEmpty()) {
                    held = b;
                    heldTitle = title;
                    door(READ);
                }
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                bloom(billRows(b, title));
                return true;
            }
        });
        return card;
    }

    /** What can be done with a meeting held under the finger. */
    private ArrayList<ArrayList<Bloom.Row>> billRows(final Sift.Bill b, final String title) {
        ArrayList<ArrayList<Bloom.Row>> groups = new ArrayList<ArrayList<Bloom.Row>>();
        ArrayList<Bloom.Row> go = new ArrayList<Bloom.Row>();
        if (b.source.length() > 0) {
            go.add(new Bloom.Row(Glyph.OPEN, Words.s("open_post"), null, false, new Bloom.Act() {
                public boolean act(Bloom.Row row, Bloom menu) {
                    open(Ways.of("post", b.source, b.post));
                    return true;
                }
            }));
            go.add(new Bloom.Row(Glyph.CHANNEL, Words.s("open_channel"), null, false, new Bloom.Act() {
                public boolean act(Bloom.Row row, Bloom menu) {
                    open(Ways.of("channel", b.source, 0));
                    return true;
                }
            }));
        }
        go.add(new Bloom.Row(Glyph.SHARE, Words.s("share"), null, false, new Bloom.Act() {
            public boolean act(Bloom.Row row, Bloom menu) {
                share(b, title);
                return true;
            }
        }));
        groups.add(go);
        ArrayList<Bloom.Row> away = new ArrayList<Bloom.Row>();
        away.add(new Bloom.Row(Glyph.CROSS, Words.s("hide"), null, true, new Bloom.Act() {
            public boolean act(Bloom.Row row, Bloom menu) {
                Board.hide(Main.this, b);
                Trace.note("board: a meeting taken off by hand");
                clock.postDelayed(new Runnable() {
                    public void run() {
                        refreshBoard();
                        flash(Glyph.CROSS, Words.s("hidden"));
                    }
                }, Pace.LEAVE + 40L);
                return true;
            }
        }));
        groups.add(away);
        return groups;
    }

    private void open(String address) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
        } catch (ActivityNotFoundException nobody) {
            Trace.note("open: nothing on the phone opens that");
        }
    }

    /** A meeting told in plain words, to send to someone. */
    private void share(Sift.Bill b, String title) {
        StringBuilder t = new StringBuilder();
        Calendar c = Days.calendar(b.day);
        t.append(title).append('\n');
        t.append(dayName(c)).append(", ").append(c.get(Calendar.DAY_OF_MONTH)).append(' ')
            .append(Words.s("month_" + (c.get(Calendar.MONTH) + 1)));
        if (b.minutes >= 0) {
            t.append(", ").append(hourOf(b.minutes));
        }
        t.append('\n');
        if (b.book.length() > 0) {
            t.append('\u00AB').append(b.book).append('\u00BB').append('\n');
        }
        if (b.place.length() > 0) {
            t.append(b.place).append('\n');
        }
        if (b.source.length() > 0) {
            t.append(Ways.of("post", b.source, b.post));
        } else if (b.line.length() > 0) {
            t.append(b.line);
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, t.toString().trim());
        try {
            startActivity(Intent.createChooser(send, null));
        } catch (Exception none) {
            Trace.note("share: nothing to share with");
        }
    }

    // ------------------------------------------------------------------ reading an announcement

    /**
     * One announcement read here, in the board's own dress. At the head the
     * club in the masthead's spaced capitals, its face beside it where the
     * mark stands on the board; the day large in the book face; the date and
     * the hour; the book and the place; what can be done with the meeting.
     * Then the words as the channel wrote them, its links in the board's
     * colour, and a later notice that called the meeting off or moved it
     * set apart above them.
     */
    private View readerView() {
        final Sift.Bill b = held;
        final String title = heldTitle;
        LinearLayout column = column();
        if (b == null) {
            return scroll(column, true);
        }
        Calendar now = Calendar.getInstance();
        Calendar c = Days.calendar(b.day);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView club = Letter.serif(words(Letter.LABEL_L, title.toUpperCase(Locale.getDefault()),
            Tone.PRIMARY));
        club.setLetterSpacing(0.14f);
        club.setMaxLines(2);
        club.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(club, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Faces.Disc face = new Faces.Disc(this, b.source, title, Round.px(48f));
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        faceParams.leftMargin = Round.dp(16);
        head.addView(face, faceParams);
        column.addView(head, wide());

        int today = Days.stamp(now);
        Calendar next = (Calendar) now.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        String dayWord = b.day == today ? Words.s("today")
            : b.day == Days.stamp(next) ? Words.s("tomorrow") : dayName(c);
        TextView day = words(Letter.DISPLAY_S, dayWord, Tone.ON_SURFACE);
        LinearLayout.LayoutParams dayParams = wide();
        dayParams.topMargin = Round.dp(10);
        column.addView(day, dayParams);

        String date = c.get(Calendar.DAY_OF_MONTH) + " " + Words.s("month_" + (c.get(Calendar.MONTH) + 1));
        if (c.get(Calendar.YEAR) != now.get(Calendar.YEAR)) {
            date += " " + c.get(Calendar.YEAR);
        }
        String hour = b.minutes >= 0 ? hourOf(b.minutes) : "";
        SpannableString when = new SpannableString(hour.length() > 0 ? date + "  \u00B7  " + hour : date);
        if (hour.length() > 0) {
            when.setSpan(new ForegroundColorSpan(Tone.of(Tone.PRIMARY)), when.length() - hour.length(),
                when.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView whenView = Letter.serif(words(Letter.TITLE_L, "", Tone.ON_SURFACE_VARIANT));
        whenView.setText(when);
        LinearLayout.LayoutParams whenParams = wide();
        whenParams.topMargin = Round.dp(2);
        column.addView(whenView, whenParams);

        if (b.cancelled || b.moved) {
            TextView flag = Letter.set(new TextView(this), Letter.LABEL_L);
            flag.setText(Words.s(b.cancelled ? "cancelled" : "moved"));
            flag.setTextColor(b.cancelled ? Bloom.errorInk() : Tone.of(Tone.TERTIARY));
            LinearLayout.LayoutParams flagParams = wide();
            flagParams.topMargin = Round.dp(8);
            column.addView(flag, flagParams);
        }
        if (b.cancelled) {
            day.setPaintFlags(day.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            whenView.setPaintFlags(whenView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            day.setAlpha(0.6f);
            whenView.setAlpha(0.72f);
        }

        if (b.book.length() > 0) {
            TextView book = words(Letter.HEADLINE_S, "\u00AB" + b.book + "\u00BB", Tone.ON_SURFACE);
            LinearLayout.LayoutParams bookParams = wide();
            bookParams.topMargin = Round.dp(20);
            column.addView(book, bookParams);
        }
        if (b.place.length() > 0) {
            TextView place = words(Letter.BODY_L, b.place, Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams placeParams = wide();
            placeParams.topMargin = Round.dp(b.book.length() > 0 ? 6 : 20);
            column.addView(place, placeParams);
        }

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        if (!b.cancelled) {
            acts.addView(button(Words.s("to_calendar"), TONAL, new View.OnClickListener() {
                public void onClick(View v) {
                    toCalendar(b, title);
                }
            }), buttonPlace(0));
        }
        LinearLayout.LayoutParams shareParams = buttonPlace(0);
        shareParams.leftMargin = b.cancelled ? 0 : Round.dp(8);
        acts.addView(button(Words.s("share"), OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                share(b, title);
            }
        }), shareParams);
        LinearLayout.LayoutParams actsParams = wide();
        actsParams.topMargin = Round.dp(24);
        column.addView(acts, actsParams);
        if (b.source.length() > 0) {
            column.addView(button(Words.s("open_post"), OUTLINED, new View.OnClickListener() {
                public void onClick(View v) {
                    open(Ways.of("post", b.source, b.post));
                }
            }), buttonPlace(8));
        }

        if (b.note.length() > 0) {
            LinearLayout notice = new LinearLayout(this);
            notice.setOrientation(LinearLayout.VERTICAL);
            notice.setPadding(Round.dp(16), Round.dp(14), Round.dp(16), Round.dp(16));
            notice.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.L));
            TextView said = Letter.set(new TextView(this), Letter.LABEL_L);
            said.setText(Words.s(b.cancelled ? "cancelled" : "moved"));
            said.setTextColor(b.cancelled ? Bloom.errorInk() : Tone.of(Tone.TERTIARY));
            notice.addView(said);
            TextView noteText = words(Letter.BODY_M, "", Tone.ON_SURFACE);
            noteText.setText(linked(b.note, new ArrayList<String[]>(), null));
            readable(noteText);
            LinearLayout.LayoutParams noteTextParams = wide();
            noteTextParams.topMargin = Round.dp(6);
            notice.addView(noteText, noteTextParams);
            LinearLayout.LayoutParams noticeParams = wide();
            noticeParams.topMargin = Round.dp(28);
            column.addView(notice, noticeParams);
        }

        View rule = new View(this);
        rule.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Round.dp(1)));
        ruleParams.topMargin = Round.dp(28);
        column.addView(rule, ruleParams);

        if (b.posted > 0L) {
            Calendar p = Calendar.getInstance();
            p.setTimeInMillis(b.posted);
            String on = p.get(Calendar.DAY_OF_MONTH) + " " + Words.s("month_" + (p.get(Calendar.MONTH) + 1));
            if (p.get(Calendar.YEAR) != now.get(Calendar.YEAR)) {
                on += " " + p.get(Calendar.YEAR);
            }
            TextView posted = words(Letter.LABEL_M, Words.s("posted_on").replace("{d}", on),
                Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams postedParams = wide();
            postedParams.topMargin = Round.dp(16);
            column.addView(posted, postedParams);
        }

        ArrayList<String[]> loose = new ArrayList<String[]>();
        TextView body = words(Letter.BODY_L, "", Tone.ON_SURFACE);
        if (b.text.length() > 0) {
            body.setText(linked(b.text, b.links, loose));
        } else {
            body.setText(b.line);
        }
        readable(body);
        LinearLayout.LayoutParams bodyParams = wide();
        bodyParams.topMargin = Round.dp(10);
        column.addView(body, bodyParams);
        if (b.text.length() == 0) {
            TextView later = words(Letter.BODY_S, Words.s("no_words"), Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams laterParams = wide();
            laterParams.topMargin = Round.dp(12);
            column.addView(later, laterParams);
        }

        /* Links whose words could not be found in the text stand under it, one to a line. */
        if (!loose.isEmpty()) {
            TextView named = words(Letter.LABEL_L, Words.s("links"), Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams namedParams = wide();
            namedParams.topMargin = Round.dp(24);
            column.addView(named, namedParams);
            for (int i = 0; i < loose.size(); i++) {
                final String[] one = loose.get(i);
                TextView link = words(Letter.BODY_L, one[0], Tone.PRIMARY);
                link.setPadding(0, Round.dp(8), 0, Round.dp(8));
                link.setBackground(Round.touch(null, Tone.of(Tone.PRIMARY), Round.S));
                link.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        open(one[1]);
                    }
                });
                column.addView(link, wide());
            }
        }
        return scroll(column, true);
    }

    /** Words set for reading: room between the lines, links that answer the finger. */
    private void readable(TextView view) {
        view.setLineSpacing(0f, 1.32f);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(Tone.of(Tone.PRIMARY) & 0x33FFFFFF);
        view.setLinksClickable(true);
    }

    /**
     * An announcement's words with its links made live: the addresses written
     * out in it, and the links it hid under words, each on the first place
     * those words stand that is not already a link. What cannot be placed is
     * handed back to be shown apart.
     */
    private SpannableString linked(String text, ArrayList<String[]> hidden, ArrayList<String[]> loose) {
        SpannableString s = new SpannableString(text);
        Linkify.addLinks(s, Linkify.WEB_URLS);
        URLSpan[] written = s.getSpans(0, s.length(), URLSpan.class);
        for (int i = 0; i < written.length; i++) {
            int start = s.getSpanStart(written[i]);
            int end = s.getSpanEnd(written[i]);
            String url = written[i].getURL();
            s.removeSpan(written[i]);
            s.setSpan(new Lead(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int i = 0; i < hidden.size(); i++) {
            String words = hidden.get(i)[0];
            int at = text.indexOf(words);
            while (at >= 0 && s.getSpans(at, at + words.length(), Lead.class).length > 0) {
                at = text.indexOf(words, at + 1);
            }
            if (at >= 0) {
                s.setSpan(new Lead(hidden.get(i)[1]), at, at + words.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (loose != null) {
                loose.add(hidden.get(i));
            }
        }
        return s;
    }

    /** A link in the words: in the board's colour, and opened the way every address here is. */
    private final class Lead extends ClickableSpan {
        private final String url;

        Lead(String url) {
            this.url = url;
        }

        @Override
        public void onClick(View widget) {
            open(url);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setColor(Tone.of(Tone.PRIMARY));
            paint.setUnderlineText(true);
        }
    }

    /**
     * The meeting handed to the phone's calendar, filled in and waiting for
     * a yes there: the club and the book, the hour and two after it, the
     * place, and the way back to the announcement. A day without an hour
     * goes in as the whole day.
     */
    private void toCalendar(Sift.Bill b, String title) {
        Calendar c = Days.calendar(b.day);
        Intent add = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
        add.putExtra(CalendarContract.Events.TITLE,
            b.book.length() > 0 ? title + " \u00B7 \u00AB" + b.book + "\u00BB" : title);
        if (b.minutes >= 0) {
            c.set(Calendar.HOUR_OF_DAY, b.minutes / 60);
            c.set(Calendar.MINUTE, b.minutes % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long begin = c.getTimeInMillis();
            add.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
            add.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 2L * 60L * 60L * 1000L);
        } else {
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            add.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true);
            add.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, c.getTimeInMillis());
            add.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, c.getTimeInMillis() + 24L * 60L * 60L * 1000L);
        }
        if (b.place.length() > 0) {
            add.putExtra(CalendarContract.Events.EVENT_LOCATION, b.place);
        }
        StringBuilder about = new StringBuilder();
        if (b.source.length() > 0) {
            about.append(Ways.of("post", b.source, b.post)).append("\n\n");
        }
        about.append(b.text.length() > 0 ? Sift.keep(b.text, 2000) : b.line);
        add.putExtra(CalendarContract.Events.DESCRIPTION, about.toString().trim());
        try {
            startActivity(add);
            Trace.note("calendar: a meeting handed over");
        } catch (ActivityNotFoundException nobody) {
            Trace.note("calendar: no calendar on the phone takes it");
        }
    }

    // ------------------------------------------------------------------ carrying the board

    /**
     * The board as one text, to take elsewhere: handed to the phone's sharing,
     * which reaches this application on another phone or another application
     * on this one, or kept in a file; and a file of it, or of any list of
     * channels, taken in here.
     */
    private View carryView() {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("carry"), Tone.ON_SURFACE));
        TextView about = words(Letter.BODY_M, Words.s("carry_about"), Tone.ON_SURFACE_VARIANT);
        about.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams aboutParams = wide();
        aboutParams.topMargin = Round.dp(8);
        column.addView(about, aboutParams);

        int kept = Board.kept(this).size();
        int hidden = Board.hidden(this).size();
        TextView count = words(Letter.LABEL_L, Words.s("carry_n")
            .replace("{c}", String.valueOf(Board.sources(this).size()))
            .replace("{b}", String.valueOf(kept)).replace("{h}", String.valueOf(hidden)), Tone.PRIMARY);
        LinearLayout.LayoutParams countParams = wide();
        countParams.topMargin = Round.dp(20);
        column.addView(count, countParams);

        column.addView(button(Words.s("share"), TONAL, new View.OnClickListener() {
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, Carry.write(Main.this));
                try {
                    startActivity(Intent.createChooser(send, null));
                } catch (Exception none) {
                    Trace.note("carry: nothing to share with");
                }
            }
        }), buttonPlace(24));
        column.addView(button(Words.s("carry_save"), OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                Intent make = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                make.addCategory(Intent.CATEGORY_OPENABLE);
                make.setType("text/plain");
                make.putExtra(Intent.EXTRA_TITLE, "board.txt");
                try {
                    startActivityForResult(make, SAVE_CARRY);
                } catch (ActivityNotFoundException nobody) {
                    Trace.note("carry: no place to save a file");
                }
            }
        }), buttonPlace(8));
        column.addView(button(Words.s("carry_load"), OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(pick, LOAD_CARRY);
                } catch (ActivityNotFoundException nobody) {
                    Trace.note("carry: no picker of files");
                }
            }
        }), buttonPlace(8));
        return scroll(column, true);
    }

    /** A file taken in: a carried board, or any text that names channels. */
    private void takeIn(String text) {
        List<String> came;
        if (Carry.is(text)) {
            Carry.Taken taken = Carry.apply(this, Carry.read(text));
            came = taken.channels;
            if (Carry.relook) {
                Carry.relook = false;
                Tone.read(this);
                retone();
            }
        } else {
            ArrayList<String> named = Ways.channels(text);
            ArrayList<Board.Source> fresh = new ArrayList<Board.Source>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < named.size(); i++) {
                Board.Source s = new Board.Source();
                s.name = named.get(i);
                s.added = now;
                s.state = Board.WAIT;
                fresh.add(s);
            }
            if (fresh.isEmpty()) {
                flash(Glyph.CROSS, Words.s("carry_bad"));
                return;
            }
            came = Board.welcome(this, fresh);
        }
        flash(Glyph.TICK, Words.s("carry_done").replace("{c}", String.valueOf(came.size())));
        if (trail.isEmpty()) {
            refreshBoard();
        } else if (SOURCES.equals(front())) {
            redrawSources();
        }
        if (!came.isEmpty()) {
            Crier.round(this, came);
        }
    }

    // ------------------------------------------------------------------ about

    /**
     * What the application is, told once and plainly: the mark and the name
     * at the head, then what the name means, how the board works, every kind
     * of link it understands with an example of each, the languages, and
     * what it will not do. The examples are made from the hosts the board
     * reads, so they say what the board really takes.
     */
    private View aboutView() {
        LinearLayout column = column();

        Crest mark = new Crest(this, Round.px(112f));
        mark.setClickable(false);
        mark.setBackground(null);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markParams.gravity = Gravity.CENTER_HORIZONTAL;
        markParams.topMargin = Round.dp(12);
        column.addView(mark, markParams);

        TextView name = words(Letter.DISPLAY_M, getString(R.string.app_name), Tone.ON_SURFACE);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(16);
        column.addView(name, nameParams);

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unknown) {
            version = "";
        }
        TextView made = Letter.serif(words(Letter.LABEL_L,
            Words.s("about_version").replace("{v}", version), Tone.PRIMARY));
        made.setGravity(Gravity.CENTER_HORIZONTAL);
        made.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams madeParams = wide();
        madeParams.topMargin = Round.dp(2);
        column.addView(made, madeParams);

        aboutPart(column, "about_name_h", "about_name");
        aboutPart(column, "about_how_h", "about_how");

        column.addView(aboutHead("about_links_h"), aboutHeadPlace());
        String[] hosts = Ways.list("hosts");
        String host = hosts.length > 0 ? hosts[0] : "";
        String[] folder = Ways.list("folder");
        String[][] kinds = {
            {host + "/name", "link_channel"},
            {"@name", "link_handle"},
            {host + "/name/123", "link_message"},
            {host + "/+\u2026", "link_closed"},
            {host + "/" + (folder.length > 0 ? folder[0] : "") + "/\u2026", "link_folder"},
            {"@one  @two  @three", "link_list"},
            {Carry.HEAD + "  \u2026", "link_carry"},
        };
        for (int i = 0; i < kinds.length; i++) {
            TextView sample = Letter.serif(words(Letter.TITLE_M, kinds[i][0], Tone.PRIMARY));
            LinearLayout.LayoutParams sampleParams = wide();
            sampleParams.topMargin = Round.dp(i == 0 ? 8 : 14);
            column.addView(sample, sampleParams);
            TextView said = words(Letter.BODY_M, Words.s(kinds[i][1]), Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams saidParams = wide();
            saidParams.topMargin = Round.dp(2);
            column.addView(said, saidParams);
        }

        aboutPart(column, "about_lang_h", "about_lang");
        aboutPart(column, "about_not_h", "about_not");

        final String home = Ways.get("home");
        if (home.length() > 0) {
            View rule = new View(this);
            rule.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
            LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Round.dp(1)));
            ruleParams.topMargin = Round.dp(32);
            column.addView(rule, ruleParams);
            TextView named = words(Letter.LABEL_M, Words.s("about_home"), Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams namedParams = wide();
            namedParams.topMargin = Round.dp(14);
            column.addView(named, namedParams);
            TextView link = words(Letter.BODY_L, home.replaceFirst("^https?://", ""), Tone.PRIMARY);
            link.setPadding(0, Round.dp(6), 0, Round.dp(6));
            link.setBackground(Round.touch(null, Tone.of(Tone.PRIMARY), Round.S));
            link.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    open(home);
                }
            });
            column.addView(link, wide());
        }
        return scroll(column, true);
    }

    private TextView aboutHead(String key) {
        return Letter.serif(words(Letter.HEADLINE_S, Words.s(key), Tone.ON_SURFACE));
    }

    private static LinearLayout.LayoutParams aboutHeadPlace() {
        LinearLayout.LayoutParams params = wide();
        params.topMargin = Round.dp(32);
        return params;
    }

    /** One part of the story: its head in the book face, its words with room between the lines. */
    private void aboutPart(LinearLayout column, String head, String body) {
        column.addView(aboutHead(head), aboutHeadPlace());
        TextView told = words(Letter.BODY_L, Words.s(body), Tone.ON_SURFACE_VARIANT);
        told.setLineSpacing(0f, 1.32f);
        LinearLayout.LayoutParams toldParams = wide();
        toldParams.topMargin = Round.dp(8);
        column.addView(told, toldParams);
    }

    // ------------------------------------------------------------------ the words

    /**
     * The words the board is read by. At the head the cloud, whose sizes are
     * what each word found at the last reading; under it the two lists,
     * plain and in order, where a word is switched off, taken away, or added.
     */
    private View lexiconView(boolean arriving) {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("lexicon"), Tone.ON_SURFACE));
        TextView about = words(Letter.BODY_M, Words.s("lexicon_about"), Tone.ON_SURFACE_VARIANT);
        about.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams aboutParams = wide();
        aboutParams.topMargin = Round.dp(8);
        column.addView(about, aboutParams);

        ArrayList<Cloud.Word> all = cloudWords();
        Cloud cloud = new Cloud(this, all, arriving, new Cloud.Touch() {
            public void open(Cloud.Word word) {
                wordMenu(word);
            }
        });
        LinearLayout.LayoutParams cloudParams = wide();
        cloudParams.topMargin = Round.dp(20);
        column.addView(cloud, cloudParams);

        wordList(column, all, Lex.CALL, "lexicon_calls", "lexicon_calls_what");
        wordList(column, all, Lex.AWAY, "lexicon_away", "lexicon_away_what");
        return scroll(column, false);
    }

    /** Every word of both kinds, the lexicon's and one's own, with what it found. */
    private ArrayList<Cloud.Word> cloudWords() {
        ArrayList<Board.Source> sources = Board.sources(this);
        ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < sources.size(); i++) {
            names.add(sources.get(i).name);
        }
        Sift.Tally tally = Keep.tally(this, names);
        ArrayList<Cloud.Word> out = new ArrayList<Cloud.Word>();
        String[] kinds = {Lex.CALL, Lex.AWAY};
        for (int k = 0; k < kinds.length; k++) {
            List<String> lexicon = Lex.lexicon(kinds[k]);
            for (int i = 0; i < lexicon.size(); i++) {
                String word = lexicon.get(i);
                String form = tally.form(kinds[k], word);
                String bare = word.endsWith("*") ? word.substring(0, word.length() - 1) : word;
                out.add(new Cloud.Word(kinds[k], word, form.length() > 0 ? form : bare + "\u2026",
                    false, Lex.isOff(kinds[k], word), tally.count(kinds[k], word)));
            }
            List<Lex.Own> own = Lex.owned(kinds[k]);
            for (int i = 0; i < own.size(); i++) {
                String said = own.get(i).said;
                out.add(new Cloud.Word(kinds[k], "+" + said, said, true, false,
                    tally.count(kinds[k], "+" + said)));
            }
        }
        return out;
    }

    /** One kind of words as capsules in order, and the capsule that adds one. */
    private void wordList(LinearLayout column, ArrayList<Cloud.Word> all, final String kind,
                          String head, String what) {
        TextView named = Letter.serif(words(Letter.HEADLINE_S, Words.s(head), Tone.ON_SURFACE));
        LinearLayout.LayoutParams namedParams = wide();
        namedParams.topMargin = Round.dp(32);
        column.addView(named, namedParams);
        TextView said = words(Letter.BODY_S, Words.s(what), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams saidParams = wide();
        saidParams.topMargin = Round.dp(2);
        column.addView(said, saidParams);

        ArrayList<Cloud.Word> mine = new ArrayList<Cloud.Word>();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).kind.equals(kind)) {
                mine.add(all.get(i));
            }
        }
        Collections.sort(mine, new java.util.Comparator<Cloud.Word>() {
            public int compare(Cloud.Word a, Cloud.Word b) {
                if (a.own != b.own) {
                    return a.own ? 1 : -1;
                }
                return a.label.compareTo(b.label);
            }
        });
        Flow flow = new Flow(this, Round.dp(8));
        for (int i = 0; i < mine.size(); i++) {
            final Cloud.Word w = mine.get(i);
            TextView chip = Letter.set(new TextView(this), Letter.BODY_M);
            chip.setText(w.label);
            chip.setSingleLine(true);
            chip.setPadding(Round.dp(14), Round.dp(7), Round.dp(14), Round.dp(7));
            chip.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL),
                Tone.of(Tone.ON_SURFACE), Round.FULL));
            chip.setTextColor(Tone.of(w.off ? Tone.OUTLINE : Lex.AWAY.equals(kind) ? Tone.TERTIARY
                : w.count > 0 ? Tone.ON_SURFACE : Tone.ON_SURFACE_VARIANT));
            if (w.own) {
                chip.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC));
            }
            if (w.off) {
                chip.setPaintFlags(chip.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    wordMenu(w);
                }
            });
            flow.addView(chip);
        }
        TextView add = Letter.set(new TextView(this), Letter.LABEL_L);
        add.setText("+  " + Words.s("word_add"));
        add.setSingleLine(true);
        add.setPadding(Round.dp(16), Round.dp(8), Round.dp(16), Round.dp(8));
        add.setBackground(Round.touch(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL),
            Tone.of(Tone.ON_SECONDARY_CONTAINER), Round.FULL));
        add.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askWord(kind);
            }
        });
        flow.addView(add);
        LinearLayout.LayoutParams flowParams = wide();
        flowParams.topMargin = Round.dp(14);
        column.addView(flow, flowParams);
    }

    /** What can be done with one word: switched off or on again if it is the lexicon's, taken away if it is one's own. */
    private void wordMenu(final Cloud.Word w) {
        ArrayList<ArrayList<Bloom.Row>> groups = new ArrayList<ArrayList<Bloom.Row>>();
        ArrayList<Bloom.Row> rows = new ArrayList<Bloom.Row>();
        String found = String.valueOf(w.count);
        if (w.own) {
            rows.add(new Bloom.Row(Glyph.CROSS, Words.s("word_remove"), found, true, new Bloom.Act() {
                public boolean act(Bloom.Row row, Bloom menu) {
                    Lex.removeOwn(w.kind, w.key.substring(1));
                    wordsChanged(Glyph.CROSS, Words.s("word_removed"));
                    return true;
                }
            }));
        } else {
            rows.add(new Bloom.Row(w.off ? Glyph.TICK : Glyph.CROSS, Words.s(w.off ? "word_on" : "word_off"),
                found, !w.off, new Bloom.Act() {
                public boolean act(Bloom.Row row, Bloom menu) {
                    Lex.setOff(w.kind, w.key, !w.off);
                    wordsChanged(w.off ? Glyph.TICK : Glyph.CROSS, Words.s(w.off ? "word_switched_on" : "word_switched_off"));
                    return true;
                }
            }));
        }
        groups.add(rows);
        bloom(groups);
    }

    /** The field takes a word or a phrase of one's own for one kind. */
    private void askWord(String kind) {
        wording = kind;
        adding = false;
        asking = null;
        ask.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        ask.setText("");
        ask.setHint(Words.s("word_hint"));
        hereWord.setVisibility(View.GONE);
        ask.setVisibility(View.VISIBLE);
        hereMark.kind(Glyph.EDIT);
        keyboard(true);
    }

    private void commitWord() {
        String typed = ask.getText().toString().trim();
        String kind = wording;
        endAsk();
        if (typed.length() == 0 || kind == null) {
            return;
        }
        int done = Lex.addOwn(kind, typed);
        if (done == Lex.SHORT) {
            flash(Glyph.CROSS, Words.s("word_short"));
        } else if (done == Lex.THERE) {
            flash(Glyph.EDIT, Words.s("word_there"));
        } else {
            wordsChanged(Glyph.TICK, Words.s("word_added"));
        }
    }

    /** The words changed: kept, the screen drawn again where it stands, and the channels read again to see what it did. */
    private void wordsChanged(int kind, String said) {
        Keep.saveWords(this, Lex.layer());
        Trace.note("words: changed, the channels read again");
        if (LEXICON.equals(front())) {
            final int y = scroller == null ? 0 : scroller.getScrollY();
            forgetScene();
            replace(lexiconView(false));
            if (y > 0 && scroller != null) {
                final ScrollView now = scroller;
                now.post(new Runnable() {
                    public void run() {
                        now.scrollTo(0, y);
                    }
                });
            }
        }
        flash(kind, said);
        if (wordsBefore == null) {
            wordsBefore = new HashSet<String>();
            ArrayList<Sift.Bill> bills = Board.bills(this);
            for (int i = 0; i < bills.size(); i++) {
                wordsBefore.add(bills.get(i).key());
            }
        }
        if (Crier.busy()) {
            wordsAgain = true;
        } else {
            Crier.round(this, null);
        }
    }

    /** A reading ended: if the words had changed, what they did to the board is said, and the cloud grows again from it. */
    private void wordsRead() {
        if (wordsAgain) {
            wordsAgain = false;
            Crier.round(this, null);
            return;
        }
        if (wordsBefore == null) {
            return;
        }
        HashSet<String> now = new HashSet<String>();
        ArrayList<Sift.Bill> bills = Board.bills(this);
        for (int i = 0; i < bills.size(); i++) {
            now.add(bills.get(i).key());
        }
        int came = 0;
        for (String key : now) {
            if (!wordsBefore.contains(key)) {
                came++;
            }
        }
        int went = 0;
        for (String key : wordsBefore) {
            if (!now.contains(key)) {
                went++;
            }
        }
        wordsBefore = null;
        flash(Glyph.BOARD, came == 0 && went == 0 ? Words.s("words_same")
            : Words.s("words_diff").replace("{a}", String.valueOf(came)).replace("{r}", String.valueOf(went)));
        Trace.note("words: the board after the change, " + came + " came, " + went + " went");
        if (LEXICON.equals(front()) && !typing() && bloom == null) {
            final int y = scroller == null ? 0 : scroller.getScrollY();
            forgetScene();
            replace(lexiconView(false));
            if (y > 0 && scroller != null) {
                final ScrollView sv = scroller;
                sv.post(new Runnable() {
                    public void run() {
                        sv.scrollTo(0, y);
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------------ settings

    private View settingsView() {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("settings"), Tone.ON_SURFACE));

        LinearLayout.LayoutParams first = wide();
        first.topMargin = Round.dp(28);
        column.addView(card(Glyph.CHANNEL, Words.s("sources"), Words.s("sources_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(SOURCES);
                }
            }), first);
        column.addView(card(Glyph.EDIT, Words.s("lexicon"), Words.s("lexicon_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(LEXICON);
                }
            }), next());
        column.addView(card(Glyph.LOOK, Words.s("look"), Words.s("look_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(LOOK);
                }
            }), next());
        column.addView(card(Glyph.LANGUAGE, Words.s("language"), Words.s("language_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(LANGUAGE);
                }
            }), next());
        column.addView(card(Glyph.SHARE, Words.s("carry"), Words.s("carry_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(CARRY);
                }
            }), next());
        column.addView(card(Glyph.RIBBON, Words.s("about"), Words.s("about_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(ABOUT);
                }
            }), next());
        column.addView(card(Glyph.CARET, Words.s("log"), Words.s("log_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    door(LOG);
                }
            }), next());

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unknown) {
            version = "";
        }
        TextView made = words(Letter.LABEL_M, getString(R.string.app_name) + "  " + version,
            Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams madeParams = wide();
        madeParams.topMargin = Round.dp(32);
        column.addView(made, madeParams);
        return scroll(column, true);
    }

    private static LinearLayout.LayoutParams next() {
        LinearLayout.LayoutParams params = wide();
        params.topMargin = Round.dp(8);
        return params;
    }

    /** A card: a mark in its disc, a name, and one line about it. */
    private LinearLayout card(int kind, String name, String caption, View.OnClickListener click) {
        LinearLayout made = new LinearLayout(this);
        made.setOrientation(LinearLayout.HORIZONTAL);
        made.setGravity(Gravity.CENTER_VERTICAL);
        made.setPadding(Round.dp(16), Round.dp(16), Round.dp(20), Round.dp(16));
        made.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));
        made.setOnClickListener(click);
        Glyph mark = new Glyph(this, kind, Round.px(44f), 0.5f,
            Tone.of(Tone.SURFACE_HIGHEST), 0x00000000, Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markParams.rightMargin = Round.dp(16);
        made.addView(mark, markParams);
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        said.addView(words(Letter.TITLE_M, name, Tone.ON_SURFACE));
        TextView line = words(Letter.BODY_M, caption, Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams lineParams = wide();
        lineParams.topMargin = Round.dp(2);
        said.addView(line, lineParams);
        made.addView(said, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return made;
    }

    // ------------------------------------------------------------------ channels

    /**
     * The channels the board reads, each a card: its face and name, how the
     * last reading went, a cross to remove it, and under them the tag that
     * sifts its posts — given by hand, or the one the board noticed itself.
     */
    private View sourcesView() {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("sources"), Tone.ON_SURFACE));
        TextView what = words(Letter.BODY_M, Words.s("sources_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);

        LinearLayout.LayoutParams addParams = buttonPlace(20);
        column.addView(button(Words.s("add_channel"), TONAL, new View.OnClickListener() {
            public void onClick(View v) {
                askAdd();
            }
        }), addParams);

        ArrayList<Board.Source> sources = Board.sources(this);
        Collections.sort(sources, new java.util.Comparator<Board.Source>() {
            public int compare(Board.Source a, Board.Source b) {
                return a.shown().compareToIgnoreCase(b.shown());
            }
        });
        if (sources.isEmpty()) {
            TextView none = words(Letter.BODY_L, Words.s("no_sources"), Tone.ON_SURFACE);
            LinearLayout.LayoutParams noneParams = wide();
            noneParams.topMargin = Round.dp(28);
            column.addView(none, noneParams);
        }
        for (int i = 0; i < sources.size(); i++) {
            LinearLayout.LayoutParams params = wide();
            params.topMargin = Round.dp(i == 0 ? 24 : 8);
            column.addView(sourceCard(sources.get(i)), params);
        }
        return scroll(column, true);
    }

    private View sourceCard(final Board.Source s) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Round.dp(16), Round.dp(14), Round.dp(8), Round.dp(10));
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new Faces.Disc(this, s.name, s.shown(), Round.px(44f)));
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView name = words(Letter.TITLE_M, s.shown(), Tone.ON_SURFACE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(name);
        String state = s.state == Board.OPEN
            ? "@" + s.name + "  \u00B7  " + Words.s("found_n").replace("{n}", String.valueOf(s.ahead))
            : s.state == Board.CLOSED ? Words.s("state_closed")
            : s.state == Board.GROUP ? "@" + s.name + "  \u00B7  " + Words.s("state_group")
            : s.state == Board.SILENT ? Words.s("state_silent") : Words.s("state_wait");
        TextView line = words(Letter.BODY_S, state,
            s.state == Board.CLOSED || s.state == Board.SILENT || s.state == Board.GROUP ? Tone.TERTIARY : Tone.ON_SURFACE_VARIANT);
        line.setMaxLines(2);
        LinearLayout.LayoutParams lineParams = wide();
        lineParams.topMargin = Round.dp(2);
        said.addView(line, lineParams);
        LinearLayout.LayoutParams saidParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saidParams.leftMargin = Round.dp(14);
        top.addView(said, saidParams);

        boolean armed = s.name.equals(arming)
            && System.currentTimeMillis() - armingAt < 3000L;
        if (armed) {
            TextView sure = Letter.set(new TextView(this), Letter.LABEL_L);
            sure.setText(Words.s("remove_sure"));
            sure.setGravity(Gravity.CENTER);
            sure.setPadding(Round.dp(16), 0, Round.dp(16), 0);
            sure.setTextColor(Bloom.errorInk());
            sure.setBackground(Round.touch(Round.ring(0x00000000, Round.FULL, Bloom.errorInk()),
                Bloom.errorInk(), Round.FULL));
            sure.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    arming = null;
                    Board.drop(Main.this, s.name);
                    Faces.forget(s.name);
                    Trace.note("channels: one removed");
                    redrawSources();
                    flash(Glyph.CROSS, Words.s("removed"));
                }
            });
            LinearLayout.LayoutParams sureParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(36));
            sureParams.leftMargin = Round.dp(8);
            sureParams.rightMargin = Round.dp(6);
            top.addView(sure, sureParams);
        } else {
            Glyph cross = new Glyph(this, Glyph.CROSS, Round.px(40f), 0.62f,
                0x00000000, 0x00000000, Tone.of(Tone.ON_SURFACE_VARIANT));
            cross.setBackground(Round.disc(Tone.of(Tone.ON_SURFACE)));
            cross.setContentDescription(Words.s("remove"));
            cross.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    arming = s.name;
                    armingAt = System.currentTimeMillis();
                    redrawSources();
                    clock.postDelayed(new Runnable() {
                        public void run() {
                            if (s.name.equals(arming) && SOURCES.equals(front()) && !typing()) {
                                arming = null;
                                redrawSources();
                            }
                        }
                    }, 3100L);
                }
            });
            LinearLayout.LayoutParams crossParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            crossParams.leftMargin = Round.dp(4);
            top.addView(cross, crossParams);
        }
        card.addView(top, wide());

        /* A group has no wall to sift, so no tag: only how its announcements come. */
        if (s.state == Board.GROUP) {
            TextView hint = words(Letter.BODY_M, Words.s("group_hint"), Tone.ON_SURFACE_VARIANT);
            hint.setLineSpacing(0f, 1.2f);
            LinearLayout.LayoutParams hintParams = wide();
            hintParams.topMargin = Round.dp(12);
            hintParams.rightMargin = Round.dp(8);
            card.addView(hint, hintParams);
            return card;
        }

        /* The tag: what sifts the channel's posts, and the way to change it. */
        LinearLayout tag = new LinearLayout(this);
        tag.setOrientation(LinearLayout.HORIZONTAL);
        tag.setGravity(Gravity.CENTER_VERTICAL);
        tag.setPadding(Round.dp(12), Round.dp(8), Round.dp(16), Round.dp(8));
        tag.setMinimumHeight(Round.dp(40));
        tag.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL),
            Tone.of(Tone.ON_SURFACE), Round.FULL));
        boolean given = s.tag.length() > 0;
        boolean noticed = !given && s.learned.length() > 0;
        tag.addView(new Glyph(this, Glyph.TAG, Round.px(20f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(given || noticed ? Tone.PRIMARY : Tone.ON_SURFACE_VARIANT)));
        String tagWords = given ? Words.s("tag_is").replace("{t}", "#" + s.tag)
            : noticed ? Words.s("tag_learned").replace("{t}", "#" + s.learned) : Words.s("tag_none");
        TextView tagLine = words(Letter.BODY_M, tagWords,
            given ? Tone.ON_SURFACE : Tone.ON_SURFACE_VARIANT);
        tagLine.setSingleLine(true);
        tagLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tagLineParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tagLineParams.leftMargin = Round.dp(10);
        tag.addView(tagLine, tagLineParams);
        tag.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askTag(s);
            }
        });
        LinearLayout.LayoutParams tagParams = wide();
        tagParams.topMargin = Round.dp(10);
        tagParams.rightMargin = Round.dp(8);
        card.addView(tag, tagParams);
        return card;
    }

    /** The field takes a channel's tag, with the one it has already in it. */
    private void askTag(Board.Source s) {
        asking = s.name;
        adding = false;
        ask.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ask.setText(s.tag);
        ask.setSelection(ask.getText().length());
        ask.setHint(Words.s("tag_hint"));
        hereWord.setVisibility(View.GONE);
        ask.setVisibility(View.VISIBLE);
        hereMark.kind(Glyph.TAG);
        keyboard(true);
    }

    /** The tag taken: kept, the channel's meetings forgotten, and the channel read again with it. */
    private void commitTag() {
        if (asking == null) {
            return;
        }
        String typed = ask.getText().toString().trim();
        while (typed.startsWith("#")) {
            typed = typed.substring(1).trim();
        }
        typed = typed.replaceAll("\\s+", "");
        Board.Source s = Board.source(this, asking);
        String name = asking;
        endAsk();
        if (s == null) {
            return;
        }
        boolean changed = !typed.equals(s.tag);
        s.tag = typed;
        Board.put(this, s);
        if (changed) {
            Board.replace(this, name, new ArrayList<Sift.Bill>());
            ArrayList<String> one = new ArrayList<String>();
            one.add(name);
            Crier.round(this, one);
            Trace.note("channels: a tag " + (typed.length() > 0 ? "given" : "taken off"));
        }
        redrawSources();
        flash(Glyph.TAG, Words.s(typed.length() > 0 ? "tag_set" : "tag_off"));
    }

    private void endAsk() {
        asking = null;
        adding = false;
        wording = null;
        keyboard(false);
        ask.setVisibility(View.GONE);
        hereWord.setVisibility(View.VISIBLE);
        plate(false);
    }

    /** Whether the field is taking words now, for a tag or for a channel. */
    private boolean typing() {
        return asking != null || adding || wording != null;
    }

    /**
     * The field takes a channel by hand: its link, pasted, or its short name,
     * with or without the sign before it. The way in for whoever found a
     * channel somewhere that cannot share it.
     */
    private void askAdd() {
        adding = true;
        asking = null;
        ask.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        ask.setText("");
        ask.setHint(Words.s("add_hint"));
        hereWord.setVisibility(View.GONE);
        ask.setVisibility(View.VISIBLE);
        hereMark.kind(Glyph.CHANNEL);
        keyboard(true);
    }

    /** What was typed, taken for a channel if it is one, and read at once. */
    private void commitAdd() {
        String typed = ask.getText().toString().trim();
        endAsk();
        if (typed.length() == 0) {
            return;
        }
        Ways.Pointed pointed = Ways.pointed(typed);
        String name = null;
        if (pointed.kind == Ways.Pointed.CHANNEL && pointed.post > 0) {
            /* A link to one message goes where a shared one goes: the sheet reads it. */
            Intent one = new Intent(this, Take.class);
            one.setAction(Intent.ACTION_SEND);
            one.setType("text/plain");
            one.putExtra(Intent.EXTRA_TEXT, typed);
            startActivity(one);
            return;
        }
        if (pointed.kind == Ways.Pointed.CHANNEL) {
            name = pointed.name;
        } else if (pointed.kind == Ways.Pointed.CLOSED) {
            flash(Glyph.CROSS, Words.s("take_closed"));
            return;
        } else if (pointed.kind == Ways.Pointed.FOLDER) {
            flash(Glyph.CROSS, Words.s("take_folder"));
            return;
        } else if (typed.matches("@?[A-Za-z0-9_]{4,64}")) {
            name = typed.startsWith("@") ? typed.substring(1) : typed;
        }
        if (name == null) {
            flash(Glyph.CROSS, Words.s("not_channel"));
            return;
        }
        if (Board.source(this, name) != null) {
            flash(Glyph.CHANNEL, Words.s("take_there"));
            return;
        }
        Board.Source s = new Board.Source();
        s.name = name;
        s.added = System.currentTimeMillis();
        s.state = Board.WAIT;
        Board.put(this, s);
        Trace.note("channels: one added by hand");
        ArrayList<String> one = new ArrayList<String>();
        one.add(name);
        Crier.round(this, one);
        redrawSources();
        flash(Glyph.CHANNEL, Words.s("added"));
    }

    // ------------------------------------------------------------------- look

    /**
     * The seed, and everything that grows from it, on one screen: a specimen
     * of the roles at the top, the two dials under it. Every movement of a
     * dial regrows the whole scheme at once, the bar and the button included,
     * so the choice is seen in place and not in a preview.
     */
    private View lookView() {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("look"), Tone.ON_SURFACE));
        TextView what = words(Letter.BODY_M, Words.s("look_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);

        LinearLayout accents = row();
        accents.addView(swatch(Tone.PRIMARY, Tone.ON_PRIMARY, Words.s("sw_primary"),
            Letter.LABEL_L, Round.L), cell(88f, 0));
        accents.addView(swatch(Tone.SECONDARY, Tone.ON_SECONDARY, Words.s("sw_secondary"),
            Letter.LABEL_L, Round.L), cell(88f, 8));
        accents.addView(swatch(Tone.TERTIARY, Tone.ON_TERTIARY, Words.s("sw_tertiary"),
            Letter.LABEL_L, Round.L), cell(88f, 8));
        LinearLayout.LayoutParams accentsParams = wide();
        accentsParams.topMargin = Round.dp(28);
        column.addView(accents, accentsParams);

        LinearLayout containers = row();
        containers.addView(swatch(Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER, "Aa",
            Letter.TITLE_L, Round.M), cell(56f, 0));
        containers.addView(swatch(Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER, "Aa",
            Letter.TITLE_L, Round.M), cell(56f, 8));
        containers.addView(swatch(Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER, "Aa",
            Letter.TITLE_L, Round.M), cell(56f, 8));
        column.addView(containers, next());

        LinearLayout ground = row();
        int[] steps = {
            Tone.SURFACE_LOWEST, Tone.SURFACE, Tone.SURFACE_CONTAINER,
            Tone.SURFACE_HIGH, Tone.SURFACE_HIGHEST, Tone.SURFACE_BRIGHT,
        };
        for (int i = 0; i < steps.length; i++) {
            ground.addView(swatch(steps[i], Tone.ON_SURFACE, "", Letter.LABEL_S, Round.S),
                cell(36f, i == 0 ? 0 : 4));
        }
        column.addView(ground, next());
        TextView groundWord = words(Letter.LABEL_M, Words.s("sw_ground"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams groundWordParams = wide();
        groundWordParams.topMargin = Round.dp(6);
        column.addView(groundWord, groundWordParams);

        TextView hueWord = words(Letter.LABEL_L, Words.s("hue"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams hueWordParams = wide();
        hueWordParams.topMargin = Round.dp(32);
        column.addView(hueWord, hueWordParams);
        hueDial = new Dial(this, Tone.hue() / 360f, new Dial.Moved() {
            public void moved(float value, boolean done) {
                seed(value * 360f, Tone.rich());
            }
        });
        column.addView(hueDial, wide());

        TextView richWord = words(Letter.LABEL_L, Words.s("richness"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams richWordParams = wide();
        richWordParams.topMargin = Round.dp(16);
        column.addView(richWord, richWordParams);
        richDial = new Dial(this, Tone.rich(), new Dial.Moved() {
            public void moved(float value, boolean done) {
                seed(Tone.hue(), value);
            }
        });
        column.addView(richDial, wide());

        if (Build.VERSION.SDK_INT >= 31) {
            wallChip = new LinearLayout(this);
            wallChip.setOrientation(LinearLayout.HORIZONTAL);
            wallChip.setGravity(Gravity.CENTER_VERTICAL);
            wallChip.setPadding(Round.dp(10), 0, Round.dp(16), 0);
            wallChip.setMinimumHeight(Round.dp(32));
            wallMark = new Glyph(this, Glyph.SPARK, Round.px(18f), 0.92f,
                0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            markParams.rightMargin = Round.dp(8);
            wallChip.addView(wallMark, markParams);
            wallWord = Letter.set(new TextView(this), Letter.LABEL_L);
            wallWord.setText(Words.s("wallpaper"));
            wallChip.addView(wallWord);
            wallChip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Keep.saveLook(Main.this, Tone.hue(), Tone.rich(), true);
                    Tone.read(Main.this);
                    if (hueDial != null) {
                        hueDial.value(Tone.hue() / 360f);
                        richDial.value(Tone.rich());
                    }
                    retone();
                }
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(32));
            chipParams.topMargin = Round.dp(20);
            column.addView(wallChip, chipParams);
        }
        paintLook();
        return scroll(column, true);
    }

    /** A hand moved a dial: the seed is chosen, and no longer follows the wallpaper. */
    private void seed(float hue, float rich) {
        Keep.saveLook(this, hue, rich, false);
        Tone.read(this);
        retone();
    }

    private LinearLayout row() {
        LinearLayout made = new LinearLayout(this);
        made.setOrientation(LinearLayout.HORIZONTAL);
        return made;
    }

    private static LinearLayout.LayoutParams cell(float tall, int before) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Round.dp(tall), 1f);
        params.leftMargin = Round.dp(before);
        return params;
    }

    private TextView swatch(int fill, int ink, String text, int rung, float radius) {
        TextView made = Letter.set(new TextView(this), rung);
        made.setText(text);
        made.setGravity(Gravity.BOTTOM | Gravity.START);
        made.setPadding(Round.dp(12), 0, Round.dp(12), Round.dp(10));
        made.setTag(Float.valueOf(radius));
        swatches.add(made);
        swatchRoles.add(new int[] {fill, ink});
        return made;
    }

    /** The specimen and the dials in the colours the seed gives now. */
    private void paintLook() {
        for (int i = 0; i < swatches.size(); i++) {
            TextView one = swatches.get(i);
            int[] pair = swatchRoles.get(i);
            float radius = ((Float) one.getTag()).floatValue();
            one.setBackground(Round.box(Tone.of(pair[0]), radius));
            one.setTextColor(Tone.of(pair[1]));
        }
        if (hueDial != null) {
            int[] circle = new int[25];
            for (int i = 0; i < circle.length; i++) {
                circle[i] = Tone.at(72f, 44.0, i * 15f);
            }
            hueDial.colours(circle);
            hueDial.ink(Tone.of(Tone.PRIMARY));
        }
        if (richDial != null) {
            int[] way = new int[9];
            for (int i = 0; i < way.length; i++) {
                way[i] = Tone.at(72f, Tone.chromaOf(i / 8f), Tone.hue());
            }
            richDial.colours(way);
            richDial.ink(Tone.of(Tone.PRIMARY));
        }
        if (wallChip != null) {
            boolean on = Keep.wall(this);
            if (on) {
                wallChip.setBackground(Round.touch(Round.box(Tone.of(Tone.SECONDARY_CONTAINER),
                    Round.S), Tone.of(Tone.ON_SECONDARY_CONTAINER), Round.S));
                wallWord.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
                wallMark.tint(0x00000000, 0x00000000, Tone.of(Tone.ON_SECONDARY_CONTAINER));
            } else {
                wallChip.setBackground(Round.touch(Round.ring(0x00000000, Round.S,
                    Tone.of(Tone.OUTLINE_VARIANT)), Tone.of(Tone.ON_SURFACE_VARIANT), Round.S));
                wallWord.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
                wallMark.tint(0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
            }
        }
    }

    // --------------------------------------------------------------- language

    private View languageView() {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("language"), Tone.ON_SURFACE));
        TextView what = words(Letter.BODY_M, Words.s("language_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Round.dp(20), Round.dp(18), Round.dp(20), Round.dp(18));
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        String called = !Words.active() ? Words.s("english")
            : (Words.name().length() > 0 ? Words.name() : "untitled");
        card.addView(words(Letter.TITLE_L, called, Tone.ON_SURFACE));
        if (Words.active()) {
            String count = Words.s("words_of")
                .replace("{n}", String.valueOf(Words.filled()))
                .replace("{m}", String.valueOf(Words.total()));
            TextView counted = words(Letter.BODY_M, count, Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams countedParams = wide();
            countedParams.topMargin = Round.dp(4);
            card.addView(counted, countedParams);
            int stale = Words.stale();
            if (stale > 0) {
                TextView old = words(Letter.BODY_M,
                    Words.s("stale").replace("{n}", String.valueOf(stale)), Tone.PRIMARY);
                LinearLayout.LayoutParams oldParams = wide();
                oldParams.topMargin = Round.dp(2);
                card.addView(old, oldParams);
            }
        }
        LinearLayout.LayoutParams cardParams = wide();
        cardParams.topMargin = Round.dp(28);
        column.addView(card, cardParams);

        column.addView(button(Words.s("module_load"), TONAL, new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(pick, LOAD_MODULE);
                } catch (ActivityNotFoundException nobody) {
                    Trace.note("language: no picker of files");
                }
            }
        }), buttonPlace(24));
        column.addView(button(Words.s("module_save"), OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                Intent make = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                make.addCategory(Intent.CATEGORY_OPENABLE);
                make.setType("text/plain");
                make.putExtra(Intent.EXTRA_TITLE, "language.txt");
                try {
                    startActivityForResult(make, SAVE_FORM);
                } catch (ActivityNotFoundException nobody) {
                    Trace.note("language: no place to save a file");
                }
            }
        }), buttonPlace(8));
        if (Words.active()) {
            column.addView(button(Words.s("use_english"), PLAIN, new View.OnClickListener() {
                public void onClick(View v) {
                    Words.forget();
                    Words.save(Main.this);
                    relabel();
                }
            }), buttonPlace(8));
        }
        return scroll(column, true);
    }

    private static LinearLayout.LayoutParams buttonPlace(int above) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(40));
        params.topMargin = Round.dp(above);
        return params;
    }

    /** A button is a capsule; its kind decides only how loudly it is coloured. */
    private TextView button(String text, int kind, View.OnClickListener click) {
        TextView made = Letter.set(new TextView(this), Letter.LABEL_L);
        made.setText(text);
        made.setGravity(Gravity.CENTER);
        made.setPadding(Round.dp(24), 0, Round.dp(24), 0);
        made.setSingleLine(true);
        made.setOnClickListener(click);
        switch (kind) {
            case TONAL:
                made.setBackground(Round.touch(Round.box(Tone.of(Tone.SECONDARY_CONTAINER),
                    Round.FULL), Tone.of(Tone.ON_SECONDARY_CONTAINER), Round.FULL));
                made.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
                break;
            case OUTLINED:
                made.setBackground(Round.touch(Round.ring(0x00000000, Round.FULL,
                    Tone.of(Tone.OUTLINE_VARIANT)), Tone.of(Tone.PRIMARY), Round.FULL));
                made.setTextColor(Tone.of(Tone.PRIMARY));
                break;
            default:
                made.setBackground(Round.touch(null, Tone.of(Tone.PRIMARY), Round.FULL));
                made.setTextColor(Tone.of(Tone.PRIMARY));
                break;
        }
        return made;
    }

    /** The language changed: every word already standing is said again. */
    private void relabel() {
        plate(false);
        crest.setContentDescription(Words.s("settings"));
        if (LANGUAGE.equals(front())) {
            forgetScene();
            replace(languageView());
        }
    }

    // ------------------------------------------------------------------ journal

    private View logView() {
        LinearLayout column = column();
        column.addView(words(Letter.HEADLINE_M, Words.s("log"), Tone.ON_SURFACE));
        TextView what = words(Letter.BODY_M, Words.s("log_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);
        final String all = Trace.whole(this);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.addView(button(Words.s("log_copy"), TONAL, new View.OnClickListener() {
            public void onClick(View v) {
                android.content.ClipboardManager board = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
                if (board != null) {
                    board.setPrimaryClip(android.content.ClipData.newPlainText("log", all));
                }
                if (Build.VERSION.SDK_INT < 33) {
                    flash(Glyph.TICK, Words.s("copied"));
                }
            }
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(40)));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(40));
        shareParams.leftMargin = Round.dp(8);
        keys.addView(button(Words.s("share"), OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, all);
                try {
                    startActivity(Intent.createChooser(send, null));
                } catch (Exception none) {
                    Trace.note("nothing to share the journal with");
                }
            }
        }), shareParams);
        LinearLayout.LayoutParams keysParams = wide();
        keysParams.topMargin = Round.dp(20);
        column.addView(keys, keysParams);
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setPadding(Round.dp(16), Round.dp(10), Round.dp(16), Round.dp(14));
        lines.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        logLines(lines, all);
        LinearLayout.LayoutParams linesParams = wide();
        linesParams.topMargin = Round.dp(16);
        column.addView(lines, linesParams);
        return scroll(column, true);
    }

    /**
     * The journal laid out to be read rather than deciphered: the hour apart
     * from the words, the subject of a line lit, the runs of the application
     * parted by a line of their own.
     */
    private void logLines(LinearLayout into, String all) {
        String[] raw = all.split("\n");
        for (int i = 0; i < raw.length && into.getChildCount() < 260; i++) {
            String line = raw[i];
            if (line.trim().length() == 0) {
                continue;
            }
            if (line.startsWith("\u2014")) {
                into.addView(logBreak(), wide());
                continue;
            }
            int gap = line.indexOf("  ");
            String when = gap > 0 ? line.substring(0, gap) : "";
            String said = gap > 0 ? line.substring(gap + 2).trim() : line.trim();
            String hour = when.length() >= 8 ? when.substring(0, 8) : when;
            android.text.SpannableStringBuilder made = new android.text.SpannableStringBuilder();
            made.append(hour).append("   ").append(said);
            made.setSpan(new android.text.style.ForegroundColorSpan(Tone.of(Tone.ON_SURFACE_VARIANT)),
                0, hour.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int colon = said.indexOf(':');
            if (colon > 0) {
                int from = hour.length() + 3;
                made.setSpan(new android.text.style.ForegroundColorSpan(Tone.of(Tone.PRIMARY)),
                    from, from + colon, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView row = Letter.set(new TextView(this), Letter.BODY_S);
            row.setTextColor(Tone.of(Tone.ON_SURFACE));
            row.setPadding(0, Round.dp(5), 0, Round.dp(5));
            row.setText(made);
            row.setTextIsSelectable(true);
            into.addView(row, wide());
        }
        if (into.getChildCount() == 0) {
            into.addView(words(Letter.BODY_M, Words.s("log_what"), Tone.ON_SURFACE_VARIANT), wide());
        }
    }

    private View logBreak() {
        LinearLayout made = new LinearLayout(this);
        made.setOrientation(LinearLayout.HORIZONTAL);
        made.setGravity(Gravity.CENTER_VERTICAL);
        made.setPadding(0, Round.dp(14), 0, Round.dp(10));
        View left = new View(this);
        left.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
        made.addView(left, new LinearLayout.LayoutParams(0, Math.max(1, Round.dp(1)), 1f));
        TextView said = Letter.set(new TextView(this), Letter.LABEL_S);
        said.setText(Words.s("log_before"));
        said.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        said.setPadding(Round.dp(10), 0, Round.dp(10), 0);
        made.addView(said);
        View right = new View(this);
        right.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
        made.addView(right, new LinearLayout.LayoutParams(0, Math.max(1, Round.dp(1)), 1f));
        return made;
    }

    // ------------------------------------------------------------------ scenes

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Round.dp(24), Round.dp(28), Round.dp(24), Round.dp(40));
        return column;
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private ScrollView scroll(LinearLayout column, boolean arriving) {
        ScrollView made = new ScrollView(this);
        made.setFillViewport(true);
        made.setVerticalScrollBarEnabled(false);
        made.addView(column, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller = made;
        if (arriving) {
            arrive(column);
        }
        return made;
    }

    private TextView words(int rung, String text, int role) {
        TextView view = Letter.set(new TextView(this), rung);
        view.setText(text);
        view.setTextColor(Tone.of(role));
        inked.add(view);
        inkRoles.add(role);
        return view;
    }

    /**
     * One scene gives way to the next. The old one fades out quickly on top;
     * the new one grows in beneath it from a little smaller.
     */
    private void swap(View made) {
        final View old = scene;
        for (int i = stage.getChildCount() - 1; i >= 0; i--) {
            View one = stage.getChildAt(i);
            if (one != old && one != made && Boolean.TRUE.equals(one.getTag(R.id.scene))) {
                one.animate().cancel();
                stage.removeView(one);
            }
        }
        scene = made;
        made.setTag(R.id.scene, Boolean.TRUE);
        stage.addView(made, 0, cover());
        made.setAlpha(0f);
        made.setScaleX(0.96f);
        made.setScaleY(0.96f);
        made.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(70L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        if (old != null && old != made) {
            old.animate().alpha(0f).scaleX(1.02f).scaleY(1.02f)
                .setStartDelay(0L).setDuration(Pace.PRESS)
                .setInterpolator(Pace.AWAY)
                .withEndAction(new Runnable() {
                    public void run() {
                        stage.removeView(old);
                    }
                }).start();
            stage.postDelayed(new Runnable() {
                public void run() {
                    if (old != scene && old.getParent() == stage) {
                        stage.removeView(old);
                    }
                }
            }, Pace.PRESS + 120L);
        }
    }

    /** A scene put in place at once, without a change: for a screen drawn again where it stands. */
    private void replace(View made) {
        View old = scene;
        scene = made;
        made.setTag(R.id.scene, Boolean.TRUE);
        int at = old == null ? 0 : Math.max(0, stage.indexOfChild(old));
        if (old != null) {
            old.animate().cancel();
            stage.removeView(old);
        }
        stage.addView(made, at, cover());
    }

    private static FrameLayout.LayoutParams cover() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** The blocks of a scene arrive one after another, each a beat behind; only the head of a long one. */
    private void arrive(ViewGroup column) {
        for (int i = 0; i < column.getChildCount() && i <= 12; i++) {
            View one = column.getChildAt(i);
            one.setAlpha(0f);
            one.setTranslationY(Round.px(24f));
            one.animate().alpha(1f).translationY(0f)
                .setStartDelay(70L + i * Pace.STAGGER).setDuration(Pace.ARRIVE)
                .setInterpolator(Pace.STANDARD).start();
        }
    }

    /** Clears what the scene in front remembered before the next one is made. */
    private void forgetScene() {
        inked.clear();
        inkRoles.clear();
        swatches.clear();
        swatchRoles.clear();
        hueDial = null;
        richDial = null;
        wallChip = null;
        wallMark = null;
        wallWord = null;
    }

    // ------------------------------------------------------------------ doors

    private String front() {
        return trail.isEmpty() ? null : trail.get(trail.size() - 1);
    }

    /** A screen of ours opens over the board, or over the screen before it. */
    private void door(String which) {
        boolean first = trail.isEmpty();
        if (first) {
            boardY = scroller == null ? 0 : scroller.getScrollY();
        }
        trail.add(which);
        if (first) {
            blob.leaving(true);
        }
        forgetScene();
        swap(viewOf(which));
        placeCrest();
        plate(true);
        Trace.note("opened " + which);
    }

    /** One step back: to the screen before, or out to the board. */
    private void back() {
        if (trail.isEmpty()) {
            return;
        }
        blob.depart();
        trail.remove(trail.size() - 1);
        forgetScene();
        arming = null;
        if (trail.isEmpty()) {
            blob.leaving(false);
            swap(boardView(false));
            placeCrest();
            final int y = boardY;
            if (y > 0 && scroller != null) {
                final ScrollView now = scroller;
                now.post(new Runnable() {
                    public void run() {
                        now.scrollTo(0, y);
                    }
                });
            }
        } else {
            swap(viewOf(front()));
        }
        plate(true);
    }

    private View viewOf(String which) {
        if (SOURCES.equals(which)) {
            return sourcesView();
        }
        if (LOOK.equals(which)) {
            return lookView();
        }
        if (LANGUAGE.equals(which)) {
            return languageView();
        }
        if (LOG.equals(which)) {
            return logView();
        }
        if (READ.equals(which)) {
            return readerView();
        }
        if (CARRY.equals(which)) {
            return carryView();
        }
        if (ABOUT.equals(which)) {
            return aboutView();
        }
        if (LEXICON.equals(which)) {
            return lexiconView(true);
        }
        return settingsView();
    }

    @Override
    public void onBackPressed() {
        if (bloom != null) {
            bloom.dismiss();
        } else if (typing()) {
            endAsk();
        } else if (!trail.isEmpty()) {
            back();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ the menu at the finger

    private void bloom(ArrayList<ArrayList<Bloom.Row>> groups) {
        for (int i = stage.getChildCount() - 1; i >= 0; i--) {
            if (stage.getChildAt(i) instanceof Bloom) {
                stage.removeViewAt(i);
            }
        }
        int[] at = new int[2];
        stage.getLocationOnScreen(at);
        final Bloom made = new Bloom(this, downRawX - at[0], downRawY - at[1], groups,
            new Bloom.Gone() {
                public void gone() {
                    bloom = null;
                    bloomFollow = false;
                }
            });
        stage.addView(made, cover());
        made.heldAt(downRawX, downRawY);
        made.open();
        bloom = made;
        if (fingerDown) {
            bloomFollow = true;
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(now, now,
                android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
    }

    /** Every touch passes here first: where a finger came down, and a finger that belongs to the menu. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        int act = e.getActionMasked();
        if (act == android.view.MotionEvent.ACTION_DOWN) {
            downRawX = e.getRawX();
            downRawY = e.getRawY();
            fingerDown = true;
        }
        if (bloom != null && bloomFollow) {
            if (act == android.view.MotionEvent.ACTION_MOVE) {
                bloom.follow(e.getRawX(), e.getRawY());
            } else if (act == android.view.MotionEvent.ACTION_UP
                || act == android.view.MotionEvent.ACTION_CANCEL) {
                bloomFollow = false;
                fingerDown = false;
                if (act == android.view.MotionEvent.ACTION_UP) {
                    bloom.release(e.getRawX(), e.getRawY());
                }
            }
            return true;
        }
        if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) {
            fingerDown = false;
        }
        return super.dispatchTouchEvent(e);
    }

    // ------------------------------------------------------------------ files

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri where = data.getData();
        if (request == LOAD_MODULE) {
            String text = readText(where);
            if (Words.isModule(text)) {
                Words.read(text);
                Words.save(this);
                relabel();
                flash(Glyph.LANGUAGE, Words.s("module_taken"));
                Trace.note("language: a module taken, " + Words.filled() + " words");
            } else {
                flash(Glyph.CROSS, Words.s("module_bad"));
            }
        } else if (request == SAVE_CARRY) {
            boolean kept = writeText(where, Carry.write(this));
            flash(kept ? Glyph.TICK : Glyph.CROSS, Words.s(kept ? "carry_saved" : "unsaved"));
        } else if (request == LOAD_CARRY) {
            takeIn(readText(where));
        } else if (request == SAVE_FORM) {
            boolean kept = writeText(where, Words.write());
            flash(kept ? Glyph.TICK : Glyph.CROSS, Words.s(kept ? "module_saved" : "unsaved"));
        }
    }

    private String readText(Uri where) {
        try {
            InputStream in = getContentResolver().openInputStream(where);
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int got;
            while ((got = in.read(chunk)) > 0 && all.size() < 2 * 1024 * 1024) {
                all.write(chunk, 0, got);
            }
            in.close();
            return new String(all.toByteArray(), "UTF-8");
        } catch (Exception unread) {
            Trace.note("file: not read, " + unread.getClass().getSimpleName());
            return null;
        }
    }

    private boolean writeText(Uri where, String text) {
        try {
            OutputStream out = getContentResolver().openOutputStream(where, "wt");
            if (out == null) {
                return false;
            }
            out.write(text.getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Exception unwritten) {
            Trace.note("file: not written, " + unwritten.getClass().getSimpleName());
            return false;
        }
    }

    // ------------------------------------------------------------------ a word in passing

    /** A short word in a capsule of the application's own tones, standing clear of the bar. */
    private void flash(int kind, String text) {
        if (note == null) {
            note = new LinearLayout(this);
            note.setOrientation(LinearLayout.HORIZONTAL);
            note.setGravity(Gravity.CENTER_VERTICAL);
            note.setPadding(Round.dp(18), Round.dp(14), Round.dp(24), Round.dp(14));
            note.setVisibility(View.GONE);
            FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            where.bottomMargin = Round.dp(18);
            stage.addView(note, where);
        }
        note.removeAllViews();
        note.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGHEST), Round.FULL));
        note.setElevation(Round.px(6f));
        final Glyph mark = new Glyph(this, kind, Round.px(22f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markParams.rightMargin = Round.dp(12);
        note.addView(mark, markParams);
        TextView says = Letter.set(new TextView(this), Letter.BODY_M);
        says.setText(text);
        says.setTextColor(Tone.of(Tone.ON_SURFACE));
        note.addView(says);
        mark.setScaleX(0.4f);
        mark.setScaleY(0.4f);
        mark.animate().scaleX(1f).scaleY(1f).setStartDelay(Pace.STAGGER).setDuration(Pace.GROW)
            .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
        note.bringToFront();
        note.setVisibility(View.VISIBLE);
        note.setAlpha(0f);
        note.setTranslationY(Round.px(14f));
        note.animate().alpha(1f).translationY(0f).setDuration(Pace.SHEET)
            .setInterpolator(Pace.STANDARD).start();
        note.removeCallbacks(hush);
        note.postDelayed(hush, 1900L);
    }

    private final Runnable hush = new Runnable() {
        public void run() {
            if (note == null) {
                return;
            }
            note.animate().alpha(0f).translationY(Round.px(10f)).setDuration(Pace.LEAVE)
                .setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                    public void run() {
                        note.setVisibility(View.GONE);
                    }
                }).start();
        }
    };
}
