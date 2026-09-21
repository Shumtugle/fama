package io.github.shumtugle.fama;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * The sheet that rises when something is shared to the board.
 *
 * It opens over whatever shared it, the messenger most likely, and asks one
 * thing. A channel's link: the sheet reads the channel there and then, shows
 * its face and name and the nearest meeting it announces, and offers one
 * capsule, to the board. A plain text with no link — an announcement copied
 * from anywhere — is weighed as it is, trusted since a hand chose it, and
 * its meeting offered the same way. A link the board cannot follow, a closed
 * channel or an invitation, says so and offers only to close.
 *
 * Taken, the capsule turns into a tick, the sheet sinks, and the messenger
 * is where it was. The board learns the rest by itself.
 */
public final class Take extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private View scrim;
    private LinearLayout sheet;
    private Faces.Disc face;
    private LinearLayout faceSlot;
    private TextView title;
    private TextView under;
    private LinearLayout body;
    private TextView take;
    private boolean leaving;

    /** What would go on the board: a channel, or meetings handed over by hand. */
    private Board.Source channel;
    private ArrayList<Sift.Bill> found = new ArrayList<Sift.Bill>();
    private boolean byHand;
    /** The number of the one message a link pointed at, or nought. */
    private int pointedPost;
    /** One message of a group, to go on the board by itself. */
    private boolean groupHand;
    /** A whole board carried over, to be taken in. */
    private Carry.Load carried;
    /** Many channels named in one text, those not on the board yet. */
    private ArrayList<String> many;
    /** What the capsule offers. */
    private String offer;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Trace.watch(getApplicationContext());
        Round.measure(this);
        Tone.read(this);
        Words.load(this);
        Ways.ready(this);
        build();
        rise();
        handle(getIntent());
    }

    // ------------------------------------------------------------------ the sheet

    private void build() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        root = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(0x80000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sink();
            }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(Round.sheet(Tone.of(Tone.SURFACE_CONTAINER), Round.XL));
        sheet.setPadding(Round.dp(24), Round.dp(12), Round.dp(24), Round.dp(24));

        View handle = new View(this);
        handle.setBackground(Round.box(Tone.of(Tone.OUTLINE_VARIANT), Round.FULL));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(Round.dp(32), Round.dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, handleParams);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        faceSlot = new LinearLayout(this);
        head.addView(faceSlot, new LinearLayout.LayoutParams(Round.dp(56), Round.dp(56)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        title = Letter.set(new TextView(this), Letter.TITLE_L);
        title.setTextColor(Tone.of(Tone.ON_SURFACE));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(title);
        under = Letter.set(new TextView(this), Letter.BODY_M);
        under.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        under.setSingleLine(true);
        LinearLayout.LayoutParams underParams = wide();
        underParams.topMargin = Round.dp(2);
        names.addView(under, underParams);
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        namesParams.leftMargin = Round.dp(16);
        head.addView(names, namesParams);
        LinearLayout.LayoutParams headParams = wide();
        headParams.topMargin = Round.dp(20);
        sheet.addView(head, headParams);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setMinimumHeight(Round.dp(72));
        LinearLayout.LayoutParams bodyParams = wide();
        bodyParams.topMargin = Round.dp(20);
        sheet.addView(body, bodyParams);

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView close = Letter.set(new TextView(this), Letter.LABEL_L);
        close.setText(Words.s("close"));
        close.setGravity(Gravity.CENTER);
        close.setPadding(Round.dp(20), 0, Round.dp(20), 0);
        close.setTextColor(Tone.of(Tone.PRIMARY));
        close.setBackground(Round.touch(null, Tone.of(Tone.PRIMARY), Round.FULL));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sink();
            }
        });
        keys.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            Round.dp(48)));
        take = Letter.set(new TextView(this), Letter.LABEL_L);
        take.setGravity(Gravity.CENTER);
        take.setPadding(Round.dp(28), 0, Round.dp(28), 0);
        take.setVisibility(View.GONE);
        take.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                keepIt();
            }
        });
        LinearLayout.LayoutParams takeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(48));
        takeParams.leftMargin = Round.dp(8);
        keys.addView(take, takeParams);
        LinearLayout.LayoutParams keysParams = wide();
        keysParams.topMargin = Round.dp(24);
        sheet.addView(keys, keysParams);

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        root.addView(sheet, sheetParams);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int bottom = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.systemBars()).bottom
                    : insets.getSystemWindowInsetBottom();
                sheet.setPadding(Round.dp(24), Round.dp(12), Round.dp(24), Round.dp(24) + bottom);
                return insets;
            }
        });
        setContentView(root);
        root.requestApplyInsets();
        paintTake(false, false);
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** The sheet rises from the edge, the room behind it darkening. */
    private void rise() {
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
        sheet.setTranslationY(Round.px(420f));
        sheet.animate().translationY(0f).setStartDelay(40L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.EMPHASIS).start();
    }

    /** It sinks back the way it came, and the window goes with it. */
    private void sink() {
        if (leaving) {
            return;
        }
        leaving = true;
        scrim.animate().alpha(0f).setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).start();
        sheet.animate().translationY(sheet.getHeight() + Round.px(40f)).setStartDelay(0L)
            .setDuration(Pace.LEAVE + 60L).setInterpolator(Pace.AWAY)
            .withEndAction(new Runnable() {
                public void run() {
                    finish();
                    overridePendingTransition(0, 0);
                }
            }).start();
    }

    @Override
    public void onBackPressed() {
        sink();
    }

    // ------------------------------------------------------------------ what came

    private void handle(Intent intent) {
        CharSequence handed = intent == null ? null : intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String text = handed == null ? "" : handed.toString();
        if (Carry.is(text)) {
            Trace.note("take: a carried board");
            takeCarry(text);
            return;
        }
        ArrayList<String> named = Ways.channels(text);
        if (named.size() >= 2) {
            Trace.note("take: " + named.size() + " channels at once");
            takeMany(named);
            return;
        }
        Ways.Pointed pointed = Ways.pointed(text);
        Trace.note("take: " + (pointed.kind == Ways.Pointed.CHANNEL ? "a channel"
            : pointed.kind == Ways.Pointed.CLOSED ? "a closed link"
            : pointed.kind == Ways.Pointed.FOLDER ? "a folder" : "a text"));
        if (pointed.kind == Ways.Pointed.CHANNEL) {
            pointedPost = pointed.post;
            readChannel(pointed.name);
        } else if (pointed.kind == Ways.Pointed.CLOSED || pointed.kind == Ways.Pointed.FOLDER) {
            showFace("", "\u00B7");
            title.setText(Words.s("take_note"));
            under.setText("");
            say(Words.s(pointed.kind == Ways.Pointed.CLOSED ? "take_closed" : "take_folder"),
                Tone.ON_SURFACE);
        } else {
            takeText(text);
        }
    }

    /** A channel read while the sheet stands: its face, its name, its nearest meeting. */
    private void readChannel(final String name) {
        final Board.Source there = Board.source(this, name);
        showFace(name, there == null ? name : there.shown());
        title.setText(there == null ? name : there.shown());
        under.setText("@" + name);
        waiting();
        final String tag = there == null ? "" : there.tag;
        final int post = pointedPost;
        new Thread(new Runnable() {
            public void run() {
                final Crier.Reading r = Crier.safely(name, tag);
                /* A group has no wall; the one message the link pointed at is read by itself. */
                final ArrayList<Sift.Bill> one = r.state == Board.GROUP && post > 0
                    ? Crier.single(name, post, Calendar.getInstance()) : null;
                if (r.state == Board.OPEN || r.state == Board.GROUP) {
                    try {
                        Crier.keepFace(getApplicationContext(), name, r.wall.photo, false);
                    } catch (Throwable unfetched) {
                        Trace.note("take: the face broke, " + unfetched.getClass().getSimpleName());
                    }
                }
                main.post(new Runnable() {
                    public void run() {
                        if (!isFinishing()) {
                            read(name, there, r, one);
                        }
                    }
                });
            }
        }, "take").start();
    }

    private void read(String name, Board.Source there, Crier.Reading r, ArrayList<Sift.Bill> one) {
        Trace.note("take: " + name + ", " + Crier.stateName(r.state) + ", " + r.bills.size() + " ahead");
        Board.Source s = there != null ? there : new Board.Source();
        s.name = there != null ? there.name : name;
        s.state = r.state;
        s.readAt = System.currentTimeMillis();
        if (s.added == 0L) {
            s.added = s.readAt;
        }
        if (r.state == Board.OPEN || r.state == Board.GROUP) {
            if (r.wall.title.length() > 0) {
                s.title = r.wall.title;
            }
            if (r.state == Board.OPEN) {
                s.learned = r.learned;
                s.ahead = r.bills.size();
            }
            Faces.forget(name);
            showFace(name, s.shown());
            title.setText(s.shown());
        }
        channel = s;
        found = r.bills;
        body.removeAllViews();
        /* A group: its one message, if the link pointed at one that names a
           day ahead, goes on the board by itself; the group as a whole cannot. */
        if (r.state == Board.GROUP) {
            if (there != null) {
                Board.put(this, s);
            }
            if (one != null && !one.isEmpty()) {
                groupHand = true;
                found = one;
                Sift.order(found, null);
                nearest(found.get(0), s.shown());
                paintTake(false, false);
                showTake();
            } else {
                say(Words.s(one == null ? "take_group" : "take_group_none"), Tone.TERTIARY);
            }
            return;
        }
        /* A page that did not read is said out loud, and the channel can still
           go on the board: the board will try again, and the journal will say why. */
        if (r.state == Board.SILENT) {
            say(Words.s("take_silent"), Tone.TERTIARY);
        } else if (r.state == Board.CLOSED) {
            say(Words.s("take_unread"), Tone.TERTIARY);
        } else if (found.isEmpty()) {
            say(Words.s("take_none"), Tone.ON_SURFACE_VARIANT);
        } else {
            nearest(found.get(0), s.shown());
        }
        if (there != null) {
            /* Already on the board: it was read anyway, so the board hears the news. */
            if (r.state == Board.OPEN) {
                Board.put(this, s);
                Board.replace(this, s.name, found);
            }
            paintTake(false, true);
            take.setVisibility(View.VISIBLE);
            take.setEnabled(false);
        } else {
            paintTake(false, false);
            showTake();
        }
    }

    /** A board carried over: what it holds, and one capsule to take it in. */
    private void takeCarry(String text) {
        carried = Carry.read(text);
        showFace("", Words.s("carry"));
        title.setText(Words.s("carry"));
        under.setText(Words.s("carry_n").replace("{c}", String.valueOf(carried.sources.size()))
            .replace("{b}", String.valueOf(carried.bills.size()))
            .replace("{h}", String.valueOf(carried.hidden.size())));
        body.removeAllViews();
        say(Words.s("carry_sheet"), Tone.ON_SURFACE_VARIANT);
        offer = Words.s("carry_take");
        paintTake(false, false);
        showTake();
    }

    /** A text naming many channels, a list of clubs: those not on the board yet, added at once. */
    private void takeMany(ArrayList<String> named) {
        many = new ArrayList<String>();
        int there = 0;
        for (int i = 0; i < named.size(); i++) {
            if (Board.source(this, named.get(i)) == null) {
                many.add(named.get(i));
            } else {
                there++;
            }
        }
        showFace("", Words.s("sources"));
        title.setText(Words.s("sources"));
        under.setText(Words.s("many_n").replace("{n}", String.valueOf(named.size())));
        body.removeAllViews();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < many.size() && i < 24; i++) {
            if (i > 0) {
                names.append("  \u00B7  ");
            }
            names.append('@').append(many.get(i));
        }
        if (many.size() > 24) {
            names.append("  \u2026");
        }
        if (there > 0) {
            names.append(names.length() > 0 ? "\n\n" : "")
                .append(Words.s("many_there").replace("{n}", String.valueOf(there)));
        }
        say(names.toString(), Tone.ON_SURFACE);
        if (many.isEmpty()) {
            paintTake(false, true);
            take.setVisibility(View.VISIBLE);
            take.setEnabled(false);
        } else {
            offer = Words.s("add_all");
            paintTake(false, false);
            showTake();
        }
    }

    /** A text with no link: an announcement handed over by hand, trusted, and weighed for its day. */
    private void takeText(String text) {
        byHand = true;
        showFace("", Words.s("by_hand"));
        title.setText(Words.s("take_note"));
        under.setText(Words.s("by_hand"));
        long now = System.currentTimeMillis();
        Sift.Post post = new Sift.Post((int) ((now / 1000L) & 0x7FFFFFFF), text, now);
        found = Sift.weigh("", post, "", "", Calendar.getInstance(), true);
        if (found.isEmpty()) {
            say(Words.s("take_no_day"), Tone.ON_SURFACE);
            return;
        }
        Sift.order(found, null);
        body.removeAllViews();
        nearest(found.get(0), Words.s("by_hand"));
        paintTake(false, false);
        showTake();
    }

    private void showFace(String name, String shown) {
        faceSlot.removeAllViews();
        face = new Faces.Disc(this, name, shown, Round.px(56f));
        faceSlot.addView(face);
    }

    /** The reading is under way: the turning circle, and a word for it. */
    private void waiting() {
        body.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Blob turning = new Blob(this, Round.px(40f), Tone.of(Tone.SURFACE_HIGH),
            Tone.of(Tone.PRIMARY));
        turning.setClickable(false);
        turning.busy(true);
        row.addView(turning);
        TextView said = Letter.set(new TextView(this), Letter.BODY_M);
        said.setText(Words.s("take_reading"));
        said.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        LinearLayout.LayoutParams saidParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saidParams.leftMargin = Round.dp(14);
        row.addView(said, saidParams);
        body.addView(row, wide());
    }

    private void say(String words, int role) {
        body.removeAllViews();
        TextView said = Letter.set(new TextView(this), Letter.BODY_L);
        said.setText(words);
        said.setTextColor(Tone.of(role));
        body.addView(said, wide());
        arrive(said);
    }

    /** The nearest meeting, as a small card: the hour, the day, the book or the line, the place. */
    private void nearest(Sift.Bill b, String who) {
        TextView label = Letter.set(new TextView(this), Letter.LABEL_L);
        label.setText(Words.s("take_next"));
        label.setTextColor(Tone.of(Tone.PRIMARY));
        body.addView(label, wide());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(Round.dp(16), Round.dp(14), Round.dp(16), Round.dp(14));
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.L));
        TextView hour = Letter.serif(Letter.set(new TextView(this), Letter.TITLE_L));
        hour.setText(b.minutes >= 0 ? b.minutes / 60 + ":" + (b.minutes % 60 < 10 ? "0" : "")
            + b.minutes % 60 : "\u00B7");
        hour.setTextColor(Tone.of(Tone.PRIMARY));
        card.addView(hour, new LinearLayout.LayoutParams(Round.dp(66),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        Calendar c = Days.calendar(b.day);
        int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        TextView day = Letter.set(new TextView(this), Letter.TITLE_M);
        day.setText(Words.s("day_" + dow) + ", " + c.get(Calendar.DAY_OF_MONTH) + " "
            + Words.s("month_" + (c.get(Calendar.MONTH) + 1)));
        day.setTextColor(Tone.of(Tone.ON_SURFACE));
        said.addView(day);
        if (b.book.length() > 0) {
            TextView book = Letter.serif(Letter.set(new TextView(this), Letter.BODY_L));
            book.setText("\u00AB" + b.book + "\u00BB");
            book.setTextColor(Tone.of(Tone.ON_SURFACE));
            book.setMaxLines(2);
            LinearLayout.LayoutParams p = wide();
            p.topMargin = Round.dp(4);
            said.addView(book, p);
        } else if (b.line.length() > 0) {
            TextView gist = Letter.set(new TextView(this), Letter.BODY_M);
            gist.setText(b.line);
            gist.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
            gist.setMaxLines(2);
            LinearLayout.LayoutParams p = wide();
            p.topMargin = Round.dp(4);
            said.addView(gist, p);
        }
        if (b.place.length() > 0) {
            TextView place = Letter.set(new TextView(this), Letter.BODY_S);
            place.setText(b.place);
            place.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
            place.setSingleLine(true);
            place.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams p = wide();
            p.topMargin = Round.dp(4);
            said.addView(place, p);
        }
        if (b.cancelled || b.moved) {
            TextView flag = Letter.set(new TextView(this), Letter.LABEL_M);
            flag.setText(Words.s(b.cancelled ? "cancelled" : "moved"));
            flag.setTextColor(b.cancelled ? Bloom.errorInk() : Tone.of(Tone.TERTIARY));
            LinearLayout.LayoutParams p = wide();
            p.topMargin = Round.dp(6);
            said.addView(flag, p);
        }
        card.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams cardParams = wide();
        cardParams.topMargin = Round.dp(8);
        body.addView(card, cardParams);
        arrive(card);
    }

    private void arrive(View v) {
        v.setAlpha(0f);
        v.setTranslationY(Round.px(14f));
        v.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
    }

    // ------------------------------------------------------------------ the capsule

    private void showTake() {
        take.setVisibility(View.VISIBLE);
        take.setAlpha(0f);
        take.setScaleX(0.9f);
        take.setScaleY(0.9f);
        take.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.EMPHASIS).start();
    }

    /** The capsule in its three states: offered, already there, and taken. */
    private void paintTake(boolean taken, boolean already) {
        if (taken || already) {
            take.setText((taken ? "\u2713  " : "") + Words.s(taken ? "take_added" : "take_there"));
            take.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
            take.setBackground(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL));
        } else {
            take.setText(offer != null ? offer : Words.s("take_add"));
            take.setTextColor(Tone.of(Tone.ON_PRIMARY));
            take.setBackground(Round.touch(Round.box(Tone.of(Tone.PRIMARY), Round.FULL),
                Tone.of(Tone.ON_PRIMARY), Round.FULL));
        }
    }

    /** To the board: kept, the capsule turns, and the sheet sinks a moment later. */
    private void keepIt() {
        if (leaving || !take.isEnabled()) {
            return;
        }
        take.setEnabled(false);
        if (carried != null) {
            Carry.Taken taken = Carry.apply(this, carried);
            if (!taken.channels.isEmpty()) {
                Crier.round(getApplicationContext(), taken.channels);
            }
        } else if (many != null) {
            long now = System.currentTimeMillis();
            ArrayList<Board.Source> fresh = new ArrayList<Board.Source>();
            for (int i = 0; i < many.size(); i++) {
                Board.Source s = new Board.Source();
                s.name = many.get(i);
                s.added = now;
                s.state = Board.WAIT;
                fresh.add(s);
            }
            ArrayList<String> came = Board.welcome(this, fresh);
            Trace.note("take: " + came.size() + " channels added at once");
            if (!came.isEmpty()) {
                Crier.round(getApplicationContext(), came);
            }
        } else if (byHand) {
            Board.add(this, found);
            Trace.note("take: a meeting kept by hand");
        } else if (groupHand && channel != null) {
            Board.put(this, channel);
            Board.add(this, found);
            Trace.note("take: a group's message kept, " + found.size() + " ahead");
        } else if (channel != null) {
            Board.put(this, channel);
            if (channel.state == Board.OPEN) {
                Board.replace(this, channel.name, found);
            }
            Trace.note("take: a channel kept, " + found.size() + " ahead");
        }
        take.performHapticFeedback(Build.VERSION.SDK_INT >= 30
            ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        take.animate().scaleX(0.92f).scaleY(0.92f).setDuration(Pace.PRESS)
            .setInterpolator(Pace.STANDARD).withEndAction(new Runnable() {
                public void run() {
                    paintTake(true, false);
                    take.animate().scaleX(1f).scaleY(1f).setDuration(Pace.GROW)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                }
            }).start();
        main.postDelayed(new Runnable() {
            public void run() {
                sink();
            }
        }, 900L);
    }

    @Override
    protected void onPause() {
        Trace.keep(this);
        super.onPause();
    }
}
