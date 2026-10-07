package io.github.shumtugle.fama;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which posts are announcements, and what each one announces.
 *
 * A post is not judged by a word. Words are weak witnesses: a meeting is
 * mentioned in a joke, a date in a sale, a place in a review. A post is
 * weighed instead, and the day to come is the spine of it — a post with no
 * day still ahead announces nothing, however festive it looks. On that
 * spine the other witnesses add their weight: a word that calls people
 * together, the channel's own tag, an hour, a place, a title in quotes.
 *
 * A post that neither calls anyone nor carries the tag has to prove itself
 * with everything else at once — a day, an hour, a place and a title — or a
 * sale that ends on Friday at ten in the shop would be a meeting.
 *
 * Two gifts come free. A paid post is marked by law, so it is thrown out by
 * its mark. And a meeting called off is not thrown out but kept, crossed
 * through: a card that vanished would send someone to a door that is shut.
 */
final class Sift {

    /** How far ahead a day may be and still stand on the board. */
    static final int AHEAD_DAYS = 180;
    /** How much of an announcement is kept for reading: more than any announcement says. */
    static final int TEXT_MOST = 6000;

    private static final int DAY = 3;
    private static final int CALL = 2;
    private static final int TAG = 2;
    private static final int HOUR = 1;
    private static final int PLACE = 1;
    private static final int TITLE = 1;
    /** What a report of something already past takes off. */
    private static final int AWAY = 2;

    private static final Pattern HASH = Pattern.compile("#([\\p{L}\\p{N}_]+)");
    private static final Pattern QUOTED = Pattern.compile(
        "\u00AB([^\u00AB\u00BB\\n]{2,90})\u00BB|\u201C([^\u201C\u201D\\n]{2,90})\u201D|\"([^\"\\n]{2,90})\""
        + "|\u2018([^\u2018\u2019\\n]{2,90})\u2019|\u201E([^\u201E\u201C\\n]{2,90})\u201C");
    private static final Pattern LEAD = Pattern.compile("^[^\\p{L}\\p{N}\u00AB\u201C\u201E\u2018\"]+");
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");

    /** A post as it was read from a channel. */
    static final class Post {
        final int id;
        final String text;
        final long posted;

        /** Words that lead somewhere, each a pair: the words, and where they lead. */
        final ArrayList<String[]> links = new ArrayList<String[]>();

        Post(int id, String text, long posted) {
            this.id = id;
            this.text = text == null ? "" : text;
            this.posted = posted;
        }
    }

    /** One meeting on the board. */
    static final class Bill {
        String source = "";
        int post;
        int day;
        int minutes = -1;
        String book = "";
        String place = "";
        String line = "";
        boolean cancelled;
        boolean moved;
        long posted;
        int weight;
        /** The announcement's own words, for reading it here; they leave with the meeting's day. */
        String text = "";
        ArrayList<String[]> links = new ArrayList<String[]>();
        /** A later post's words that called the meeting off or moved it. */
        String note = "";
        /** Said only roughly: an approximate day or place, which a firm telling outranks. */
        boolean rough;
        /** A detail promised for later — the place, the hour — and not yet told. */
        boolean pending;
        /** The book is the post's own words about whom is read, not a title in quotes. */
        boolean loose;

        /** The same meeting read twice is known by this. */
        String key() {
            return source + "/" + post + "/" + day + "/" + minutes;
        }
    }

    /**
     * What the words found in one reading: for every word that calls or turns
     * away, how many posts it was met in, and the form it was met in most.
     * A word of one's own is kept under its typed form with a sign before it.
     */
    static final class Tally {
        final HashMap<String, Integer> found = new HashMap<String, Integer>();
        final HashMap<String, HashMap<String, Integer>> forms = new HashMap<String, HashMap<String, Integer>>();

        void add(String kind, String word, String form, int times) {
            String key = kind + "\t" + word;
            Integer had = found.get(key);
            found.put(key, Integer.valueOf((had == null ? 0 : had.intValue()) + times));
            HashMap<String, Integer> seen = forms.get(key);
            if (seen == null) {
                seen = new HashMap<String, Integer>();
                forms.put(key, seen);
            }
            Integer f = seen.get(form);
            seen.put(form, Integer.valueOf((f == null ? 0 : f.intValue()) + times));
        }

        int count(String kind, String word) {
            Integer n = found.get(kind + "\t" + word);
            return n == null ? 0 : n.intValue();
        }

        /** The form a word was met in most, or nothing. */
        String form(String kind, String word) {
            HashMap<String, Integer> seen = forms.get(kind + "\t" + word);
            String best = "";
            int most = 0;
            if (seen != null) {
                for (Map.Entry<String, Integer> e : seen.entrySet()) {
                    if (e.getValue().intValue() > most) {
                        most = e.getValue().intValue();
                        best = e.getKey();
                    }
                }
            }
            return best;
        }

        /** Written out, a line to a word: kind, word, count, form. */
        String write() {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Integer> e : found.entrySet()) {
                String[] kw = e.getKey().split("\t", 2);
                out.append(e.getKey()).append('\t').append(e.getValue()).append('\t')
                    .append(form(kw[0], kw[1])).append('\n');
            }
            return out.toString();
        }

