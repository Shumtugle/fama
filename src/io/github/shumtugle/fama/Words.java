package io.github.shumtugle.fama;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The string table.
 *
 * English lives in the source, keyed by a stable id. Every other tongue lives
 * outside the source, in a module anyone can fill in a text editor and hand
 * to a stranger. A missing key falls back to English silently, so a module is
 * useful from its first line.
 *
 * One module travels inside the package, beside the code rather than in it,
 * and is taken up by itself the first time the application opens on a phone
 * that speaks its language: whoever the board is for should not have to know
 * that languages are modules. Once chosen or refused, the choice is theirs.
 */
public final class Words {

    /** The line a module opens with; it is how a module is told from any other text. */
    public static final String HEAD = "# a language for this board";

    public static final String[] IDS = {
        "board", "empty_title", "empty_what", "quiet_title", "quiet_what", "first_read",
        "ahead_n", "read_at", "reading_n", "today", "tomorrow", "day_1", "day_2", "day_3",
        "day_4", "day_5", "day_6", "day_7", "month_1", "month_2", "month_3", "month_4", "month_5",
        "month_6", "month_7", "month_8", "month_9", "month_10", "month_11", "month_12",
        "cancelled", "moved", "open_post", "open_channel", "share", "hide", "hidden", "post",
        "posted_on", "to_calendar", "links", "no_words", "settings", "sources", "sources_what",
        "no_sources", "tag_none", "tag_is", "tag_learned", "tag_hint", "tag_set", "tag_off",
        "found_n", "state_closed", "state_silent", "state_wait", "state_group", "group_hint",
        "remove", "remove_sure", "removed", "lexicon", "lexicon_what", "lexicon_about",
        "lexicon_calls", "lexicon_calls_what", "lexicon_away", "lexicon_away_what", "word_add",
        "word_hint", "word_on", "word_off", "word_remove", "word_removed", "word_switched_on",
        "word_switched_off", "word_added", "word_short", "word_there", "words_diff", "words_same",
        "look", "look_what", "hue", "richness", "wallpaper", "sw_primary", "sw_secondary",
        "sw_tertiary", "sw_ground", "language", "language_what", "english", "words_of",
        "module_load", "module_save", "use_english", "module_taken", "module_bad", "module_saved",
        "unsaved", "stale", "carry", "carry_what", "carry_about", "carry_n", "carry_save",
        "carry_load", "carry_saved", "carry_sheet", "carry_take", "carry_done", "carry_bad",
        "many_n", "many_there", "add_all", "about", "about_what", "about_version", "about_name_h",
        "about_name", "about_how_h", "about_how", "about_links_h", "link_channel", "link_handle",
        "link_message", "link_closed", "link_folder", "link_list", "link_carry", "about_lang_h",
        "about_lang", "about_not_h", "about_not", "about_home", "log", "log_what", "log_copy",
        "log_before", "copied", "take_reading", "take_add", "take_there", "take_added",
        "take_next", "take_none", "take_closed", "take_folder", "take_group", "take_group_none",
        "take_silent", "take_no_day", "take_note", "by_hand", "close", "add_channel", "add_hint",
        "added", "not_channel", "take_unread",
    };

