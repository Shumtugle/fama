package io.github.shumtugle.fama;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A board carried to another phone, or into another application, as plain
 * text: the channels with their tags and names, the meetings kept by hand,
 * the meetings taken off, and the look. What the channels can say again is
 * left out; they say it again at the first reading.
 *
 * One line to a thing, its parts parted by tabs, under a line that names
 * the text for what it is:
 *
 *   carry 1
 *   look      hue, richness, whether the wallpaper leads
 *   channel   short name, tag, name as shown
 *   hidden    the key of a meeting taken off
 *   bill      a meeting kept by hand, as the board keeps it
 *   own       a word of one's own: whether it calls or turns away, as typed
 *   off       a word of the lexicon switched off, the same way
 *
 * Taking a carried board in adds to the one already there and takes
 * nothing away: a channel already on it keeps what it has.
 */
final class Carry {

    static final String HEAD = "carry 1";

    /** Set when a carried look was taken in, so the board regrows its colours when it is next in front. */
    static volatile boolean relook;

    /** What a carried text holds. */
    static final class Load {
        final ArrayList<Board.Source> sources = new ArrayList<Board.Source>();
        final ArrayList<Sift.Bill> bills = new ArrayList<Sift.Bill>();
        final ArrayList<String> hidden = new ArrayList<String>();
        /** The lines of the words' layer, as the lexicon keeps them. */
        final StringBuilder words = new StringBuilder();
        boolean look;
        float hue;
        float rich;
        boolean wall;
    }

    private Carry() {
    }

    /** The whole board, written out. */
    static String write(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("# a board carried over: its channels, the meetings kept by hand, ")
            .append("the meetings taken off, and its look\n");
        out.append(HEAD).append('\n');
        float[] look = Keep.look(context);
        out.append("look\t").append(look[0]).append('\t').append(look[1]).append('\t')
            .append(Keep.wall(context) ? 1 : 0).append('\n');
        ArrayList<Board.Source> sources = Board.sources(context);
        for (int i = 0; i < sources.size(); i++) {
            Board.Source s = sources.get(i);
            out.append("channel\t").append(s.name).append('\t').append(flat(s.tag)).append('\t')
                .append(flat(s.title)).append('\n');
        }
        ArrayList<String> hidden = Board.hidden(context);
        for (int i = 0; i < hidden.size(); i++) {
            out.append("hidden\t").append(hidden.get(i)).append('\n');
        }
        out.append(Lex.layer());
        ArrayList<Sift.Bill> kept = Board.kept(context);
        for (int i = 0; i < kept.size(); i++) {
            try {
                out.append("bill\t").append(Board.json(kept.get(i)).toString()).append('\n');
            } catch (Exception unwritten) {
                Trace.note("carry: a meeting not written, " + unwritten.getClass().getSimpleName());
            }
        }
        return out.toString();
    }

    /** Words that must stay on one line and in one part of it. */
    private static String flat(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** Whether a text is a carried board: its first line that is not a remark names it so. */
    static boolean is(String text) {
        if (text == null) {
            return false;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            return line.equals(HEAD);
        }
        return false;
    }

    /** A carried text read; a line it does not understand is passed over. */
    static Load read(String text) {
        Load load = new Load();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            String[] parts = line.split("\t", -1);
            try {
                if ("look".equals(parts[0]) && parts.length >= 4) {
                    load.hue = Float.parseFloat(parts[1]);
                    load.rich = Float.parseFloat(parts[2]);
                    load.wall = "1".equals(parts[3].trim());
                    load.look = true;
                } else if ("channel".equals(parts[0]) && parts.length >= 2
                    && parts[1].trim().matches("[A-Za-z0-9_]{4,64}")) {
                    Board.Source s = new Board.Source();
                    s.name = parts[1].trim();
                    s.tag = parts.length > 2 ? parts[2].trim() : "";
                    s.title = parts.length > 3 ? parts[3].trim() : "";
                    s.state = Board.WAIT;
                    load.sources.add(s);
                } else if ("hidden".equals(parts[0]) && parts.length >= 2) {
                    load.hidden.add(parts[1].trim());
                } else if (("own".equals(parts[0]) || "off".equals(parts[0])) && parts.length >= 3) {
                    load.words.append(line).append('\n');
                } else if ("bill".equals(parts[0]) && parts.length >= 2) {
                    load.bills.add(Board.bill(new JSONObject(parts[1])));
                }
            } catch (Exception odd) {
                Trace.note("carry: a line passed over, " + odd.getClass().getSimpleName());
            }
        }
        return load;
    }

    /** A carried board taken in beside the one here; what came that was not here before. */
    static Taken apply(Context context, Load load) {
        Taken taken = new Taken();
        long now = System.currentTimeMillis();
        for (int i = 0; i < load.sources.size(); i++) {
            load.sources.get(i).added = now;
        }
        taken.channels = Board.welcome(context, load.sources);
        taken.bills = Board.add(context, load.bills);
        taken.hidden = Board.hideAll(context, load.hidden);
        taken.words = Lex.merge(load.words.toString());
        if (taken.words > 0) {
            Keep.saveWords(context, Lex.layer());
        }
        if (load.look) {
            Keep.saveLook(context, load.hue, load.rich, load.wall);
            relook = true;
        }
        Trace.note("carry: taken in, " + taken.channels.size() + " channels, " + taken.bills
            + " kept by hand, " + taken.hidden + " taken off");
        return taken;
    }

    /** What a carry brought that was not here before. */
    static final class Taken {
        List<String> channels = new ArrayList<String>();
        int bills;
        int hidden;
        int words;
    }
}
