package io.github.shumtugle.fama;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Words compared the forgiving way.
 *
 * A post is written in a hurry, on a phone, sometimes by someone who wants
 * it to slip past a filter. So a word is met not only as it is spelled but
 * as it was meant: whatever the case, whichever of the two spellings of one
 * letter, with a grammatical ending of its own, with a typo or two if it is
 * long enough to carry them, with two neighbouring letters swapped, with
 * Latin letters that only look Cyrillic, typed on the wrong keyboard layout,
 * glued to a picture, or with a letter held down too long.
 *
 * Typos are rarely in the first letters, so a middle-length word is forgiven
 * two only when its start is right, and a word of three letters or fewer is
 * forgiven nothing at all: short words are where false friends live.
 */
final class Near {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** The Cyrillic alphabet, and what each letter is written as in Latin. */
    private static final String RU =
        "\u0430\u0431\u0432\u0433\u0434\u0435\u0451\u0436\u0437\u0438\u0439\u043a\u043b\u043c"
        + "\u043d\u043e\u043f\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044a"
        + "\u044b\u044c\u044d\u044e\u044f";
    private static final String[] LA = {
        "a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "i", "k", "l", "m", "n", "o", "p",
        "r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya",
    };

    /** The two keyboards, key under key: a word typed with the wrong one still counts. */
    private static final String KEN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
    private static final String KRU =
        "\u0439\u0446\u0443\u043a\u0435\u043d\u0433\u0448\u0449\u0437\u0445\u044a\u0444\u044b"
        + "\u0432\u0430\u043f\u0440\u043e\u043b\u0434\u0436\u044d\u044f\u0447\u0441\u043c\u0438"
        + "\u0442\u044c\u0431\u044e\u0451";

    /**
     * Latin letters that pass for Cyrillic ones, and the Cyrillic they pass
     * for. Only a word that already has Cyrillic in it is mended with this:
     * a word wholly in Latin is Latin, and is left alone.
     */
    private static final String LOOKS = "aceopxykABCEHKMOPTXY";
    private static final String IS =
        "\u0430\u0441\u0435\u043e\u0440\u0445\u0443\u043a"
        + "\u0410\u0412\u0421\u0415\u041d\u041a\u041c\u041e\u0420\u0422\u0425\u0423";

    private Near() {
    }

    // ------------------------------------------------------------ one text

    /**
     * A text made plain for comparing: disguised letters mended, lower case,
     * one spelling of each letter, no accents, a letter held down let go,
     * and everything that is not a letter or a digit turned into a space.
     */
    static String norm(String text) {
        if (text == null) {
            return "";
        }
        String t = mend(text).toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
        t = MARKS.matcher(Normalizer.normalize(t, Normalizer.Form.NFD)).replaceAll("");
        t = NOT_WORD.matcher(t).replaceAll(" ").trim();
        return unheld(t);
    }

