package io.github.shumtugle.fama;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the board reads from, and the marks it reads a page by.
 *
 * None of it is in the source. The addresses belong to someone else's
 * service and the marks to someone else's page; either may change on a
 * morning nobody chose, and when it does, the file beside the code is
 * mended, not the code. The source knows only that a channel has a wall,
 * that a wall has posts, and that a post has words and a moment.
 */
final class Ways {

    private static final Map<String, String> SAID = new HashMap<String, String>();

    private Ways() {
    }

    /**
     * The ways and the lexicon read from the package, once for the life of
     * the process: the board and the sheet that takes a shared channel both
     * need them, and whichever opens first reads them for both.
     */
    static synchronized void ready(android.content.Context context) {
        if (!SAID.isEmpty() && Lex.ready()) {
            return;
        }
        try {
            read(context.getAssets().open("ways.txt"));
            Lex.read(context.getAssets().open("lexicon.txt"));
            Lex.layer(Keep.words(context));
        } catch (Exception unread) {
            Trace.note("ways: the package did not give them, " + unread.getClass().getSimpleName());
        }
    }

    static synchronized void read(InputStream in) {
        SAID.clear();
        if (in == null) {
            return;
        }
        try {
            BufferedReader lines = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.trim().length() == 0 || line.trim().charAt(0) == '#') {
                    continue;
                }
                int cut = line.indexOf('\t');
                if (cut <= 0) {
                    continue;
                }
                SAID.put(line.substring(0, cut).trim(), line.substring(cut + 1).trim());
            }
            lines.close();
        } catch (Exception unread) {
            Trace.note("ways: not read, " + unread.getClass().getSimpleName());
        }
    }

    static synchronized String get(String key) {
        String v = SAID.get(key);
        return v == null ? "" : v;
    }

    /** An address with a channel's name, and a post's number where it asks for one. */
    static String of(String key, String name, int id) {
        return get(key).replace("{name}", name).replace("{id}", String.valueOf(id));
    }

    /** The parts of a value parted by spaces. */
    static String[] list(String key) {
        String v = get(key);
        return v.length() == 0 ? new String[0] : v.split("\\s+");
    }

    // ------------------------------------------------------------ a shared link

    /** What a shared text points at. */
    static final class Pointed {
        static final int NOTHING = 0;
        static final int CHANNEL = 1;
        static final int CLOSED = 2;
        static final int FOLDER = 3;

        int kind = NOTHING;
        String name = "";
        /** The number of the message the link points at, or nought for the whole channel. */
        int post;
    }

    /**
     * The first link in a text that goes to one of the hosts, taken apart: a
     * public channel's short name, or a link that leads somewhere the board
     * cannot follow — a closed channel, an invitation, a folder.
     */
    static Pointed pointed(String text) {
        Pointed out = new Pointed();
        if (text == null) {
            return out;
        }
        String[] hosts = list("hosts");
        if (hosts.length == 0) {
            return out;
        }
        Matcher m = link(hosts).matcher(text);
        while (m.find()) {
            String first = m.group(1);
            if (first.startsWith("+") || contains(list("closed"), first)) {
                out.kind = Pointed.CLOSED;
                return out;
            }
            if (contains(list("folder"), first)) {
                out.kind = Pointed.FOLDER;
                return out;
            }
            if (contains(list("reserved"), first) || first.length() < 4) {
                continue;
            }
            out.kind = Pointed.CHANNEL;
            out.name = first;
            out.post = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            return out;
        }
        return out;
    }

    /** A link to any of the hosts: its first part, and the number of one message if it has one. */
    private static Pattern link(String[] hosts) {
        StringBuilder any = new StringBuilder();
        for (int i = 0; i < hosts.length; i++) {
            if (i > 0) {
                any.append('|');
            }
            any.append(Pattern.quote(hosts[i]));
        }
        return Pattern.compile("(?i)(?:https?://)?(?:www\\.)?(?:" + any
            + ")/(?:s/)?([+A-Za-z0-9_]{1,64})(?:/(\\d{1,9}))?");
    }

    /** A short name written as it is said aloud, with the sign before it; not the middle of an address. */
    private static final Pattern HANDLE = Pattern.compile("(?<![\\w@.])@([A-Za-z][A-Za-z0-9_]{3,63})");

    /**
     * Every public channel a text names, in the order it names them, each
     * once: its links, and its short names written with the sign before
     * them. A list of clubs pasted from anywhere is read this way.
     */
    static ArrayList<String> channels(String text) {
        ArrayList<String> out = new ArrayList<String>();
        String[] hosts = list("hosts");
        if (text == null || hosts.length == 0) {
            return out;
        }
        Matcher m = link(hosts).matcher(text);
        while (m.find()) {
            String first = m.group(1);
            if (first.startsWith("+") || contains(list("closed"), first) || contains(list("folder"), first)
                || contains(list("reserved"), first) || first.length() < 4) {
                continue;
            }
            once(out, first);
        }
        Matcher said = HANDLE.matcher(text);
        while (said.find()) {
            once(out, said.group(1));
        }
        return out;
    }

    private static void once(ArrayList<String> names, String name) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(name)) {
                return;
            }
        }
        names.add(name);
    }

    private static boolean contains(String[] words, String word) {
        for (int i = 0; i < words.length; i++) {
            if (words[i].equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }
}
