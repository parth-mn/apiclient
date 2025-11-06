package com.example.apiclient;

import java.util.List;

public final class TwimlBuilder {
    private TwimlBuilder() {}

    /** Build a TwiML <Response> with 1..N <Message> nodes. */
    public static String messages(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("<Response>");
        if (parts == null || parts.isEmpty()) {
            sb.append("<Message>OK</Message>");
        } else {
            for (String p : parts) {
                sb.append("<Message>").append(escapeXml(p)).append("</Message>");
            }
        }
        sb.append("</Response>");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
