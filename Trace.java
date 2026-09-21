package io.github.shumtugle.fama;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * A ring of the last few hundred things that happened, with the clock on each
 * one. It exists because fixing a thing you cannot see is guesswork, and
 * guesswork has cost us three versions.
 */
public final class Trace {

    private static final int KEEP = 500;
    private static final ArrayList<String> lines = new ArrayList<String>();

    private Trace() {
    }

    public static synchronized void note(String what) {
        String when = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        lines.add(when + "  " + (what == null ? "" : what));
        while (lines.size() > KEEP) {
            lines.remove(0);
        }
    }

    public static synchronized String write() {
        StringBuilder b = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            b.append(lines.get(i)).append('\n');
        }
        return b.toString();
    }

    public static synchronized void clear() {
        lines.clear();
    }

    private static final String BEFORE = "before.txt";

    /**
     * The ring set down whenever the window steps away. A phone that lets the
     * application go while another is in front — a camera, say — takes the
     * ring with it, and the next start would not know what came before; so
     * the next start reads it back, under a line that says where it ends.
     */
    public static synchronized void keep(Context context) {
        try {
            FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), BEFORE));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                b.append(lines.get(i)).append('\n');
            }
            out.write(b.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception unwritten) {
            // a journal that cannot be kept is only a journal of this run
        }
    }

    private static synchronized void readBefore(Context context) {
        File file = new File(context.getFilesDir(), BEFORE);
        if (!file.isFile()) {
            return;
        }
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(file), "UTF-8"));
            ArrayList<String> old = new ArrayList<String>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() > 0) {
                    old.add(line);
                }
            }
            in.close();
            if (!old.isEmpty()) {
                old.add("\u2014 the run before ends here \u2014");
                lines.addAll(0, old);
                while (lines.size() > KEEP) {
                    lines.remove(0);
                }
            }
        } catch (Exception unread) {
            // nothing to show from before
        }
        file.delete();
    }

    public static synchronized int size() {
        return lines.size();
    }

    // ------------------------------------------------------------- falls

    private static final String FALL = "fall.txt";
    private static boolean watching;

    /**
     * When the application falls, the ring above dies with it, and the one thing
     * that would say why is gone. So a fall is caught on its way down: what
     * broke, where, and the last few hundred lines before it are written to
     * a file of the application's own, and the fall then goes on to the
     * system as it would have anyway. Only the last fall is kept.
     */
    public static synchronized void watch(Context context) {
        if (watching) {
            return;
        }
        watching = true;
        readBefore(context);
        final File file = new File(context.getFilesDir(), FALL);
        final Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable fall) {
                try {
                    StringWriter trace = new StringWriter();
                    fall.printStackTrace(new PrintWriter(trace));
                    StringBuilder b = new StringBuilder();
                    b.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(new Date())).append("  thread ").append(thread.getName())
                        .append('\n').append(trace).append('\n').append(write());
                    FileOutputStream out = new FileOutputStream(file);
                    out.write(b.toString().getBytes("UTF-8"));
                    out.close();
                } catch (Throwable ignored) {
                    // a fall while writing about a fall has nowhere left to go
                }
                if (before != null) {
                    before.uncaughtException(thread, fall);
                }
            }
        });
        if (file.exists()) {
            note("the last run ended in a fall");
        }
    }

    /** The last fall as it was written, or nothing. */
    public static String fall(Context context) {
        File file = new File(context.getFilesDir(), FALL);
        if (!file.exists() || file.length() > 2 * 1024 * 1024) {
            return "";
        }
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            int at = 0;
            while (at < bytes.length) {
                int got = in.read(bytes, at, bytes.length - at);
                if (got < 0) {
                    break;
                }
                at += got;
            }
            in.close();
            return new String(bytes, 0, at, "UTF-8");
        } catch (java.io.IOException broken) {
            return "";
        }
    }

    public static void forgetFall(Context context) {
        new File(context.getFilesDir(), FALL).delete();
    }

    /** What goes out when the log is copied or saved: the last fall first, then now. */
    public static String whole(Context context) {
        String fall = fall(context);
        return fall.length() == 0 ? write() : "=== last fall ===\n" + fall
            + "\n=== this run ===\n" + write();
    }
}