    /** The words of a text, made plain, in order. A picture glued to a word falls away from it. */
    static ArrayList<String> words(String text) {
        ArrayList<String> out = new ArrayList<String>();
        String t = norm(text);
        if (t.length() == 0) {
            return out;
        }
        String[] parts = t.split(" ");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() > 0) {
                out.add(parts[i]);
            }
        }
        return out;
    }

    /**
     * Latin look-alikes inside a Cyrillic word put back. The word is a run
     * of letters; if any of them is Cyrillic, every Latin letter in it that
     * has a Cyrillic twin becomes that twin.
     */
    static String mend(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) {
                out.append(c);
                i++;
                continue;
            }
            int end = i;
            boolean cyrillic = false;
            boolean latin = false;
            while (end < n && Character.isLetter(text.charAt(end))) {
                char d = text.charAt(end);
                if (d >= '\u0400' && d <= '\u04FF') {
                    cyrillic = true;
                } else if (d < 128) {
                    latin = true;
                }
                end++;
            }
            for (int k = i; k < end; k++) {
                char d = text.charAt(k);
                int twin = cyrillic && latin ? LOOKS.indexOf(d) : -1;
                out.append(twin >= 0 ? IS.charAt(twin) : d);
            }
            i = end;
        }
        return out.toString();
    }

    /** Three or more of one letter in a row are one letter: a word shouted is the same word. */
    private static String unheld(String t) {
        StringBuilder out = new StringBuilder(t.length());
        int n = t.length();
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            int run = 1;
            while (i + run < n && t.charAt(i + run) == c) {
                run++;
            }
            if (run >= 3 && Character.isLetter(c)) {
                out.append(c);
            } else {
                for (int k = 0; k < run; k++) {
                    out.append(c);
                }
            }
            i += run - 1;
        }
        return out.toString();
    }

    // ------------------------------------------------------------ forms of a word

    /** A Cyrillic word written in Latin, letter by letter. */
    static String lat(String w) {
        StringBuilder o = new StringBuilder(w.length() + 4);
        for (int i = 0; i < w.length(); i++) {
            int k = RU.indexOf(w.charAt(i));
            if (k < 0) {
                o.append(w.charAt(i));
            } else {
                o.append(LA[k]);
            }
        }
        return o.toString();
    }

    /** The same keys pressed on the other keyboard. */
    static String swap(String w) {
        StringBuilder o = new StringBuilder(w.length());
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            int k = KEN.indexOf(c);
            if (k >= 0) {
                o.append(KRU.charAt(k));
                continue;
            }
            k = KRU.indexOf(c);
            o.append(k >= 0 ? KEN.charAt(k) : c);
        }
        return o.toString();
    }

    // ------------------------------------------------------------ distance

    /**
     * How many single changes turn one word into the other: a letter put in,
     * taken out, or changed, and two neighbours swapped counted as one.
     */
    static int dl(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                    && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, d[i - 2][j - 2] + c);
                }
                d[i][j] = best;
            }
        }
        return d[m][n];
    }

    /** How many changes a word may carry and still be itself, by its length and its start. */
    static int room(String v, String w) {
        int n = v.length();
        if (n <= 3) {
            return 0;
        }
        if (n == 4) {
            return 1;
        }
        if (n <= 7) {
            return w.length() >= 3 && v.regionMatches(0, w, 0, 3) ? 2 : 1;
        }
        return 2;
    }

    /**
     * Whether a word, or the beginning of one, is met by another. The first is
     * what is looked for — often only a stem — and the second what stands in
     * the text: it may begin with the first, begin with all of it but its
     * last letter, or differ from it, or from its own beginning, by no more
     * than the room the first word has.
     */
    static boolean meets(String v, String w) {
        if (v.length() == 0) {
            return true;
        }
        if (w.startsWith(v)) {
            return true;
        }
        int r = room(v, w);
        if (r == 0) {
            return false;
        }
        if (v.length() >= 5 && w.startsWith(v.substring(0, v.length() - 1))) {
            return true;
        }
        if (Math.abs(w.length() - v.length()) <= r && dl(v, w) <= r) {
            return true;
        }
        return w.length() > v.length() && dl(v, w.substring(0, v.length())) <= r;
    }

    /** Whether a plain word matches a looked-for one in any of its forms. */
    static boolean like(String looked, String word) {
        if (looked.length() <= 3) {
            return word.equals(looked);
        }
        if (meets(looked, word)) {
            return true;
        }
        String l = lat(looked);
        String w = lat(word);
        if (!l.equals(looked) || !w.equals(word)) {
            if (meets(l, w)) {
                return true;
            }
        }
        String s = swap(word);
        return !s.equals(word) && meets(looked, s);
    }

    /** Whether any word of a text is met by any of the looked-for ones. */
    static boolean any(List<String> words, List<String> looked) {
        for (int i = 0; i < words.size(); i++) {
            if (which(words.get(i), looked) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which of the looked-for words a word begins with, strictly: no typo
     * forgiven. For kinds of words whose short stems would otherwise meet
     * too much — a café is one slip from a surname.
     */
    static int starts(String word, List<String> looked) {
        for (int k = 0; k < looked.size(); k++) {
            String l = looked.get(k);
            if (l.length() <= 3 ? word.equals(l) : word.startsWith(l)) {
                return k;
            }
        }
        return -1;
    }

    /** Which of the looked-for words a word is met by, or minus one. */
    static int which(String word, List<String> looked) {
        for (int k = 0; k < looked.size(); k++) {
            if (like(looked.get(k), word)) {
                return k;
            }
        }
        return -1;
    }
}
