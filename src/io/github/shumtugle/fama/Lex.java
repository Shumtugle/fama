package io.github.shumtugle.fama;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The words an announcement is read by: the months and the days of the
 * week, the words that make a post an announcement, the words that say
 * where, the ones that call a meeting off.
 *
 * They are not in the source. They live in a plain text beside it, one kind
 * to a line, so the reading can be taught another language, or a new habit
 * of the channels, without a line of code changing. Every word is kept made
 * plain, the same way the posts are, so the two meet on equal terms.
 *
 * Over the words that call and the words that turn away lies a layer of the
 * reader's own: words and phrases added by hand, read strictly, and words
 * of the lexicon switched off. A word switched off stays off when a newer
 * lexicon comes; nothing of the lexicon is ever deleted by hand.
 */
final class Lex {

    private static final Map<String, List<String>> KINDS = new HashMap<String, List<String>>();
    /** The lexicon's words with those switched off left out, for the kinds a hand can change. */
    private static final Map<String, List<String>> ACTIVE = new HashMap<String, List<String>>();
    private static final Map<String, List<Own>> OWN = new HashMap<String, List<Own>>();
    private static final Map<String, Set<String>> OFF = new HashMap<String, Set<String>>();

    /** The kinds whose words a hand can change: those that call, and those that turn away. */
    static final String CALL = "event";
    static final String AWAY = "away";
    private static final List<String> NONE = new ArrayList<String>();
    /** Kinds kept exactly as written: pictures are not words and plainness would erase them. */
    private static final String RAW = "pins";

    private Lex() {
    }

