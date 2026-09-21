package io.github.shumtugle.fama;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The one who goes round the channels.
 *
 * It walks the list one channel at a time, never two at once: thirty doors
 * knocked on together look like an attack, and the board is in no hurry.
 * At each it reads the public wall — and, if the channel is a busy one whose
 * last page covers only a few days, the page before — weighs the posts,
 * learns the channel's tag if it keeps one, and hands the meetings to the
 * board. The screen hears of each channel as it is done, so the board fills
 * while it is looked at.
 *
 * It goes on the phone's own network, whatever that is. The board has no
 * server and asks nobody's permission: it reads what anyone can open.
 */
final class Crier {

    /** Who wants to know how the round goes. */
    interface Heard {
        void began(int count);

        void read(int done, int count, String name);

        void ended(int count);
    }

    /** What one channel gave. */
    static final class Reading {
        int state = Board.WAIT;
        Wall wall;
        String learned = "";
        ArrayList<Sift.Bill> bills = new ArrayList<Sift.Bill>();
        /** What the words found on the page. */
        Sift.Tally tally = new Sift.Tally();
    }

    private static final int MOST_BYTES = 4 * 1024 * 1024;
    /** A last page that reaches back fewer days than this asks for the page before it. */
    private static final int BUSY_DAYS = 14;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean busy;
    private static Heard heard;

    private Crier() {
    }

    static boolean busy() {
        return busy;
    }

    static void listen(Heard who) {
        heard = who;
    }

    // ------------------------------------------------------------------ the round

    /** Goes round the channels named, or all of them, unless a round is already under way. */
    static void round(final Context context, final List<String> only) {
        if (busy) {
            return;
        }
        final Context app = context.getApplicationContext();
        final ArrayList<Board.Source> list = new ArrayList<Board.Source>();
        ArrayList<Board.Source> all = Board.sources(app);
        for (int i = 0; i < all.size(); i++) {
            if (only == null || only.contains(all.get(i).name)) {
                list.add(all.get(i));
            }
        }
        if (list.isEmpty()) {
            return;
        }
        busy = true;
        tell(0, list.size(), null, false);
        new Thread(new Runnable() {
            public void run() {
                int opened = 0;
                for (int i = 0; i < list.size(); i++) {
                    Board.Source s = list.get(i);
                    Reading r = safely(s.name, s.tag);
                    Board.Source now = Board.source(app, s.name);
                    if (now == null) {
                        continue;
                    }
                    now.state = r.state;
                    now.readAt = System.currentTimeMillis();
                    if (r.state == Board.OPEN) {
                        opened++;
                        if (r.wall.title.length() > 0) {
                            now.title = r.wall.title;
                        }
                        now.learned = r.learned;
                        now.ahead = r.bills.size();
                        Board.replace(app, now.name, r.bills);
                        keepFace(app, now.name, r.wall.photo, false);
                        Keep.saveTally(app, now.name, r.tally.write());
                    } else if (r.state == Board.GROUP) {
                        /* A group has no wall, but its page still says what it is called and shows its face. */
                        if (r.wall.title.length() > 0) {
                            now.title = r.wall.title;
                        }
                        keepFace(app, now.name, r.wall.photo, false);
                    }
                    Board.put(app, now);
                    Trace.note("read: " + s.name + ", " + stateName(r.state) + ", "
                        + (r.wall == null ? 0 : r.wall.posts.size()) + " posts, "
                        + r.bills.size() + " ahead" + (r.learned.length() > 0 ? ", tag learned" : ""));
                    tell(i + 1, list.size(), s.name, false);
                }
                if (only == null && opened > 0) {
                    Keep.saveReadAt(app, System.currentTimeMillis());
                }
                busy = false;
                tell(list.size(), list.size(), null, true);
            }
        }, "round").start();
    }

    private static void tell(final int done, final int count, final String name, final boolean end) {
        MAIN.post(new Runnable() {
            public void run() {
                Heard who = heard;
                if (who == null) {
                    return;
                }
                if (end) {
                    who.ended(count);
                } else if (name == null) {
                    who.began(count);
                } else {
                    who.read(done, count, name);
                }
            }
        });
    }

    static String stateName(int state) {
        return state == Board.OPEN ? "open" : state == Board.CLOSED ? "closed"
            : state == Board.SILENT ? "silent" : state == Board.GROUP ? "group" : "waiting";
    }

    // ------------------------------------------------------------------ one channel

    /**
     * One channel read so that nothing on its page can bring the application
     * down: whatever breaks is written in the journal, and the channel is
     * taken for one that did not answer, to be read again later.
     */
    static Reading safely(String name, String tag) {
        try {
            return read(name, tag, Calendar.getInstance());
        } catch (Throwable broke) {
            Trace.note("read: " + name + " broke, " + broke.getClass().getSimpleName() + " "
                + String.valueOf(broke.getMessage()));
            Reading r = new Reading();
            r.state = Board.SILENT;
            return r;
        }
    }

