package io.github.shumtugle.fama;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A channel's public page, read as a wall of posts.
 *
 * The page is written for a browser, not for us, so it is read by the marks
 * it leaves for its own scripts: each post opens with the channel and the
 * number it bears, its words sit in one block marked as the message text,
 * and its moment is written in a machine's form inside the link to it. The
 * words are taken out of their markup the plain way — line breaks kept,
 * tags dropped, entities spelled out — since the reading after this cares
 * about words, not about how they were set.
 *
 * A quoted message inside a reply has a block of its own under another
 * mark; it is not the post's own text and is not taken for it.
 */
final class Wall {

    String title = "";
    String photo = "";
    final ArrayList<Sift.Post> posts = new ArrayList<Sift.Post>();

    private static final Pattern META = Pattern.compile(
        "<meta\\s+property=\"og:(title|image)\"\\s+content=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");
    /** The number at the end of a link to a post, before any question the link asks. */
    private static final Pattern NUMBERED = Pattern.compile("href=\"[^\"]*/(\\d+)(?:\\?[^\"]*)?\"");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|</(?:div|p|blockquote|li)>");
    private static final Pattern ANCHOR = Pattern.compile(
        "(?is)<a\\s[^>]*?href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    private Wall() {
    }

    /** Whether the page is a group's: it shows who is in it, and never a wall. */
    boolean group;

    /** Whether the page showed a wall at all: a channel that is closed or empty shows none. */
    boolean open() {
        return !posts.isEmpty();
    }

    /** The lowest post number read, for asking the page for what came before it. */
    int earliest() {
        int low = Integer.MAX_VALUE;
        for (int i = 0; i < posts.size(); i++) {
            low = Math.min(low, posts.get(i).id);
        }
        return low == Integer.MAX_VALUE ? 0 : low;
    }

    /** The page read. */
    static Wall read(String html) {
        Wall wall = new Wall();
        if (html == null) {
            return wall;
        }
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String value = plain(meta.group(2)).trim();
            if ("title".equalsIgnoreCase(meta.group(1)) && wall.title.length() == 0) {
                wall.title = value;
            } else if ("image".equalsIgnoreCase(meta.group(1)) && wall.photo.length() == 0) {
                wall.photo = value;
            }
        }
        Matcher head = Pattern.compile("class=\"" + Pattern.quote(Ways.get("mark.title"))
            + "\"[^>]*>(?:\\s*<span[^>]*>)?([^<]+)").matcher(html);
        if (head.find()) {
            String named = plain(head.group(1)).trim();
            if (named.length() > 0) {
                wall.title = named;
            }
        }
        if (wall.photo.length() == 0) {
            Matcher photo = Pattern.compile("class=\"" + Pattern.quote(Ways.get("mark.photo"))
                + "[^\"]*\"[^>]*>\\s*<img\\s+src=\"([^\"]+)\"").matcher(html);
            if (photo.find()) {
                wall.photo = photo.group(1);
            }
        }
        if (wall.photo.startsWith("//")) {
            wall.photo = "https:" + wall.photo;
        }

        String extraMark = Ways.get("mark.extra");
        if (extraMark.length() > 0) {
            Matcher extra = Pattern.compile("class=\"" + Pattern.quote(extraMark)
                + "\"[^>]*>([^<]*)").matcher(html);
            if (extra.find()) {
                String count = plain(extra.group(1)).toLowerCase(Locale.ROOT);
                String[] words = Ways.list("group");
                for (int i = 0; i < words.length && !wall.group; i++) {
                    wall.group = count.contains(words[i].toLowerCase(Locale.ROOT));
                }
            }
        }