    private static final String[] EN = {
        "what's on",
        "nothing is announced yet",
        "share a channel here from the messenger, and whatever it announces will stand on this board by itself",
        "no meetings ahead",
        "the channels are read, and none of them names a day still to come",
        "reading the channels for the first time",
        "ahead: {n}",
        "read at {t}",
        "reading {n} of {m}",
        "today",
        "tomorrow",
        "monday",
        "tuesday",
        "wednesday",
        "thursday",
        "friday",
        "saturday",
        "sunday",
        "january",
        "february",
        "march",
        "april",
        "may",
        "june",
        "july",
        "august",
        "september",
        "october",
        "november",
        "december",
        "called off",
        "moved",
        "open the announcement",
        "open the channel",
        "share",
        "take off the board",
        "taken off the board",
        "the announcement",
        "posted {d}",
        "to the calendar",
        "links",
        "the words of this announcement come with the next reading",
        "settings",
        "channels",
        "where the board is gathered from; a tag keeps only the posts that carry it",
        "no channels yet: share one from the messenger",
        "no tag: every post is weighed",
        "tag: {t}",
        "noticed by itself: {t}",
        "a tag, like #announcement",
        "the tag is set",
        "the tag is taken off",
        "{n} ahead",
        "cannot be read: the channel has no public page",
        "did not answer: check the network or the tunnel",
        "not read yet",
        "a group, not a channel",
        "a group has no open wall: its announcements come here one by one, shared as links to their messages",
        "remove",
        "remove?",
        "the channel is removed",
        "words",
        "what makes a post an announcement, and what turns it away",
        "A word is as large as the number of announcements it found at the last reading. Touch a word to bring it forward; touch it again to change it.",
        "they call",
        "they add weight, as the word for a meeting does",
        "they turn away",
        "they take weight off: reports of what has already been",
        "add a word",
        "a word or a phrase: master class",
        "switch on",
        "switch off",
        "take away",
        "the word is taken away; reading again",
        "the word is on; reading again",
        "the word is off; reading again",
        "the word is added; reading again",
        "too short: three letters at least",
        "this word is already there",
        "meetings: +{a}, −{r}",
        "the board is the same",
        "colour",
        "one seed, and every colour grows from it",
        "hue",
        "richness",
        "from the wallpaper",
        "primary",
        "secondary",
        "tertiary",
        "ground",
        "language",
        "english lives inside; every other language is a module",
        "english",
        "{n} of {m} words",
        "load a module",
        "save the template",
        "use english",
        "the language is taken",
        "this is not a language module",
        "the template is saved",
        "it could not be saved",
        "words whose english has changed since the module was made: {n}",
        "carry over",
        "channels, meetings sent by hand, what was taken off, and the colour, as one text",
        "Everything the board knows by itself: the channels with their tags, the meetings sent by hand, the meetings taken off, and the colour. Meetings from channels are not carried: the channels will tell them again.",
        "channels: {c}  ·  by hand: {b}  ·  taken off: {h}",
        "save to a file",
        "load from a file",
        "the board is saved",
        "channels not yet on the board come onto it and are read; what is already there stays as it is",
        "carry over",
        "channels carried over: {c}",
        "this file holds neither a carried board nor a channel",
        "channels in the text: {n}",
        "already on the board: {n}",
        "add them all",
        "about",
        "what the name means, how the board works, what it understands",
        "version {v}  ·  MIT licence",
        "the name",
        "Fama is Latin for what is said of a thing: report, rumour, and fame. In Virgil she has as many tongues as feathers and runs through the city telling what has happened, and the painters give her a trumpet. Here she tells only what the channels themselves announce.",
        "how it works",
        "There is no server and no account. The phone itself opens the public page of each channel, over whatever network it has, and weighs every post: a day still ahead is the spine of an announcement, and words that call people together, the channel's tag, an hour, a place and a title in quotes add their weight. A meeting called off is crossed out, a moved one is marked. The board reads the channels again when it is opened after half an hour, and keeps an announcement's words only until its day has passed.",
        "what it understands",
        "a public channel: read whole, and read again",
        "the same, by its short name",
        "one message; from a group, the only way in",
        "invitations and closed chats: they have no public page and are not read",
        "a folder: not read whole; its channels one by one",
        "any text that names several channels: all of them at once",
        "a carried board: channels, meetings sent by hand, what was taken off, the colour",
        "languages",
        "The interface speaks English by itself; Russian travels inside as a module and is taken up on a phone set to Russian; any other language is a module filled in by hand, under Settings, Language. Announcements are read in Russian: the words for meetings, days, months and places lie in a file beside the code. English announcements are next.",
        "what it does not do",
        "It signs in nowhere, reads no closed channel and no group's talk, and reads no network that shows its pages only to those who have signed in.",
        "the source",
        "journal",
        "what the application did, to send when something goes wrong",
        "copy the journal",
        "the run before",
        "copied",
        "reading the channel",
        "to the board",
        "already on the board",
        "on the board",
        "the nearest",
        "no meetings ahead yet; it will be read again",
        "a closed channel has no public page; share its announcements one by one",
        "a folder is not read whole yet; share its channels one by one",
        "this is a group, not a channel: a group's talk has no open wall. Share its announcements here one by one, as links to their messages",
        "this is a group, not a channel, and the message did not read or names no day ahead",
        "the channel did not answer; check the network or the tunnel",
        "no day ahead is named in this text",
        "an announcement",
        "by hand",
        "close",
        "add a channel",
        "a channel's link or @name",
        "the channel is added",
        "this is neither a channel's link nor its name",
        "the channel's page did not read just now; it can go on the board, and will be read again",
    };

    private static final Map<String, String> TABLE = new LinkedHashMap<String, String>();
    private static final Map<String, String> MINE = new LinkedHashMap<String, String>();
    /** For every word of the module, the English it was translated from, as the module said. */
    private static final Map<String, String> FROM = new LinkedHashMap<String, String>();
    private static String moduleName = "";

    static {
        for (int i = 0; i < IDS.length; i++) {
            TABLE.put(IDS[i], EN[i]);
        }
    }

    private Words() {
    }

    public static String en(String id) {
        String s = TABLE.get(id);
        return s == null ? id : s;
    }

    /** The word as the reader should see it: their module first, English behind it. */
    public static String s(String id) {
        String s = MINE.get(id);
        if (s != null && s.length() > 0) {
            return s;
        }
        return en(id);
    }

    public static String name() {
        return moduleName;
    }

    public static boolean active() {
        return MINE.size() > 0;
    }

    public static int filled() {
        return MINE.size();
    }

    public static int total() {
        return IDS.length;
    }

    public static void forget() {
        MINE.clear();
        FROM.clear();
        moduleName = "";
    }

