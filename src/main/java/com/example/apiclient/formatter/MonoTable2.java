package com.example.apiclient.formatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Minimal fixed-width table for WhatsApp code blocks. */
final class MonoTable2 {
    private final List<String[]> rows = new ArrayList<>();
    private String[] header;

    MonoTable2 header(String... cols) { this.header = cols; return this; }
    MonoTable2 row(String... cols) { rows.add(cols); return this; }

    String render() {
        // compute column widths
        int cols = header != null ? header.length : rows.stream().mapToInt(r -> r.length).max().orElse(0);
        int[] w = new int[cols];
        if (header != null) grow(w, header);
        for (String[] r : rows) grow(w, r);

        StringBuilder sb = new StringBuilder();
        sb.append("```\n"); // WhatsApp monospace block
        if (header != null) {
            sb.append(padRow(header, w)).append('\n');
            char[] line = new char[Math.max(3, Arrays.stream(w).sum() + (cols - 1) * 2)];
            Arrays.fill(line, '-');
            sb.append(line).append('\n');
        }
        for (String[] r : rows) sb.append(padRow(r, w)).append('\n');
        sb.append("```");
        return sb.toString();
    }

    private static void grow(int[] w, String[] r) {
        for (int i = 0; i < w.length && i < r.length; i++) {
            String v = r[i] == null ? "" : r[i];
            if (v.length() > w[i]) w[i] = v.length();
        }
    }

    private static String padRow(String[] r, int[] w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            String v = i < r.length ? (r[i] == null ? "" : r[i]) : "";
            sb.append(padRight(v, w[i]));
            if (i < w.length - 1) sb.append("  "); // 2 spaces between columns
        }
        return sb.toString();
    }

    private static String padRight(String s, int len) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }
}
