
package com.example.apiclient.formatter;

import java.util.List;

public record WhatsappMessage(String body, List<String> mediaUrls) {
    public static WhatsappMessage text(String body) {
        return new WhatsappMessage(body, List.of());
    }
    public static WhatsappMessage withMedia(String body, String... media) {
        return new WhatsappMessage(body, List.of(media));
    }
}