    /**
     * How many words of the module were translated from an English that has
     * changed since: the key is the same, the meaning moved on, and the old
     * translation still stands until a newer module comes.
     */
    public static int stale() {
        int n = 0;
        for (Map.Entry<String, String> entry : FROM.entrySet()) {
            if (MINE.containsKey(entry.getKey()) && !entry.getValue().equals(en(entry.getKey()))) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- storage

    public static void load(Context context) {
        SharedPreferences p = context.getSharedPreferences("words", Context.MODE_PRIVATE);
        moduleName = p.getString("!name", "");
        MINE.clear();
        FROM.clear();
        for (int i = 0; i < IDS.length; i++) {
            String v = p.getString(IDS[i], "");
            if (v.length() > 0) {
                MINE.put(IDS[i], v);
            }
            String was = p.getString("~" + IDS[i], null);
            if (was != null) {
                FROM.put(IDS[i], was);
            }
        }
        if (!p.getBoolean("!settled", false)) {
            settle(context);
        } else if (active() && filled() < total()) {
            refresh(context);
        }
    }

    /** The module that travels inside for this phone's language, or nothing. */
    private static String inside(Context context) {
        String tongue = Locale.getDefault().getLanguage();
        try {
            InputStream in = context.getAssets().open("modules/" + tongue + ".txt");
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int got;
            while ((got = in.read(chunk)) > 0) {
                all.write(chunk, 0, got);
            }
            in.close();
            String text = new String(all.toByteArray(), "UTF-8");
            return isModule(text) ? text : null;
        } catch (Exception none) {
            return null;
        }
    }

    /**
     * A newer version brought words the module in use lacks. If that module
     * is the one that travels inside, and the one inside now says more, the
     * one inside takes its place; a module loaded by hand is never touched.
     */
    private static void refresh(Context context) {
        String text = inside(context);
        if (text == null) {
            return;
        }
        String hadName = moduleName;
        int had = filled();
        Map<String, String> mine = new LinkedHashMap<String, String>(MINE);
        Map<String, String> from = new LinkedHashMap<String, String>(FROM);
        read(text);
        if (moduleName.equals(hadName) && filled() > had) {
            save(context);
            Trace.note("language: the module inside grew, " + had + " to " + filled());
            return;
        }
        MINE.clear();
        MINE.putAll(mine);
        FROM.clear();
        FROM.putAll(from);
        moduleName = hadName;
    }

    /**
     * The first opening: if the phone speaks the language of the module that
     * travels inside, the module is taken up. Whatever happens, it happens
     * once; afterwards only a hand changes the language.
     */
    private static void settle(Context context) {
        String text = inside(context);
        if (text != null) {
            read(text);
            save(context);
            Trace.note("language: the module inside taken up, " + Locale.getDefault().getLanguage());
        }
        context.getSharedPreferences("words", Context.MODE_PRIVATE).edit()
            .putBoolean("!settled", true).apply();
    }

    public static void save(Context context) {
        SharedPreferences.Editor e =
            context.getSharedPreferences("words", Context.MODE_PRIVATE).edit();
        e.clear();
        e.putBoolean("!settled", true);
        e.putString("!name", moduleName);
        for (Map.Entry<String, String> entry : MINE.entrySet()) {
            e.putString(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : FROM.entrySet()) {
            e.putString("~" + entry.getKey(), entry.getValue());
        }
        e.apply();
    }

    // ----------------------------------------------------------------- module

    /**
     * A module is plain text, and it carries every line whether filled or not:
     * the English above as a comment, the key and the word below. Written out
     * whole, it can be finished in any editor by someone who has never seen
     * the application, and brought back in.
     */
    public static String write() {
        StringBuilder b = new StringBuilder();
        b.append(HEAD).append('\n');
        b.append("# fill the empty lines and load the file back\n\n");
        b.append("module ").append(moduleName.length() > 0 ? moduleName : "untitled")
            .append("\n\n");
        for (int i = 0; i < IDS.length; i++) {
            String mine = MINE.get(IDS[i]);
            b.append("# ").append(en(IDS[i])).append('\n');
            b.append(IDS[i]).append('\t').append(mine == null ? "" : mine).append("\n\n");
        }
        return b.toString();
    }

    /** Whether a text is a module at all, judged by its first line. */
    public static boolean isModule(String text) {
        if (text == null) {
            return false;
        }
        String t = text.startsWith("\uFEFF") ? text.substring(1) : text;
        return t.trim().startsWith(HEAD);
    }

    public static void read(String text) {
        if (text == null) {
            return;
        }
        MINE.clear();
        FROM.clear();
        moduleName = "";
        String[] lines = text.split("\n");
        /* The English a line was made from stands as a comment just above it. */
        String above = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.charAt(0) == '#') {
                above = line.substring(1).trim();
                continue;
            }
            int cut = line.indexOf('\t');
            if (cut < 0) {
                cut = line.indexOf(' ');
            }
            if (cut <= 0) {
                continue;
            }
            String key = line.substring(0, cut).trim();
            String value = line.substring(cut + 1).trim();
            if (key.equals("module")) {
                moduleName = value;
            } else if (TABLE.containsKey(key) && value.length() > 0) {
                MINE.put(key, value);
                if (above != null) {
                    FROM.put(key, above);
                }
            }
            above = null;
        }
    }
}