    /** Read once, from wherever the lexicon comes. A second reading replaces the first. */
    static synchronized void read(InputStream in) {
        KINDS.clear();
        if (in == null) {
            return;
        }
        try {
            BufferedReader lines = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = lines.readLine()) != null) {
                String t = line.trim();
                if (t.length() == 0 || t.charAt(0) == '#') {
                    continue;
                }
                int cut = t.indexOf('\t');
                if (cut <= 0) {
                    continue;
                }
                String key = t.substring(0, cut).trim();
                String[] said = t.substring(cut + 1).trim().split("\\s+");
                ArrayList<String> words = new ArrayList<String>();
                for (int i = 0; i < said.length; i++) {
                    /* A star at the end marks a word met by its start; it outlives the making plain. */
                    boolean start = said[i].endsWith("*") && !RAW.equals(key);
                    String raw = start ? said[i].substring(0, said[i].length() - 1) : said[i];
                    String w = RAW.equals(key) ? raw : Near.norm(raw);
                    if (w.length() > 0) {
                        words.add(start ? w + "*" : w);
                    }
                }
                KINDS.put(key, words);
            }
            lines.close();
        } catch (Exception unread) {
            Trace.note("lexicon: not read, " + unread.getClass().getSimpleName());
        }
        settle();
    }

    static synchronized boolean ready() {
        return !KINDS.isEmpty();
    }

    /** The words of one kind that are in use, or none. */
    static synchronized List<String> of(String kind) {
        List<String> words = ACTIVE.get(kind);
        if (words == null) {
            words = KINDS.get(kind);
        }
        return words == null ? NONE : words;
    }

    /** Every word the lexicon has of a kind, those switched off too. */
    static synchronized List<String> lexicon(String kind) {
        List<String> words = KINDS.get(kind);
        return words == null ? NONE : new ArrayList<String>(words);
    }

    // ------------------------------------------------------------------ one's own

    /** A word or a phrase of one's own: as it was typed, and the plain stems of its parts. */
    static final class Own {
        final String said;
        final String[] stems;

        Own(String said, String[] stems) {
            this.said = said;
            this.stems = stems;
        }
    }

    /**
     * A typed word or phrase made ready for reading, or nothing if no part of
     * it is long enough to mean anything. A hyphen parts a phrase as a space
     * does, the way the posts are parted.
     */
    static Own own(String typed) {
        String said = typed.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<String> parts = Near.words(said);
        if (parts.isEmpty()) {
            return null;
        }
        String[] stems = new String[parts.size()];
        boolean long3 = false;
        for (int i = 0; i < stems.length; i++) {
            stems[i] = stem(parts.get(i));
            long3 = long3 || stems[i].length() >= 3;
        }
        return long3 ? new Own(said, stems) : null;
    }

    /**
     * The part of a typed word its other forms share: the word itself when
     * it ends in a letter no form drops; one such letter off; two, when the
     * word keeps six letters after them. So a lesson keeps its other cases
     * without meeting the word for being busy, and a long adjective loses
     * its whole ending.
     */
    static synchronized String stem(String plain) {
        if (plain.length() <= 3) {
            return plain;
        }
        List<String> ending = KINDS.get("ending");
        int cut = plain.length();
        int off = 0;
        while (off < 2 && cut > 3 && ending != null
            && ending.contains(String.valueOf(plain.charAt(cut - 1)))) {
            cut--;
            off++;
        }
        if (off == 2 && cut < 6) {
            cut++;
        }
        return plain.substring(0, cut);
    }

    /** Whether a post's plain words hold a word or phrase of one's own, its parts one after another. */
    static boolean holds(List<String> words, Own own) {
        int n = own.stems.length;
        for (int i = 0; i + n <= words.size(); i++) {
            boolean all = true;
            for (int k = 0; k < n && all; k++) {
                String w = words.get(i + k);
                String s = own.stems[k];
                all = s.length() <= 3 ? w.equals(s) : w.startsWith(s);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** One's own words of a kind. */
    static synchronized List<Own> owned(String kind) {
        List<Own> list = OWN.get(kind);
        return list == null ? new ArrayList<Own>() : new ArrayList<Own>(list);
    }

    /** Whether any word of one's own of a kind is in a post. */
    static boolean ownAny(String kind, List<String> words) {
        List<Own> list = owned(kind);
        for (int i = 0; i < list.size(); i++) {
            if (holds(words, list.get(i))) {
                return true;
            }
        }
        return false;
    }

    static synchronized boolean isOff(String kind, String word) {
        Set<String> off = OFF.get(kind);
        return off != null && off.contains(word);
    }

    /** A word of the lexicon switched off, or on again. */
    static synchronized void setOff(String kind, String word, boolean off) {
        Set<String> set = OFF.get(kind);
        if (set == null) {
            set = new HashSet<String>();
            OFF.put(kind, set);
        }
        if (off) {
            set.add(word);
        } else {
            set.remove(word);
        }
        settle();
    }

    static final int ADDED = 0;
    static final int THERE = 1;
    static final int SHORT = 2;

    /** A word or phrase of one's own added to a kind: added, already there, or too short. */
    static synchronized int addOwn(String kind, String typed) {
        Own made = own(typed);
        if (made == null) {
            return SHORT;
        }
        List<Own> list = OWN.get(kind);
        if (list == null) {
            list = new ArrayList<Own>();
            OWN.put(kind, list);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).said.equals(made.said)) {
                return THERE;
            }
        }
        list.add(made);
        return ADDED;
    }

    static synchronized void removeOwn(String kind, String said) {
        List<Own> list = OWN.get(kind);
        for (int i = list == null ? -1 : list.size() - 1; i >= 0; i--) {
            if (list.get(i).said.equals(said)) {
                list.remove(i);
            }
        }
    }

    /**
     * The layer written out, one line to a word: own, its kind, the word as
     * typed; or off, its kind, the word of the lexicon switched off.
     */
    static synchronized String layer() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, List<Own>> e : OWN.entrySet()) {
            for (int i = 0; i < e.getValue().size(); i++) {
                out.append("own\t").append(e.getKey()).append('\t').append(e.getValue().get(i).said).append('\n');
            }
        }
        for (Map.Entry<String, Set<String>> e : OFF.entrySet()) {
            for (String word : e.getValue()) {
                out.append("off\t").append(e.getKey()).append('\t').append(word).append('\n');
            }
        }
        return out.toString();
    }

    /** The layer read back; it takes the place of the one there was. */
    static synchronized void layer(String text) {
        OWN.clear();
        OFF.clear();
        merge(text);
    }

    /** Lines of a layer added to the one there is; how many of them were new. */
    static synchronized int merge(String text) {
        int came = 0;
        String[] lines = text == null ? new String[0] : text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String[] parts = lines[i].replace("\r", "").split("\t");
            if (parts.length < 3 || !(CALL.equals(parts[1]) || AWAY.equals(parts[1]))) {
                continue;
            }
            if ("own".equals(parts[0]) && addOwn(parts[1], parts[2]) == ADDED) {
                came++;
            } else if ("off".equals(parts[0]) && !isOff(parts[1], parts[2])) {
                Set<String> set = OFF.get(parts[1]);
                if (set == null) {
                    set = new HashSet<String>();
                    OFF.put(parts[1], set);
                }
                set.add(parts[2]);
                came++;
            }
        }
        settle();
        return came;
    }

    /** The words in use made again from the lexicon and what is switched off. */
    private static void settle() {
        ACTIVE.clear();
        for (Map.Entry<String, Set<String>> e : OFF.entrySet()) {
            List<String> all = KINDS.get(e.getKey());
            if (all == null || e.getValue().isEmpty()) {
                continue;
            }
            ArrayList<String> on = new ArrayList<String>();
            for (int i = 0; i < all.size(); i++) {
                if (!e.getValue().contains(all.get(i))) {
                    on.add(all.get(i));
                }
            }
            ACTIVE.put(e.getKey(), on);
        }
    }

    /** Whether a plain word is met by a word of the lexicon: whole, or by its start if the word is starred. */
    static boolean meets(String entry, String word) {
        return entry.endsWith("*") ? word.startsWith(entry.substring(0, entry.length() - 1)) : word.equals(entry);
    }

    /** Which word of a kind a plain word is met by, whole or by a starred start; or minus one. */
    static int whole(String word, List<String> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (meets(entries.get(i), word)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a plain word is exactly one of a kind. */
    static boolean is(String kind, String word) {
        return of(kind).contains(word);
    }
}
