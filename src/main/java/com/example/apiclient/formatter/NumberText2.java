package com.example.apiclient.formatter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NumberText2 {
    private NumberText2() {}

    private static final Pattern SCI = Pattern.compile("\\b[+-]?(?:\\d+\\.?\\d*|\\d*\\.\\d+)[eE][+-]?\\d+\\b");

    static String deScientific(String s) {
        if (s == null || s.isBlank()) return s;
        Matcher m = SCI.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String token = m.group();
            String plain = new BigDecimal(token).toPlainString();
            m.appendReplacement(out, Matcher.quoteReplacement(plain));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String normalizeInr(String s) {
        return s == null ? null : s.replace("INR", "₹");
    }

    /** ₹ + Indian digit grouping (e.g., 14,15,871.00). */
    static String formatINR(String numeric) {
        if (numeric == null || numeric.isBlank()) return "";
        try {
            double v = Double.parseDouble(numeric);
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            String out = nf.format(v);            // e.g., ₹14,15,871.00
            return out;
        } catch (Exception e) {
            return "₹" + numeric; // fallback
        }
    }

    static String nn(String s) {              // null-safe trim
        return (s == null || s.equalsIgnoreCase("null")) ? "" : s.trim();
    }
}
