package io.github.shumtugle.fama;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the board holds: the channels it reads, the meetings it found in
 * them, and the meetings taken off it by hand.
 *
 * It is one small file, read once and kept in memory, written whole after
 * every change through a file beside it, so a phone that dies mid-write
 * leaves the old board and not half of a new one. A meeting whose day has
 * passed is dropped when the board is read; one taken off by hand stays off
 * until its day passes too.
 */
final class Board {

    static final int WAIT = 0;
    static final int OPEN = 1;
    static final int CLOSED = 2;
    static final int SILENT = 3;
    /** A group, not a channel: its talk has no public wall, only its messages one by one. */
    static final int GROUP = 4;

    /** A channel the board reads. */
    static final class Source {
        String name = "";
        String title = "";
        String tag = "";
        String learned = "";
        long added;
        long readAt;
        int state = WAIT;
        int ahead;

        String shown() {
            return title.length() > 0 ? title : name;
        }
    }

    private static final String FILE = "board.json";
    /** How many times the board has changed in this run: a screen drawn from it knows if it is behind. */
    private static int version;
    private static ArrayList<Source> sources;
    private static ArrayList<Sift.Bill> bills;
    private static HashSet<String> hidden;

    private Board() {
    }

    // ------------------------------------------------------------------ reading

    private static void ready(Context context) {
        if (sources != null) {
            return;
        }
        sources = new ArrayList<Source>();
        bills = new ArrayList<Sift.Bill>();
        hidden = new HashSet<String>();
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) {
            return;
        }
        try {
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int got;
            while ((got = in.read(chunk)) > 0) {
                all.write(chunk, 0, got);
            }
            in.close();
            JSONObject root = new JSONObject(new String(all.toByteArray(), "UTF-8"));
            JSONArray s = root.optJSONArray("sources");
            for (int i = 0; s != null && i < s.length(); i++) {
                JSONObject o = s.getJSONObject(i);
                Source one = new Source();
                one.name = o.optString("name");
                one.title = o.optString("title");
                one.tag = o.optString("tag");
                one.learned = o.optString("learned");
                one.added = o.optLong("added");
                one.readAt = o.optLong("readAt");
                one.state = o.optInt("state", WAIT);
                one.ahead = o.optInt("ahead");
                if (one.name.length() > 0) {
                    sources.add(one);
                }
            }
            int today = Days.stamp(Calendar.getInstance());
            JSONArray b = root.optJSONArray("bills");
            for (int i = 0; b != null && i < b.length(); i++) {
                Sift.Bill one = bill(b.getJSONObject(i));
                if (one.day >= today) {
                    bills.add(one);
                }
            }
            JSONArray h = root.optJSONArray("hidden");
            for (int i = 0; h != null && i < h.length(); i++) {
                String key = h.getString(i);
                if (dayOf(key) >= today) {
                    hidden.add(key);
                }
            }
        } catch (Exception broken) {
            Trace.note("board: not read, " + broken.getClass().getSimpleName());
        }
    }

    /** The day a stored key speaks of: the third of its four parts. */
    private static int dayOf(String key) {
        String[] parts = key.split("/");
        if (parts.length < 3) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException odd) {
            return 0;
        }
    }

    /** A meeting as the board keeps it: one object, its words and links with it. */
    static JSONObject json(Sift.Bill one) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("source", one.source);
        o.put("post", one.post);
        o.put("day", one.day);
        o.put("minutes", one.minutes);
        o.put("book", one.book);
        o.put("place", one.place);
        o.put("line", one.line);
        o.put("cancelled", one.cancelled);
        o.put("moved", one.moved);
        o.put("posted", one.posted);
        o.put("weight", one.weight);
        if (one.text.length() > 0) {
            o.put("text", one.text);
        }
        if (one.note.length() > 0) {
            o.put("note", one.note);
        }
        if (!one.links.isEmpty()) {
            JSONArray ls = new JSONArray();
            for (int k = 0; k < one.links.size(); k++) {
                JSONArray pair = new JSONArray();
                pair.put(one.links.get(k)[0]);
                pair.put(one.links.get(k)[1]);
                ls.put(pair);
            }
            o.put("links", ls);
        }
        return o;
    }

    /** A meeting read back from its object. */
    static Sift.Bill bill(JSONObject o) {
        Sift.Bill one = new Sift.Bill();
        one.source = o.optString("source");
        one.post = o.optInt("post");
        one.day = o.optInt("day");
        one.minutes = o.optInt("minutes", -1);
        one.book = o.optString("book");
        one.place = o.optString("place");
        one.line = o.optString("line");
        one.cancelled = o.optBoolean("cancelled");
        one.moved = o.optBoolean("moved");
        one.posted = o.optLong("posted");
        one.weight = o.optInt("weight");
        one.text = o.optString("text");
        one.note = o.optString("note");
        JSONArray ls = o.optJSONArray("links");
        for (int k = 0; ls != null && k < ls.length(); k++) {
            JSONArray pair = ls.optJSONArray(k);
            if (pair != null && pair.length() == 2) {
                one.links.add(new String[] {pair.optString(0), pair.optString(1)});
            }
        }
        return one;
    }

    static synchronized int version() {
        return version;
    }

    private static void write(Context context) {
        version++;
        try {
            JSONObject root = new JSONObject();
            JSONArray s = new JSONArray();
            for (int i = 0; i < sources.size(); i++) {
                Source one = sources.get(i);
                JSONObject o = new JSONObject();
                o.put("name", one.name);
                o.put("title", one.title);
                o.put("tag", one.tag);
                o.put("learned", one.learned);
                o.put("added", one.added);
                o.put("readAt", one.readAt);
                o.put("state", one.state);
                o.put("ahead", one.ahead);
                s.put(o);
            }
            root.put("sources", s);
            JSONArray b = new JSONArray();
            for (int i = 0; i < bills.size(); i++) {
                b.put(json(bills.get(i)));
            }
            root.put("bills", b);
            JSONArray h = new JSONArray();
            for (String key : hidden) {
                h.put(key);
            }
            root.put("hidden", h);
            File dir = context.getFilesDir();
            File next = new File(dir, FILE + ".new");
            FileOutputStream out = new FileOutputStream(next);
            out.write(root.toString().getBytes("UTF-8"));
            out.getFD().sync();
            out.close();
            if (!next.renameTo(new File(dir, FILE))) {
                Trace.note("board: the new file did not take the old one's place");
            }
        } catch (Exception broken) {
            Trace.note("board: not written, " + broken.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ channels

    static synchronized ArrayList<Source> sources(Context context) {
        ready(context);
        return new ArrayList<Source>(sources);
    }

    static synchronized Source source(Context context, String name) {
        ready(context);
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).name.equalsIgnoreCase(name)) {
                return sources.get(i);
            }
        }
        return null;
    }

    /** The titles of the channels by name, for ordering and for cards. */
    static synchronized Map<String, String> titles(Context context) {
        ready(context);
        HashMap<String, String> out = new HashMap<String, String>();
        for (int i = 0; i < sources.size(); i++) {
            out.put(sources.get(i).name, sources.get(i).shown());
        }
        return out;
    }

    /** A channel added, or the one of that name told what it is now. */
    static synchronized void put(Context context, Source one) {
        ready(context);
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).name.equalsIgnoreCase(one.name)) {
                sources.set(i, one);
                write(context);
                return;
            }
        }
        sources.add(one);
        write(context);
    }

    /** A channel no longer read: its meetings go with it, and its face. */
    static synchronized void drop(Context context, String name) {
        ready(context);
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (sources.get(i).name.equalsIgnoreCase(name)) {
                sources.remove(i);
            }
        }
        for (int i = bills.size() - 1; i >= 0; i--) {
            if (bills.get(i).source.equalsIgnoreCase(name)) {
                bills.remove(i);
            }
        }
        face(context, name).delete();
        write(context);
    }

    // ------------------------------------------------------------------ meetings

    /** Every meeting still ahead and not taken off, in the order of the calendar. */
    static synchronized ArrayList<Sift.Bill> bills(Context context) {
        ready(context);
        int today = Days.stamp(Calendar.getInstance());
        ArrayList<Sift.Bill> out = new ArrayList<Sift.Bill>();
        for (int i = 0; i < bills.size(); i++) {
            Sift.Bill b = bills.get(i);
            if (b.day >= today && !hidden.contains(b.key())) {
                out.add(b);
            }
        }
        Sift.order(out, titles(context));
        return out;
    }

    /** A channel read again: its meetings are what the reading found, no more and no fewer. */
    static synchronized void replace(Context context, String source, List<Sift.Bill> found) {
        ready(context);
        for (int i = bills.size() - 1; i >= 0; i--) {
            if (bills.get(i).source.equalsIgnoreCase(source)) {
                bills.remove(i);
            }
        }
        bills.addAll(found);
        write(context);
    }

    /** Meetings handed over by hand, kept beside the channels' own until their day. */
    static synchronized int add(Context context, List<Sift.Bill> found) {
        ready(context);
        int came = 0;
        for (int i = 0; i < found.size(); i++) {
            Sift.Bill b = found.get(i);
            boolean there = false;
            for (int k = 0; k < bills.size() && !there; k++) {
                there = bills.get(k).key().equals(b.key());
            }
            if (!there) {
                bills.add(b);
                came++;
            }
        }
        write(context);
        return came;
    }

    // ------------------------------------------------------------------ what is carried

    /**
     * The meetings no channel can tell again: handed over by hand, or one
     * message of a group. Everything else comes back with the first reading.
     */
    static synchronized ArrayList<Sift.Bill> kept(Context context) {
        ready(context);
        HashSet<String> groups = new HashSet<String>();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).state == GROUP) {
                groups.add(sources.get(i).name.toLowerCase(Locale.ROOT));
            }
        }
        int today = Days.stamp(Calendar.getInstance());
        ArrayList<Sift.Bill> out = new ArrayList<Sift.Bill>();
        for (int i = 0; i < bills.size(); i++) {
            Sift.Bill b = bills.get(i);
            if (b.day >= today && (b.source.length() == 0
                || groups.contains(b.source.toLowerCase(Locale.ROOT)))) {
                out.add(b);
            }
        }
        return out;
    }

    /** The meetings taken off by hand whose days are still ahead. */
    static synchronized ArrayList<String> hidden(Context context) {
        ready(context);
        return new ArrayList<String>(hidden);
    }

    /** Meetings taken off elsewhere, taken off here too; how many were new. */
    static synchronized int hideAll(Context context, List<String> keys) {
        ready(context);
        int today = Days.stamp(Calendar.getInstance());
        int came = 0;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (dayOf(key) >= today && hidden.add(key)) {
                came++;
            }
        }
        write(context);
        return came;
    }

    /** Channels that are not on the board yet put on it; one already there keeps what it has. */
    static synchronized ArrayList<String> welcome(Context context, List<Source> some) {
        ready(context);
        ArrayList<String> came = new ArrayList<String>();
        for (int i = 0; i < some.size(); i++) {
            Source one = some.get(i);
            boolean there = false;
            for (int k = 0; k < sources.size() && !there; k++) {
                there = sources.get(k).name.equalsIgnoreCase(one.name);
            }
            if (!there && one.name.length() > 0) {
                sources.add(one);
                came.add(one.name);
            }
        }
        write(context);
        return came;
    }

    static synchronized void hide(Context context, Sift.Bill bill) {
        ready(context);
        hidden.add(bill.key());
        if (bill.source.length() == 0) {
            bills.remove(bill);
            for (int i = bills.size() - 1; i >= 0; i--) {
                if (bills.get(i).key().equals(bill.key())) {
                    bills.remove(i);
                }
            }
        }
        write(context);
    }

    // ------------------------------------------------------------------ faces

    /** Where a channel's picture is kept, whether or not it is there yet. */
    static File face(Context context, String name) {
        File dir = new File(context.getFilesDir(), "faces");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return new File(dir, name.toLowerCase(java.util.Locale.ROOT) + ".png");
    }
}