        String postMark = Ways.get("mark.post");
        if (postMark.length() == 0) {
            return wall;
        }
        int at = html.indexOf(postMark);
        while (at >= 0) {
            int next = html.indexOf(postMark, at + postMark.length());
            String block = html.substring(at, next < 0 ? html.length() : next);
            Sift.Post post = post(block, postMark);
            boolean again = false;
            for (int k = 0; post != null && k < wall.posts.size() && !again; k++) {
                again = wall.posts.get(k).id == post.id;
            }
            if (post != null && !again) {
                wall.posts.add(post);
            }
            at = next;
        }
        return wall;
    }

    /** One post from the stretch of page that belongs to it, or nothing if it has no words. */
    private static Sift.Post post(String block, String postMark) {
        String inner = messageInner(block);
        String text = inner == null ? null : plain(inner);
        if (text == null || text.trim().length() == 0) {
            return null;
        }
        /* The post's number is read from the link on its date, which is the
           post's own address; the mark at its head is the fallback, since the
           pictures of an album may carry marks of their own. */
        int id = -1;
        String dateMark = Ways.get("mark.date");
        int dated = dateMark.length() == 0 ? -1 : block.indexOf(dateMark);
        if (dated >= 0) {
            Matcher numbered = NUMBERED.matcher(block);
            if (numbered.find(Math.max(0, block.lastIndexOf('<', dated)))) {
                id = number(numbered.group(1));
            }
        }
        if (id < 0) {
            int end = block.indexOf('"', postMark.length());
            String named = end < 0 ? "" : block.substring(postMark.length(), end);
            int slash = named.lastIndexOf('/');
            id = slash < 0 ? -1 : number(named.substring(slash + 1));
        }
        if (id < 0) {
            return null;
        }
        long posted = 0L;
        Matcher time = TIME.matcher(block);
        if ((dated >= 0 && time.find(dated)) || time.find(0)) {
            posted = moment(time.group(1));
        }
        Sift.Post made = new Sift.Post(id, text, posted);
        anchors(inner, made.links);
        return made;
    }

    /**
     * The links a post hides under its words: a word that leads somewhere
     * other than what it says. An address written out in full is already
     * seen in the text, and a tag leads back into the page itself; neither
     * is kept.
     */
    static void anchors(String html, ArrayList<String[]> into) {
        Matcher m = ANCHOR.matcher(html);
        while (m.find()) {
            String href = entities(m.group(1)).trim();
            String label = plain(m.group(2)).trim();
            boolean away = href.startsWith("https://") || href.startsWith("http://");
            if (!away || label.length() == 0 || label.length() > 200) {
                continue;
            }
            String bare = href.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
            String seen = label.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
            if (bare.startsWith(seen.replaceAll("\\u2026$|\\.\\.\\.$", ""))) {
                continue;
            }
            into.add(new String[] {label, href});
        }
    }

    private static int number(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException odd) {
            return -1;
        }
    }

    /** The markup of the post's own message block, or nothing. */
    private static String messageInner(String block) {
        String textMark = Ways.get("mark.text");
        if (textMark.length() == 0) {
            return null;
        }
        int from = 0;
        while (true) {
            int mark = block.indexOf(textMark, from);
            if (mark < 0) {
                return null;
            }
            char after = mark + textMark.length() < block.length()
                ? block.charAt(mark + textMark.length()) : ' ';
            if (after != '"' && after != ' ') {
                from = mark + textMark.length();
                continue;
            }
            int open = block.lastIndexOf("<div", mark);
            int start = block.indexOf('>', mark);
            if (open < 0 || start < 0) {
                return null;
            }
            int close = closing(block, start + 1);
            return block.substring(start + 1, close);
        }
    }

    /** Where the division opened just before a point closes, counting the ones inside it. */
    private static int closing(String html, int from) {
        int depth = 1;
        int i = from;
        while (i < html.length()) {
            int open = html.indexOf("<div", i);
            int shut = html.indexOf("</div", i);
            if (shut < 0) {
                return html.length();
            }
            if (open >= 0 && open < shut) {
                depth++;
                i = open + 4;
            } else {
                depth--;
                if (depth == 0) {
                    return shut;
                }
                i = shut + 5;
            }
        }
        return html.length();
    }

    /** Markup made into words: breaks kept as lines, tags gone, entities spelled out. */
    static String plain(String html) {
        String t = BREAK.matcher(html).replaceAll("\n");
        t = TAGS.matcher(t).replaceAll("");
        return entities(t).replace('\u00A0', ' ');
    }

    /** Entities spelled out, and nothing else touched. */
    private static String entities(String t) {
        Matcher m = ENTITY.matcher(t);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(entity(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String entity(String name) {
        try {
            if (name.startsWith("#x") || name.startsWith("#X")) {
                return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            }
            if (name.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(name.substring(1))));
            }
        } catch (Exception odd) {
            return "";
        }
        if ("amp".equals(name)) {
            return "&";
        }
        if ("lt".equals(name)) {
            return "<";
        }
        if ("gt".equals(name)) {
            return ">";
        }
        if ("quot".equals(name)) {
            return "\"";
        }
        if ("apos".equals(name)) {
            return "'";
        }
        if ("nbsp".equals(name)) {
            return " ";
        }
        if ("laquo".equals(name)) {
            return "\u00AB";
        }
        if ("raquo".equals(name)) {
            return "\u00BB";
        }
        if ("mdash".equals(name)) {
            return "\u2014";
        }
        if ("ndash".equals(name)) {
            return "\u2013";
        }
        return "&" + name + ";";
    }

    /** A moment written the machine's way, with its offset, as milliseconds. */
    static long moment(String iso) {
        String t = iso.trim();
        String[] shapes = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss"};
        for (int i = 0; i < shapes.length; i++) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(shapes[i], Locale.US);
                if (i == 2) {
                    f.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date d = f.parse(t);
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception wrongShape) {
                // the next shape
            }
        }
        return 0L;
    }
}
