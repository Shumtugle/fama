package io.github.shumtugle.fama;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The days and the hours a text speaks of.
 *
 * A date is written a dozen ways: the twenty seventh of September, the
 * twenty seventh with its ordinal ending, 27.09, 27/09/2026, this Saturday,
 * next Saturday, tomorrow. Each is found here and turned into one day of the
 * calendar, counted from the day the post was written, not from today:
 * "tomorrow" in a post of last Tuesday is last Wednesday.
 *
 * The text is first cut into words, numbers and marks, each with the line it
 * stands on, so that a date and an hour on one line can be told to belong to
 * each other, and a list of meetings, one to a line, stays a list.
 *
 * Numbers with a dot are the one real doubt: 19.30 is an hour, 21.09 a day,
 * and 10.10 either. A month past twelve is no month, so it is an hour; a
 * number after a word that stands before hours is an hour; the rest are days.
 */
final class Days {

    static final int WORD = 0;
    static final int NUMBER = 1;
    static final int MARK = 2;

    /** A piece of the text: what kind, what it says made plain, and which line it is on. */
    static final class Piece {
        final int kind;
        final String text;
        final int line;

        Piece(int kind, String text, int line) {
            this.kind = kind;
            this.text = text;
            this.line = line;
        }
    }

    /** A day found: as year, month and day in one number, the line it was on, and where. */
    static final class Day {
        final int day;
        final int line;
        final int at;

        Day(int day, int line, int at) {
            this.day = day;
            this.line = line;
            this.at = at;
        }
    }

    /** An hour found, in minutes after midnight, with its line and place. */
    static final class Hour {
        final int minutes;
        final int line;
        final int at;

        Hour(int minutes, int line, int at) {
            this.minutes = minutes;
            this.line = line;
            this.at = at;
        }
    }

    /** Everything a text says of when. */
    static final class When {
        final ArrayList<Day> days = new ArrayList<Day>();
        final ArrayList<Hour> hours = new ArrayList<Hour>();
    }

    private static final String MARKS = ".:/-\u2013\u2014,()";

    private Days() {
    }

    // ------------------------------------------------------------------ cutting