    /**
     * One channel read and weighed, away from the screen. A network that does
     * not answer is silence; a page that answers with no wall is a closed door.
     */
    static Reading read(String name, String tag, Calendar now) {
        Reading r = new Reading();
        String html;
        try {
            html = load(Ways.of("wall", name, 0));
        } catch (IOException silent) {
            r.state = Board.SILENT;
            Trace.note("read: " + name + " did not answer, " + silent.getClass().getSimpleName());
            return r;
        }
        r.wall = Wall.read(html);
        if (!r.wall.open()) {
            r.state = r.wall.group ? Board.GROUP : Board.CLOSED;
            return r;
        }
        r.state = Board.OPEN;
        ArrayList<Sift.Post> posts = new ArrayList<Sift.Post>(r.wall.posts);
        if (busyWall(posts, now)) {
            try {
                Wall before = Wall.read(load(Ways.of("before", name, r.wall.earliest())));
                posts.addAll(0, before.posts);
            } catch (IOException unread) {
                Trace.note("read: " + name + ", the page before did not answer");
            }
        }
        r.learned = Sift.learn(posts, now);
        r.bills = Sift.sift(name, posts, tag, r.learned, now, r.tally);
        return r;
    }

    /**
     * One message of a group, read from the page made for embedding it:
     * handed over by hand, so trusted, and weighed for its day. Nothing, if
     * the page did not answer or showed no message.
     */
    static ArrayList<Sift.Bill> single(String name, int id, Calendar now) {
        try {
            Wall one = Wall.read(load(Ways.of("single", name, id)));
            for (int i = 0; i < one.posts.size(); i++) {
                if (one.posts.get(i).id == id) {
                    Trace.note("read: " + name + ", one message read by itself");
                    return Sift.weigh(name, one.posts.get(i), "", "", now, true);
                }
            }
            Trace.note("read: " + name + ", the message page showed no message");
        } catch (IOException silent) {
            Trace.note("read: " + name + ", the message page did not answer");
        } catch (Throwable broke) {
            Trace.note("read: " + name + ", the message page broke, " + broke.getClass().getSimpleName());
        }
        return new ArrayList<Sift.Bill>();
    }

    /** Whether the last page reaches back only a few days: a busy channel, whose announcement may be further down. */
    private static boolean busyWall(List<Sift.Post> posts, Calendar now) {
        if (posts.size() < 12) {
            return false;
        }
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).posted > 0) {
                oldest = Math.min(oldest, posts.get(i).posted);
            }
        }
        return oldest != Long.MAX_VALUE
            && now.getTimeInMillis() - oldest < BUSY_DAYS * 24L * 60L * 60L * 1000L;
    }

    /** A page, as text. Anything but a plain answer is an error the caller hears of. */
    static String load(String address) throws IOException {
        byte[] body = bytes(address);
        return new String(body, "UTF-8");
    }

    private static byte[] bytes(String address) throws IOException {
        if (address == null || address.length() == 0) {
            throw new IOException("no address");
        }
        HttpURLConnection line = (HttpURLConnection) new URL(address).openConnection();
        line.setConnectTimeout(12000);
        line.setReadTimeout(20000);
        line.setInstanceFollowRedirects(true);
        String agent = Ways.get("agent");
        if (agent.length() > 0) {
            line.setRequestProperty("User-Agent", agent);
        }
        line.setRequestProperty("Accept-Language", "ru, en;q=0.8");
        try {
            int code = line.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("answered " + code);
            }
            InputStream in = line.getInputStream();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int got;
            while ((got = in.read(chunk)) > 0) {
                all.write(chunk, 0, got);
                if (all.size() > MOST_BYTES) {
                    break;
                }
            }
            in.close();
            return all.toByteArray();
        } finally {
            line.disconnect();
        }
    }

    // ------------------------------------------------------------------ faces

    /**
     * A channel's picture, fetched once and kept small and square: the middle
     * of it, a hundred and forty four across, enough for a disc on a card.
     * Fetched again after a fortnight, or when asked.
     */
    static void keepFace(Context context, String name, String address, boolean again) {
        if (address == null || address.length() == 0) {
            return;
        }
        File file = Board.face(context, name);
        long age = System.currentTimeMillis() - file.lastModified();
        if (!again && file.isFile() && age < BUSY_DAYS * 24L * 60L * 60L * 1000L) {
            return;
        }
        try {
            byte[] body = bytes(address);
            Bitmap whole = BitmapFactory.decodeByteArray(body, 0, body.length);
            if (whole == null) {
                return;
            }
            int side = Math.min(whole.getWidth(), whole.getHeight());
            int left = (whole.getWidth() - side) / 2;
            int top = (whole.getHeight() - side) / 2;
            Bitmap square = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
            new Canvas(square).drawBitmap(whole, new Rect(left, top, left + side, top + side),
                new Rect(0, 0, 144, 144), new Paint(Paint.FILTER_BITMAP_FLAG));
            whole.recycle();
            FileOutputStream out = new FileOutputStream(file);
            square.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            square.recycle();
            Faces.forget(name);
        } catch (Exception unfetched) {
            Trace.note("face: " + name + " not fetched, " + unfetched.getClass().getSimpleName());
        }
    }
}