        /** Another reading's tally added to this one. */
        void read(String text) {
            String[] lines = text == null ? new String[0] : text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String[] p = lines[i].split("\t");
                if (p.length >= 3) {
                    try {
                        add(p[0], p[1], p.length > 3 ? p[3] : "", Integer.parseInt(p[2]));
                    } catch (NumberFormatException odd) {
                        // a line passed over
                    }
                }
            }
        }
    }

    private Sift() {
    }

    /** Whether a post calls people together, by the lexicon or by a word of one's own. */
    static boolean calls(List<String> words) {
        return Near.any(words, Lex.of(Lex.CALL)) || Lex.ownAny(Lex.CALL, words);
    }

    /**
     * Whether a post is a report of something already past, by the lexicon or
     * by a word of one's own. The lexicon's words are met whole: a verb that
     * says a thing took place shares its start with the word for last time,
     * and last time is how many announcements begin.
     */
    static boolean away(List<String> words) {
        List<String> turning = Lex.of(Lex.AWAY);
        for (int i = 0; i < words.size(); i++) {
            if (Lex.whole(words.get(i), turning) >= 0 && !turnedBack(words, i)) {
                return true;
            }
        }
        return Lex.ownAny(Lex.AWAY, words);
    }

    /**
     * Whether a report word is turned back to what is coming by the word
     * beside it: totals to be drawn at the next meeting are not a report of
     * the last one.
     */
    static boolean turnedBack(List<String> words, int i) {
        List<String> back = Lex.of("unaway");
        return (i > 0 && Lex.whole(words.get(i - 1), back) >= 0)
            || (i + 1 < words.size() && Lex.whole(words.get(i + 1), back) >= 0);
    }

    /** Which words that call, and which that turn away, a post holds: counted once each. */
    private static void count(Tally tally, List<String> words, boolean passed) {
        if (passed) {
            List<String> calling = Lex.of(Lex.CALL);
            HashSet<String> once = new HashSet<String>();
            for (int i = 0; i < words.size(); i++) {
                int k = Near.which(words.get(i), calling);
                if (k >= 0 && once.add(calling.get(k))) {
                    tally.add(Lex.CALL, calling.get(k), words.get(i), 1);
                }
            }
            List<Lex.Own> own = Lex.owned(Lex.CALL);
            for (int i = 0; i < own.size(); i++) {
                if (Lex.holds(words, own.get(i))) {
                    tally.add(Lex.CALL, "+" + own.get(i).said, own.get(i).said, 1);
                }
            }
        }
        List<String> turning = Lex.of(Lex.AWAY);
        HashSet<String> once = new HashSet<String>();
        for (int i = 0; i < words.size(); i++) {
            int k = Lex.whole(words.get(i), turning);
            if (k >= 0 && !turnedBack(words, i) && once.add(turning.get(k))) {
                tally.add(Lex.AWAY, turning.get(k), words.get(i), 1);
            }
        }
        List<Lex.Own> own = Lex.owned(Lex.AWAY);
        for (int i = 0; i < own.size(); i++) {
            if (Lex.holds(words, own.get(i))) {
                tally.add(Lex.AWAY, "+" + own.get(i).said, own.get(i).said, 1);
            }
        }
    }

    // ------------------------------------------------------------------ a channel

    /**
     * The meetings a channel's posts announce, merged so that one meeting
     * told in two posts — announced, then called off — is one card.
     */
    static ArrayList<Bill> sift(String source, List<Post> posts, String tag, String learned,
                                Calendar now) {
        return sift(source, posts, tag, learned, now, null);
    }

    /** The same, with what the words found counted into a tally. */
    static ArrayList<Bill> sift(String source, List<Post> posts, String tag, String learned,
                                Calendar now, Tally tally) {
        ArrayList<Bill> all = new ArrayList<Bill>();
        for (int i = 0; i < posts.size(); i++) {
            all.addAll(weigh(source, posts.get(i), tag, learned, now, false, tally));
        }
        return merge(all);
    }

    /**
     * The one post weighed. A post handed over by hand is trusted: someone
     * already judged it an announcement, so any day ahead in it is enough.
     */
    static ArrayList<Bill> weigh(String source, Post post, String tag, String learned,
                                 Calendar now, boolean trusted) {
        return weigh(source, post, tag, learned, now, trusted, null);
    }

    static ArrayList<Bill> weigh(String source, Post post, String tag, String learned,
                                 Calendar now, boolean trusted, Tally tally) {
        ArrayList<Bill> out = new ArrayList<Bill>();
        String text = post.text;
        List<String> words = Near.words(text);
        if (paid(words, text)) {
            return out;
        }
        List<String> tags = tags(text);
        boolean tagged = false;
        if (tag != null && tag.length() > 0) {
            if (!carries(tag, tags, words)) {
                return out;
            }
            tagged = true;
        } else if (learned != null && learned.length() > 0) {
            tagged = carries(learned, tags, null);
        }

        Calendar posted = (Calendar) now.clone();
        if (post.posted > 0) {
            posted.setTimeInMillis(post.posted);
        }
        Days.When when = Days.find(text, posted);
        int today = Days.stamp(now);
        Calendar far = (Calendar) now.clone();
        far.add(Calendar.DAY_OF_MONTH, AHEAD_DAYS);
        int last = Days.stamp(far);
        ArrayList<Days.Day> ahead = new ArrayList<Days.Day>();
        HashSet<Integer> seen = new HashSet<Integer>();
        HashSet<Integer> lines = new HashSet<Integer>();
        for (int i = 0; i < when.days.size(); i++) {
            Days.Day d = when.days.get(i);
            if (d.day >= today && d.day <= last && seen.add(Integer.valueOf(d.day))) {
                ahead.add(d);
                lines.add(Integer.valueOf(d.line));
            }
        }
        if (ahead.isEmpty()) {
            return out;
        }

        String[] rows = text.split("\n", -1);
        int[] promise = promise(text);
        boolean calls = calls(promise == null ? words
            : Near.words(text.substring(0, promise[0]) + " " + text.substring(promise[1])));
        boolean past = away(words);
        boolean rough = rough(text, when.days);
        String place = place(rows);
        ArrayList<Title> titles = titles(text);
        int weight = DAY + (calls ? CALL : 0) + (tagged ? TAG : 0)
            + (when.hours.isEmpty() ? 0 : HOUR) + (place.length() > 0 ? PLACE : 0)
            + (titles.isEmpty() ? 0 : TITLE) - (past ? AWAY : 0);
        boolean enough = trusted || ((calls || tagged) ? weight >= 5 : weight >= 6);
        if (tally != null) {
            count(tally, words, enough);
        }
        if (!enough) {
            return out;
        }

        /* A list of meetings, one to a line, is several cards, each with its own line's title. */
        boolean list = ahead.size() >= 2 && lines.size() >= 2;
        boolean off = said(words, "cancel");
        boolean moved = said(words, "moved");
        String gist = gist(rows);
        String reads = list ? "" : reads(rows);
        for (int i = 0; i < ahead.size(); i++) {
            Days.Day d = ahead.get(i);
            Bill b = new Bill();
            b.source = source;
            b.post = post.id;
            b.day = d.day;
            b.posted = post.posted;
            b.weight = weight;
            b.minutes = hourFor(d, when.hours, list);
            b.place = place;
            b.text = keep(text, TEXT_MOST);
            b.links = post.links;
            b.rough = rough;
            b.pending = promise != null;
            if (list) {
                String row = d.line < rows.length ? rows[d.line] : "";
                List<String> rowWords = Near.words(row);
                b.cancelled = said(rowWords, "cancel");
                b.moved = said(rowWords, "moved");
                b.book = titleOn(titles, d.line, rows, lines);
                b.line = clean(row, 120);
            } else {
                b.cancelled = off;
                b.moved = moved;
                b.book = best(titles, text);
                b.line = gist;
                if (b.book.length() == 0 && reads.length() > 0) {
                    b.book = reads;
                    b.loose = true;
                }
            }
            if (b.line.equals(b.place)) {
                b.place = "";
            }
            out.add(b);
        }
        return out;
    }

    /** The hour of a day: one on its own line first; else, the post's only hour. */
    private static int hourFor(Days.Day d, List<Days.Hour> hours, boolean list) {
        Days.Hour after = null;
        Days.Hour before = null;
        HashSet<Integer> distinct = new HashSet<Integer>();
        for (int i = 0; i < hours.size(); i++) {
            Days.Hour h = hours.get(i);
            distinct.add(Integer.valueOf(h.minutes));
            if (h.line != d.line) {
                continue;
            }
            if (h.at > d.at && after == null) {
                after = h;
            } else if (h.at < d.at) {
                before = h;
            }
        }
        if (after != null) {
            return after.minutes;
        }
        if (before != null) {
            return before.minutes;
        }
        if (!hours.isEmpty() && (distinct.size() == 1 || !list)) {
            return hours.get(0).minutes;
        }
        return -1;
    }

    // ------------------------------------------------------------------ witnesses

    /** A paid post, by the word the law makes it wear or by the token it must carry. */
    static boolean paid(List<String> words, String text) {
        List<String> marks = Lex.of("advert");
        for (int i = 0; i < words.size(); i++) {
            if (marks.contains(words.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** The tags of a post, made plain, without their sign. */
    static List<String> tags(String text) {
        ArrayList<String> out = new ArrayList<String>();
        Matcher m = HASH.matcher(text);
        while (m.find()) {
            String t = Near.norm(m.group(1)).replace(" ", "");
            if (t.length() > 0 && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Whether a post carries a tag: among its tags, or — for a tag given by hand — among its words. */
    static boolean carries(String tag, List<String> tags, List<String> words) {
        String t = Near.norm(tag.startsWith("#") ? tag.substring(1) : tag).replace(" ", "");
        if (t.length() == 0) {
            return true;
        }
        for (int i = 0; i < tags.size(); i++) {
            if (Near.like(t, tags.get(i))) {
                return true;
            }
        }
        if (words != null) {
            for (int i = 0; i < words.size(); i++) {
                if (Near.like(t, words.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Where the meeting is. A line with a pin is the place, whole. Failing
     * that, the lines are searched for a word for a street, a door or a room,
     * and a place with a name — a capital, a number, a name in quotes — is
     * taken before a bare one: "in the park" is a kind of place, "the
     * Old Garden" is a place. Failing a word, a name entered after a word
     * for meeting stands for the place: "we meet at Harbour Hall". A
     * line that opens with a spare plan — if it rains, then elsewhere — is
     * heard last, and only when nothing else is said.
     */
    static String place(String[] rows) {
        List<String> pins = Lex.of("pins");
        for (int i = 0; i < rows.length; i++) {
            for (int k = 0; k < pins.size(); k++) {
                if (rows[i].contains(pins.get(k))) {
                    String said = clean(Near.mend(rows[i]), 90);
                    if (said.length() > 2) {
                        return said;
                    }
                }
            }
        }
        String bare = "";
        String spareNamed = "";
        String spareBare = "";
        for (int i = 0; i < rows.length; i++) {
            String found = placeOn(rows, i);
            if (found.length() == 0) {
                continue;
            }
            boolean spare = spare(rows[i]);
            if (named(found)) {
                if (!spare) {
                    return found;
                }
                if (spareNamed.length() == 0) {
                    spareNamed = found;
                }
            } else if (!spare && bare.length() == 0) {
                bare = found;
            } else if (spare && spareBare.length() == 0) {
                spareBare = found;
            }
        }
        for (int i = 0; i < rows.length; i++) {
            if (!spare(rows[i])) {
                String entered = entered(Near.mend(rows[i]));
                if (entered.length() > 0) {
                    return entered;
                }
            }
        }
        if (bare.length() > 0) {
            return bare;
        }
        return spareNamed.length() > 0 ? spareNamed : spareBare;
    }

    /**
     * The place a line names by a word for one: with the small word before
     * it and a name before it, so that it reads as said ("in the Old
     * Garden"), up to the end of its sentence — or of the name in quotes
     * that comes straight after it. A word inside quotes is a title's, not
     * a place's. A place with no number in it borrows the next line when
     * that line is an address.
     */
    private static String placeOn(String[] rows, int i) {
        List<String> places = Lex.of("place");
        String row = Near.mend(rows[i]);
        Matcher word = WORDS.matcher(row);
        int previous = -1;
        int before = -1;
        String previousWord = "";
        String beforeWord = "";
        boolean previousCapital = false;
        int count = 0;
        while (word.find()) {
            String plain = Near.norm(word.group());
            count++;
            if (plain.length() > 0 && Near.starts(plain, places) >= 0 && !quoted(row, word.start())) {
                int from = word.start();
                if (previous >= 0 && previousWord.length() <= 2) {
                    from = previous;
                } else if (previous >= 0 && previousCapital) {
                    from = before >= 0 && beforeWord.length() <= 2 && beforeWord.length() > 0
                        && Character.isLowerCase(row.charAt(before)) ? before : previous;
                }
                if (count <= 2) {
                    from = 0;
                }
                String rest = row.substring(from);
                rest = rest.substring(0, sentenceEnd(rest, word.end() - from));
                int opened = rest.indexOf('\u00AB');
                int shut = rest.indexOf('\u00BB');
                int gap = opened < 0 ? -1 : rest.substring(Math.min(word.end() - from, rest.length()),
                    Math.max(Math.min(word.end() - from, rest.length()), opened)).trim().length();
                if (opened > 0 && shut > opened && gap == 0) {
                    rest = rest.substring(0, shut + 1);
                }
                String said = tail(clean(rest, 70));
                if (said.length() > 2 && !promised(said)) {
                    if (!said.matches(".*\\d.*") && i + 1 < rows.length) {
                        String next = tail(clean(rows[i + 1], 60));
                        if (next.matches(".*\\d.*") && !spare(next) && holdsPlaceWord(next)
                            && next.length() < 60) {
                            said = said + ", " + next;
                        }
                    }
                    return said;
                }
            }
            before = previous;
            beforeWord = previousWord;
            previous = word.start();
            previousWord = plain;
            previousCapital = Character.isUpperCase(row.codePointAt(word.start()));
        }
        return "";
    }

    /**
     * A name entered after a word that calls people together: the small
     * word of entering, then words that begin with a capital — "meet at Harbour
     * Hall and discuss" gives "at Harbour Hall". One word without a
     * capital may close the name when the sentence ends on it.
     */
    static String entered(String row) {
        List<String> into = Lex.of("into");
        List<String> calling = Lex.of(Lex.CALL);
        Matcher m = WORDS.matcher(row);
        ArrayList<int[]> at = new ArrayList<int[]>();
        while (m.find()) {
            at.add(new int[] {m.start(), m.end()});
        }
        for (int k = 1; k + 1 < at.size(); k++) {
            String small = Near.norm(row.substring(at.get(k)[0], at.get(k)[1]));
            if (!into.contains(small)) {
                continue;
            }
            /* The word that calls may stand further back, past the day and the hour said before the place. */
            boolean called = false;
            for (int back = k - 1; back >= 0 && back >= k - 8 && !called; back--) {
                String w = Near.norm(row.substring(at.get(back)[0], at.get(back)[1]));
                if (w.matches("\\d+") || calendarWord(w) || into.contains(w)) {
                    continue;
                }
                called = Near.which(w, calling) >= 0;
                if (!called) {
                    break;
                }
            }
            if (!called || !capital(row, at.get(k + 1)[0])) {
                continue;
            }
            int last = -1;
            for (int j = k + 1; j < at.size() && j <= k + 5; j++) {
                String between = row.substring(at.get(j - 1)[1], at.get(j)[0]);
                if (j > k + 1 && !between.matches("[\\s.\u00AB\u00BB\"']+")) {
                    break;
                }
                String plain = Near.norm(row.substring(at.get(j)[0], at.get(j)[1]));
                if (capital(row, at.get(j)[0]) && !calendarWord(plain)) {
                    last = j;
                    continue;
                }
                String after = row.substring(at.get(j)[1]);
                if (last > k && plain.length() > 2 && after.matches("^\\s*([.,;:!?)\u00BB\"\u2014\u2013-].*)?$")) {
                    last = j;
                }
                break;
            }
            if (last > k) {
                int end = at.get(last)[1];
                if (end < row.length() && "\u00BB\"".indexOf(row.charAt(end)) >= 0) {
                    end++;
                }
                String said = tail(clean(row.substring(at.get(k)[0], end), 70));
                if (said.length() > 3) {
                    return said;
                }
            }
        }
        return "";
    }

    /** Whether a found place has a name in it, not only a word for a kind of place. */
    private static boolean named(String place) {
        if (place.matches(".*[\\d\u00AB\"\u201C\u201E].*")) {
            return true;
        }
        List<String> places = Lex.of("place");
        Matcher m = WORDS.matcher(place);
        while (m.find()) {
            if (capital(place, m.start()) && Near.starts(Near.norm(m.group()), places) < 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsPlaceWord(String row) {
        List<String> places = Lex.of("place");
        List<String> words = Near.words(row);
        for (int i = 0; i < words.size(); i++) {
            if (Near.starts(words.get(i), places) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a line opens with a spare plan: its first two words hold one. */
    static boolean spare(String row) {
        List<String> words = Near.words(row);
        List<String> spare = Lex.of("spare");
        for (int i = 0; i < words.size() && i < 2; i++) {
            if (Lex.whole(words.get(i), spare) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a spot in a line stands inside quotes. */
    private static boolean quoted(String row, int at) {
        int opened = 0;
        int straight = 0;
        for (int i = 0; i < at; i++) {
            char c = row.charAt(i);
            if (c == '\u00AB' || c == '\u201E') {
                opened++;
            } else if ((c == '\u00BB' || c == '\u201C') && opened > 0) {
                opened--;
            } else if (c == '"') {
                straight++;
            }
        }
        return opened > 0 || straight % 2 == 1;
    }

    /**
     * Where a place's sentence ends, looked for after its word: at a bracket,
     * a dash between spaces, or a full stop, a mark of exclamation or of
     * question followed by a space — but not the stop of a short form, a
     * street or a house or an initial.
     */
    private static int sentenceEnd(String rest, int after) {
        List<String> shortForms = Lex.of("abbrev");
        int end = Math.min(rest.length(), whenStarts(rest, after));
        for (int i = Math.max(0, Math.min(after, rest.length())); i < end; i++) {
            char c = rest.charAt(i);
            boolean spaced = i + 1 < rest.length() && Character.isWhitespace(rest.charAt(i + 1));
            if (c == '(') {
                return i;
            }
            if ((c == '\u2014' || c == '\u2013' || c == '-') && i > 0
                && Character.isWhitespace(rest.charAt(i - 1)) && spaced) {
                return i;
            }
            if ((c == '!' || c == '?' || c == '\u2026') && (spaced || i + 1 == rest.length())) {
                return i;
            }
            if (c == '.' && (spaced || i + 1 == rest.length())) {
                int w = i;
                while (w > 0 && Character.isLetter(rest.charAt(w - 1))) {
                    w--;
                }
                String shortened = Near.norm(rest.substring(w, i));
                if (!(shortened.length() == 1 || shortForms.contains(shortened))) {
                    return i;
                }
            }
        }
        return end;
    }

    /**
     * Where the day or the hour begins after a place, if it follows it on the
     * same line: a number before a month written small, a number before the
     * colon or the stop of an hour, or a word for a day of the week — with
     * the small word that leads into it. A street named after a date keeps
     * its capital and is not cut.
     */
    private static int whenStarts(String rest, int after) {
        Matcher m = WORDS.matcher(rest);
        ArrayList<int[]> at = new ArrayList<int[]>();
        while (m.find()) {
            at.add(new int[] {m.start(), m.end()});
        }
        List<String> into = Lex.of("into");
        List<String> leading = Lex.of("at");
        for (int k = 0; k < at.size(); k++) {
            if (at.get(k)[0] < after) {
                continue;
            }
            String w = rest.substring(at.get(k)[0], at.get(k)[1]);
            boolean when = false;
            if (w.matches("\\d{1,2}")) {
                int e = at.get(k)[1];
                boolean hour = e + 2 < rest.length() && (rest.charAt(e) == ':' || rest.charAt(e) == '.')
                    && Character.isDigit(rest.charAt(e + 1)) && Character.isDigit(rest.charAt(e + 2));
                boolean dated = k + 1 < at.size() && !capital(rest, at.get(k + 1)[0])
                    && month(Near.norm(rest.substring(at.get(k + 1)[0], at.get(k + 1)[1])));
                when = hour || dated;
            } else if (!capital(rest, at.get(k)[0]) && calendarWord(Near.norm(w))) {
                when = true;
            }
            if (when) {
                int cut = at.get(k)[0];
                if (k > 0 && at.get(k - 1)[0] >= after) {
                    String small = Near.norm(rest.substring(at.get(k - 1)[0], at.get(k - 1)[1]));
                    if (into.contains(small) || leading.contains(small)) {
                        cut = at.get(k - 1)[0];
                    }
                }
                return cut;
            }
        }
        return rest.length();
    }

    private static boolean month(String plain) {
        for (int m = 1; m <= 12; m++) {
            if (Near.starts(plain, Lex.of("month." + m)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** A place's end without the pictures and marks left hanging after it. */
    private static String tail(String said) {
        String t = said;
        while (t.length() > 0) {
            char c = t.charAt(t.length() - 1);
            boolean closing = c == '\u00BB' || c == '"' || c == '\u201C'
                || (c == ')' && t.indexOf('(') >= 0);
            if (Character.isLetterOrDigit(c) || closing) {
                break;
            }
            t = t.substring(0, t.length() - 1);
        }
        return t.trim();
    }

    private static boolean capital(String text, int at) {
        return at < text.length() && Character.isUpperCase(text.codePointAt(at));
    }

    /** Whether a plain word is a month, a day of the week, or a day counted from the post. */
    private static boolean calendarWord(String plain) {
        for (int m = 1; m <= 12; m++) {
            if (Near.starts(plain, Lex.of("month." + m)) >= 0) {
                return true;
            }
        }
        for (int d = 1; d <= 7; d++) {
            if (Near.starts(plain, Lex.of("day." + d)) >= 0) {
                return true;
            }
        }
        return Lex.is("today", plain) || Lex.is("tomorrow", plain) || Lex.is("after", plain);
    }

    // ------------------------------------------------------------------ not yet settled

    /**
     * Where a post promises a detail for later — the place, the hour — as a
     * span of the text: from the word for the detail to the word after the
     * promise, or the other way round, so long as they stand within three
     * words of each other. What stands in that span talks about the meeting
     * and calls no one to it: "we shall write where the meeting is" is not
     * a meeting today. Nothing when no detail is promised.
     */
    static int[] promise(String text) {
        List<String> later = Lex.of("later");
        List<String> detail = Lex.of("detail");
        Matcher m = WORDS.matcher(text);
        ArrayList<int[]> at = new ArrayList<int[]>();
        ArrayList<String> plain = new ArrayList<String>();
        while (m.find()) {
            at.add(new int[] {m.start(), m.end()});
            plain.add(Near.norm(m.group()));
        }
        for (int i = 0; i < plain.size(); i++) {
            if (Lex.whole(plain.get(i), later) < 0) {
                continue;
            }
            for (int k = Math.max(0, i - 3); k <= Math.min(plain.size() - 1, i + 3); k++) {
                if (k != i && Lex.whole(plain.get(k), detail) >= 0) {
                    int first = Math.min(i, k);
                    int last = Math.min(plain.size() - 1, Math.max(i, k) + 1);
                    return new int[] {at.get(first)[0], at.get(last)[1]};
                }
            }
        }
        return null;
    }

    /** Whether a found place is only the promise of one: "the address we shall tell later". */
    private static boolean promised(String said) {
        List<String> later = Lex.of("later");
        List<String> words = Near.words(said);
        for (int i = 0; i < words.size(); i++) {
            if (Lex.whole(words.get(i), later) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a post says its meeting only roughly: a word for roughly within
     * three words of a word for a day, a place or an hour, or at the head of
     * a line that names a day — "the approximate date is the twelfth",
     * "provisionally: October the third".
     */
    static boolean rough(String text, List<Days.Day> days) {
        List<String> roughly = Lex.of("rough");
        List<String> detail = Lex.of("detail");
        HashSet<Integer> dayLines = new HashSet<Integer>();
        for (int i = 0; i < days.size(); i++) {
            dayLines.add(Integer.valueOf(days.get(i).line));
        }
        String[] rows = text.split("\n", -1);
        for (int r = 0; r < rows.length; r++) {
            List<String> words = Near.words(rows[r]);
            for (int i = 0; i < words.size(); i++) {
                if (Lex.whole(words.get(i), roughly) < 0) {
                    continue;
                }
                if (i < 2 && dayLines.contains(Integer.valueOf(r))) {
                    return true;
                }
                for (int k = Math.max(0, i - 3); k <= Math.min(words.size() - 1, i + 3); k++) {
                    if (k != i && Lex.whole(words.get(k), detail) >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ whom

    /**
     * Whom the meeting reads, when no title is quoted: the post's own words
     * from the word for reading to the end of the name after it: "we read"
     * and a writer. Up to three words with capitals, the dots of initials
     * between them, and one small joining word when a capital follows it.
     */
    static String reads(String[] rows) {
        List<String> verbs = Lex.of("reading");
        for (int r = 0; r < rows.length; r++) {
            String row = Near.mend(rows[r]);
            Matcher m = WORDS.matcher(row);
            ArrayList<int[]> at = new ArrayList<int[]>();
            while (m.find()) {
                at.add(new int[] {m.start(), m.end()});
            }
            for (int k = 0; k + 1 < at.size(); k++) {
                if (Lex.whole(Near.norm(row.substring(at.get(k)[0], at.get(k)[1])), verbs) < 0) {
                    continue;
                }
                int last = -1;
                int names = 0;
                for (int j = k + 1; j < at.size() && names < 3; j++) {
                    String between = row.substring(at.get(j - 1)[1], at.get(j)[0]);
                    if (!between.matches("[\\s.]+")) {
                        break;
                    }
                    String plain = Near.norm(row.substring(at.get(j)[0], at.get(j)[1]));
                    if (capital(row, at.get(j)[0]) && !calendarWord(plain)) {
                        last = j;
                        names++;
                    } else if (last > k && plain.length() == 1 && j + 1 < at.size()
                        && capital(row, at.get(j + 1)[0])) {
                        continue;
                    } else {
                        break;
                    }
                }
                if (last > k) {
                    String said = row.substring(at.get(k)[0], at.get(last)[1]);
                    if (said.length() - (at.get(k)[1] - at.get(k)[0]) >= 4) {
                        return said.replaceAll("\\s+", " ");
                    }
                }
            }
        }
        return "";
    }

    /** What the post is about in one line: the first line with words in it. */
    static String gist(String[] rows) {
        for (int i = 0; i < rows.length; i++) {
            String said = clean(rows[i], 120);
            int letters = 0;
            for (int k = 0; k < said.length() && letters < 3; k++) {
                if (Character.isLetter(said.charAt(k))) {
                    letters++;
                }
            }
            if (letters >= 3) {
                return said;
            }
        }
        return "";
    }

    /** A line with the pictures and marks before its first word taken off, and cut to a length. */
    static String clean(String row, int most) {
        String t = LEAD.matcher(Near.mend(row).trim()).replaceAll("").trim();
        t = t.replaceAll("\\s+", " ");
        if (t.length() > most) {
            int cut = t.lastIndexOf(' ', most - 1);
            t = t.substring(0, cut > most / 2 ? cut : most - 1) + "\u2026";
        }
        return t;
    }

    /**
     * An announcement kept for reading as it was written: its lines as they
     * stand, only the empty ones between paragraphs gathered into one, and
     * the rare long one cut at a word.
     */
    static String keep(String text, int most) {
        String t = text.replace("\r", "").replaceAll("[ \t\u00A0]+\n", "\n")
            .replaceAll("\n{3,}", "\n\n").trim();
        if (t.length() > most) {
            int cut = t.lastIndexOf(' ', most - 1);
            t = t.substring(0, cut > most / 2 ? cut : most - 1) + "\u2026";
        }
        return t;
    }

    // ------------------------------------------------------------------ titles

    /** A title in quotes, the line it stands on, and how likely it is to be the book. */
    static final class Title {
        final String text;
        final int line;
        final int rank;

        Title(String text, int line, int rank) {
            this.text = text;
            this.line = line;
            this.rank = rank;
        }
    }

    /**
     * Every quoted title that begins with a capital, ranked: one after a word
     * for reading or for a book is likely the book; one after a word for a
     * café, a shop or a club is likely the place, and is ranked down.
     */
    static ArrayList<Title> titles(String text) {
        ArrayList<Title> out = new ArrayList<Title>();
        Matcher m = QUOTED.matcher(text);
        while (m.find()) {
            String t = null;
            for (int g = 1; g <= m.groupCount() && t == null; g++) {
                t = m.group(g);
            }
            t = t.trim();
            if (t.length() < 2 || !Character.isUpperCase(t.codePointAt(0))) {
                continue;
            }
            int line = 0;
            for (int i = 0; i < m.start(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            List<String> before = Near.words(text.substring(Math.max(0, m.start() - 48), m.start()));
            int rank = 0;
            for (int back = 1; back <= 2 && back <= before.size(); back++) {
                String w = before.get(before.size() - back);
                int weight = back == 1 ? 3 : 1;
                if (Near.starts(w, Lex.of("venue")) >= 0) {
                    rank -= weight;
                } else if (Near.starts(w, Lex.of("book")) >= 0 || Near.which(w, Lex.of("event")) >= 0) {
                    rank += weight;
                }
            }
            out.add(new Title(Near.mend(t).replaceAll("[\\s.,;:!]+$", ""), line, rank));
        }
        return out;
    }

    /** The likeliest book among the titles, the first of the best. */
    static String best(List<Title> titles, String text) {
        Title chosen = null;
        for (int i = 0; i < titles.size(); i++) {
            Title t = titles.get(i);
            if (chosen == null || t.rank > chosen.rank) {
                chosen = t;
            }
        }
        return chosen == null || chosen.rank < -1 ? "" : chosen.text;
    }

    /** The title on a meeting's own line, or on the line under it if that line holds no day of its own. */
    private static String titleOn(List<Title> titles, int line, String[] rows, HashSet<Integer> dayLines) {
        for (int i = 0; i < titles.size(); i++) {
            if (titles.get(i).line == line && titles.get(i).rank >= -1) {
                return titles.get(i).text;
            }
        }
        if (!dayLines.contains(Integer.valueOf(line + 1))) {
            for (int i = 0; i < titles.size(); i++) {
                if (titles.get(i).line == line + 1 && titles.get(i).rank >= -1) {
                    return titles.get(i).text;
                }
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ merging

    /**
     * One meeting told twice is one card: the same day, and the same hour or
     * an hour said in only one of them. The richer telling stands, the
     * other lends it what it lacks, and a calling-off in either crosses it.
     *
     * A small club tells one meeting in instalments — a rough date, then the
     * hour, then the place — so a telling said only roughly stands last, and
     * lends a firm one nothing but its book: an approximate garden must not
     * become the place of a meeting whose place is simply not yet read. A
     * name said loosely gives way to a title in quotes. A detail promised
     * for later is kept promised until the card has both a place and an hour.
     */
    static ArrayList<Bill> merge(List<Bill> all) {
        ArrayList<Bill> sorted = new ArrayList<Bill>(all);
        Collections.sort(sorted, new Comparator<Bill>() {
            public int compare(Bill a, Bill b) {
                if (a.rough != b.rough) {
                    return a.rough ? 1 : -1;
                }
                if (a.weight != b.weight) {
                    return b.weight - a.weight;
                }
                return Long.compare(b.posted, a.posted);
            }
        });
        ArrayList<Bill> out = new ArrayList<Bill>();
        for (int i = 0; i < sorted.size(); i++) {
            Bill b = sorted.get(i);
            Bill same = null;
            for (int k = 0; k < out.size() && same == null; k++) {
                Bill o = out.get(k);
                if (o.source.equals(b.source) && o.day == b.day
                    && (o.minutes == b.minutes || o.minutes < 0 || b.minutes < 0)) {
                    same = o;
                }
            }
            if (same == null) {
                out.add(b);
                continue;
            }
            boolean lends = !b.rough || same.rough;
            if (lends && same.minutes < 0) {
                same.minutes = b.minutes;
            }
            if (b.book.length() > 0 && (same.book.length() == 0 || (same.loose && !b.loose))) {
                same.book = b.book;
                same.loose = b.loose;
            }
            if (lends && same.place.length() == 0) {
                same.place = b.place;
            }
            /* A later post that calls it off, or moves it, speaks for the meeting. */
            if (b.posted >= same.posted || b.cancelled || b.moved) {
                same.cancelled = same.cancelled || b.cancelled;
                same.moved = same.moved || b.moved;
                if ((b.cancelled || b.moved) && b.post != same.post && b.text.length() > 0) {
                    same.note = b.text;
                }
            }
        }
        for (int i = 0; i < out.size(); i++) {
            Bill o = out.get(i);
            if (o.pending && o.place.length() > 0 && o.minutes >= 0) {
                o.pending = false;
            }
        }
        return out;
    }

    /** Meetings in the order of the calendar: by day, then by hour, the hourless last. */
    static void order(List<Bill> bills, final Map<String, String> titles) {
        Collections.sort(bills, new Comparator<Bill>() {
            public int compare(Bill a, Bill b) {
                if (a.day != b.day) {
                    return a.day - b.day;
                }
                int am = a.minutes < 0 ? 24 * 60 : a.minutes;
                int bm = b.minutes < 0 ? 24 * 60 : b.minutes;
                if (am != bm) {
                    return am - bm;
                }
                String at = titles == null ? a.source : String.valueOf(titles.get(a.source));
                String bt = titles == null ? b.source : String.valueOf(titles.get(b.source));
                return at.compareToIgnoreCase(bt);
            }
        });
    }

    // ------------------------------------------------------------------ learning

    /**
     * The tag a channel marks its announcements with, if it has one it keeps
     * to: carried by at least half the posts that name a day and call people
     * together, by at least two of them, and by few of the rest. A tag the
     * channel puts on everything tells nothing and is not learned.
     */
    static String learn(List<Post> posts, Calendar now) {
        int calling = 0;
        int others = 0;
        HashMap<String, Integer> inCalling = new HashMap<String, Integer>();
        HashMap<String, Integer> inOthers = new HashMap<String, Integer>();
        for (int i = 0; i < posts.size(); i++) {
            Post p = posts.get(i);
            List<String> words = Near.words(p.text);
            Calendar posted = (Calendar) now.clone();
            if (p.posted > 0) {
                posted.setTimeInMillis(p.posted);
            }
            boolean likely = !Days.find(p.text, posted).days.isEmpty() && calls(words);
            List<String> tags = tags(p.text);
            HashMap<String, Integer> into = likely ? inCalling : inOthers;
            if (likely) {
                calling++;
            } else {
                others++;
            }
            for (int k = 0; k < tags.size(); k++) {
                Integer had = into.get(tags.get(k));
                into.put(tags.get(k), Integer.valueOf(had == null ? 1 : had.intValue() + 1));
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : inCalling.entrySet()) {
            int count = e.getValue().intValue();
            Integer elsewhere = inOthers.get(e.getKey());
            int rest = elsewhere == null ? 0 : elsewhere.intValue();
            if (count >= 2 && count * 2 >= calling && rest * 4 <= Math.max(others, 1)
                && count > bestCount) {
                best = e.getKey();
                bestCount = count;
            }
        }
        return best == null ? "" : best;
    }

    /*
     * Whether a text calls a meeting off or moves it. Held strictly, with no
     * slip forgiven: one letter away from calling off is asking readers to
     * sign up, and a forgiven slip there crossed out a meeting that stands.
     */
    private static boolean said(List<String> words, String kind) {
        List<String> looked = Lex.of(kind);
        for (int i = 0; i < words.size(); i++) {
            if (Near.starts(words.get(i), looked) >= 0) {
                return true;
            }
        }
        return false;
    }
}