    /** The text cut into words, numbers and the marks that join them. */
    static ArrayList<Piece> cut(String text) {
        ArrayList<Piece> out = new ArrayList<Piece>();
        int line = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (Character.isLetter(c)) {
                int end = i;
                while (end < n && Character.isLetter(text.charAt(end))) {
                    end++;
                }
                String word = Near.norm(text.substring(i, end));
                if (word.length() > 0) {
                    out.add(new Piece(WORD, word, line));
                }
                i = end;
            } else if (c >= '0' && c <= '9') {
                int end = i;
                while (end < n && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
                    end++;
                }
                out.add(new Piece(NUMBER, text.substring(i, end), line));
                i = end;
            } else if (MARKS.indexOf(c) >= 0) {
                out.add(new Piece(MARK, String.valueOf(c), line));
                i++;
            } else {
                i++;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ finding

    /**
     * Every day and hour in a text, with the calendar of the moment the post
     * was written to count from.
     */
    static When find(String text, Calendar posted) {
        When when = new When();
        ArrayList<Piece> p = cut(text == null ? "" : text);
        boolean[] taken = new boolean[p.size()];
        ArrayList<Day> weekdays = new ArrayList<Day>();

        for (int i = 0; i < p.size(); i++) {
            Piece a = p.get(i);
            if (a.kind == NUMBER && a.text.length() <= 2) {
                int d = Integer.parseInt(a.text);
                if (d < 1 || d > 31) {
                    continue;
                }
                if (worded(p, i, d, posted, when, taken)) {
                    continue;
                }
                numeric(p, i, d, posted, when, taken);
            } else if (a.kind == WORD) {
                int shift = Lex.is("today", a.text) ? 0 : Lex.is("tomorrow", a.text) ? 1
                    : Lex.is("after", a.text) ? 2 : -1;
                if (shift >= 0) {
                    Calendar c = (Calendar) posted.clone();
                    c.add(Calendar.DAY_OF_MONTH, shift);
                    when.days.add(new Day(stamp(c), a.line, i));
                    continue;
                }
                int wd = weekday(a.text);
                if (wd > 0) {
                    boolean further = i > 0 && p.get(i - 1).kind == WORD
                        && Near.any(one(p.get(i - 1).text), Lex.of("next"));
                    weekdays.add(new Day(stamp(onWeekday(posted, wd, further)), a.line, i));
                }
            }
        }

        /* A day of the week beside a date only names the date again. */
        for (int k = 0; k < weekdays.size(); k++) {
            Day w = weekdays.get(k);
            boolean named = false;
            for (int j = 0; j < when.days.size() && !named; j++) {
                named = when.days.get(j).line == w.line;
            }
            if (!named) {
                when.days.add(w);
            }
        }

        hours(p, taken, when);
        return when;
    }

    /** A number and a month's name: the twenty seventh of September, perhaps of a year. */
    private static boolean worded(List<Piece> p, int i, int d, Calendar posted, When when,
                                  boolean[] taken) {
        int j = i + 1;
        if (j + 1 < p.size() && mark(p.get(j), "-") && p.get(j + 1).kind == WORD
            && Lex.is("ordinal", p.get(j + 1).text)) {
            j += 2;
        } else if (j < p.size() && p.get(j).kind == WORD && Lex.is("ordinal", p.get(j).text)
            && j + 1 < p.size() && p.get(j + 1).kind == WORD && month(p.get(j + 1).text) > 0) {
            j += 1;
        }
        if (j >= p.size() || p.get(j).kind != WORD) {
            return false;
        }
        int m = month(p.get(j).text);
        if (m <= 0) {
            return false;
        }
        int y = 0;
        if (j + 1 < p.size() && p.get(j + 1).kind == NUMBER && p.get(j + 1).text.length() == 4) {
            int given = Integer.parseInt(p.get(j + 1).text);
            if (given >= 2000 && given <= 2100) {
                y = given;
                taken[j + 1] = true;
            }
        }
        int day = resolve(d, m, y, posted);
        if (day > 0) {
            when.days.add(new Day(day, p.get(i).line, i));
            taken[i] = true;
            taken[j] = true;
        }
        /* The days listed before it share its month: the third, tenth and seventeenth of October. */
        int k = i - 1;
        while (k - 1 >= 0) {
            Piece joint = p.get(k);
            boolean joins = joint.kind == MARK && ",-\u2013\u2014".indexOf(joint.text.charAt(0)) >= 0
                || joint.kind == WORD && Lex.is("join", joint.text);
            Piece earlier = p.get(k - 1);
            if (!joins || earlier.kind != NUMBER || earlier.text.length() > 2 || taken[k - 1]) {
                break;
            }
            int e = Integer.parseInt(earlier.text);
            if (e < 1 || e > 31) {
                break;
            }
            int other = resolve(e, m, y, posted);
            if (other > 0) {
                when.days.add(new Day(other, earlier.line, k - 1));
                taken[k - 1] = true;
            }
            k -= 2;
        }
        return true;
    }

    /** A day written in figures: 27.09, 27/09, 27.09.26, 3.5.2026. */
    private static void numeric(List<Piece> p, int i, int d, Calendar posted, When when,
                                boolean[] taken) {
        if (i + 2 >= p.size()) {
            return;
        }
        Piece sep = p.get(i + 1);
        Piece mp = p.get(i + 2);
        if (sep.kind != MARK || !(".".equals(sep.text) || "/".equals(sep.text))
            || mp.kind != NUMBER || mp.text.length() > 2) {
            return;
        }
        /* The tail of a longer number, a price or a version, is no day. */
        if (i >= 2 && mark(p.get(i - 1), ".") && p.get(i - 2).kind == NUMBER) {
            return;
        }
        int m = Integer.parseInt(mp.text);
        int y = 0;
        boolean yearGiven = false;
        if (i + 4 < p.size() && mark(p.get(i + 3), sep.text) && p.get(i + 4).kind == NUMBER) {
            String ys = p.get(i + 4).text;
            if (ys.length() == 2 || ys.length() == 4) {
                y = Integer.parseInt(ys);
                if (y < 100) {
                    y += 2000;
                }
                yearGiven = true;
            } else {
                return;
            }
        }
        if (m < 1 || m > 12) {
            return;
        }
        /* A month in one figure is too easily something else: three and a half hours. */
        if (mp.text.length() == 1 && !yearGiven) {
            return;
        }
        /* After a word that stands before hours, and a possible hour, it is the hour. */
        if (!yearGiven && ".".equals(sep.text) && d <= 23 && mp.text.length() == 2
            && i > 0 && p.get(i - 1).kind == WORD && Lex.is("at", p.get(i - 1).text)) {
            return;
        }
        int day = resolve(d, m, y, posted);
        if (day > 0) {
            when.days.add(new Day(day, p.get(i).line, i));
            taken[i] = true;
            taken[i + 2] = true;
            if (yearGiven) {
                taken[i + 4] = true;
            }
        }
    }

    /** The hours: 19:00, 18.30, 19 hours, in whatever pieces the days left untaken. */
    private static void hours(List<Piece> p, boolean[] taken, When when) {
        for (int i = 0; i < p.size(); i++) {
            Piece a = p.get(i);
            if (taken[i] || a.kind != NUMBER || a.text.length() > 2) {
                continue;
            }
            int h = Integer.parseInt(a.text);
            if (h > 23) {
                continue;
            }
            if (i + 2 < p.size() && (mark(p.get(i + 1), ":") || mark(p.get(i + 1), "."))
                && p.get(i + 2).kind == NUMBER && p.get(i + 2).text.length() == 2 && !taken[i + 2]) {
                int m = Integer.parseInt(p.get(i + 2).text);
                if (m <= 59) {
                    when.hours.add(new Hour(h * 60 + m, a.line, i));
                    taken[i] = true;
                    taken[i + 2] = true;
                    i += 2;
                    continue;
                }
            }
            if (i + 1 < p.size() && p.get(i + 1).kind == WORD && Lex.is("hour", p.get(i + 1).text)) {
                when.hours.add(new Hour(h * 60, a.line, i));
                taken[i] = true;
            }
        }
    }

    // ------------------------------------------------------------------ the calendar

    private static boolean mark(Piece piece, String which) {
        return piece.kind == MARK && piece.text.equals(which);
    }

    private static List<String> one(String word) {
        ArrayList<String> made = new ArrayList<String>(1);
        made.add(word);
        return made;
    }

    /**
     * Which month a word names, or nothing. A short form must be exact; a
     * long one may carry one slip of the finger.
     */
    static int month(String word) {
        for (int m = 1; m <= 12; m++) {
            List<String> forms = Lex.of("month." + m);
            for (int k = 0; k < forms.size(); k++) {
                String f = forms.get(k);
                if (f.equals(word) || f.length() >= 6 && Math.abs(f.length() - word.length()) <= 1
                    && Near.dl(f, word) <= 1) {
                    return m;
                }
            }
        }
        return 0;
    }

    /** Which day of the week a word names by itself, Monday first, or nothing. */
    static int weekday(String word) {
        if (word.length() < 4) {
            return 0;
        }
        for (int d = 1; d <= 7; d++) {
            List<String> forms = Lex.of("day." + d);
            for (int k = 0; k < forms.size(); k++) {
                String f = forms.get(k);
                if (f.length() < 4) {
                    continue;
                }
                if (f.equals(word) || f.length() >= 6 && Math.abs(f.length() - word.length()) <= 1
                    && Near.dl(f, word) <= 1) {
                    return d;
                }
            }
        }
        return 0;
    }

    /**
     * The coming day of a week from the moment of the post. The same day of
     * the week as the post is that day, if the post came before noon, and a
     * week on if it came later; "next" moves it a week further.
     */
    static Calendar onWeekday(Calendar posted, int weekday, boolean further) {
        Calendar c = (Calendar) posted.clone();
        int now = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        int ahead = (weekday - now + 7) % 7;
        if (ahead == 0 && c.get(Calendar.HOUR_OF_DAY) >= 12) {
            ahead = 7;
        }
        if (further && ahead < 7) {
            ahead += 7;
        }
        c.add(Calendar.DAY_OF_MONTH, ahead);
        return c;
    }

    /**
     * A day and a month made into a date. With no year given, it is the
     * year of the post, unless that puts it two months or more before the
     * post: a January meeting announced in December is next year's.
     */
    static int resolve(int d, int m, int y, Calendar posted) {
        int year = y > 0 ? y : posted.get(Calendar.YEAR);
        Calendar c = (Calendar) posted.clone();
        c.setLenient(true);
        c.set(year, m - 1, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (d > c.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            return 0;
        }
        c.set(Calendar.DAY_OF_MONTH, d);
        if (y == 0) {
            Calendar early = (Calendar) posted.clone();
            early.add(Calendar.DAY_OF_MONTH, -60);
            if (c.before(early)) {
                c.add(Calendar.YEAR, 1);
            }
        }
        return stamp(c);
    }

    /** A calendar's day as one number: 20260927. */
    static int stamp(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100
            + c.get(Calendar.DAY_OF_MONTH);
    }

    /** One number back into a calendar, at the start of that day. */
    static Calendar calendar(int stamp) {
        Calendar c = Calendar.getInstance();
        c.set(stamp / 10000, stamp / 100 % 100 - 1, stamp % 100, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }
}
