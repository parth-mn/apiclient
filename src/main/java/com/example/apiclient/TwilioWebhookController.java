package com.example.apiclient;

import com.example.apiclient.CommandRouter;
import com.example.apiclient.ai.NaturalLanguageRouter;
import com.example.apiclient.formatter.WhatsappFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.regex.*;
import java.util.*;

@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {

    private final CommandRouter router;
    private final NaturalLanguageRouter nl;
    private final WhatsappFormatter formatter;

    @Autowired
    public TwilioWebhookController(CommandRouter router, NaturalLanguageRouter nl, WhatsappFormatter formatter) {
        this.router = router;
        this.nl = nl;
        this.formatter = formatter;
    }

    @PostMapping(
            value = "/wh",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public String handleWebhook(@RequestParam Map<String, String> formData) {
        System.out.println("=== Twilio Webhook triggered ===");
        System.out.println("Form data: " + formData);

        String userMsg = formData.getOrDefault("Body", "").trim();
        System.out.println("Body received: " + userMsg);

        // 1) Natural language → command
        String cmd = nl.toCommand(userMsg);
        System.out.println("[Twilio] Mapped cmd: " + cmd);

        // 2) Execute command
        String reply = router.handle(cmd).blockOptional().orElse("Something went wrong.");

        // 3) Format nicely for WhatsApp (may include lines like: 'Image: https://.../medias/...').
        String formatted = formatter.formatReply(cmd, reply);

        // 4) Extract any media URLs and remove them from the body text
        Extracted ex = extractMedia(formatted);

        // 5) Build TwiML with both body + <Media> tags
        StringBuilder twiml = new StringBuilder();
        twiml.append("<Response><Message>");
        twiml.append("<Body>").append(escapeXml(ex.cleanedBody())).append("</Body>");
        for (String url : ex.mediaUrls()) {
            twiml.append("<Media>").append(escapeXml(url)).append("</Media>");
        }
        twiml.append("</Message></Response>");

        System.out.println("[Twilio] TwiML reply:\n" + twiml);
        return twiml.toString();
    }

    // ---- helpers ----

    // Find absolute URLs that include '/medias/' and strip them (and any 'Image:' labels) from the body.
    private static Extracted extractMedia(String text) {
        if (text == null || text.isBlank()) return new Extracted("", List.of());

        Pattern p = Pattern.compile("(https?://\\S+/medias/\\S+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);

        List<String> urls = new ArrayList<>();
        while (m.find()) {
            urls.add(m.group(1));
        }

        // Remove URLs and optional leading 'Image:' labels; collapse blank lines
        String cleaned = p.matcher(text).replaceAll("");
        cleaned = cleaned.replaceAll("(?i)\\bImage:\\s*", "");
        cleaned = cleaned.replaceAll("[ \\t]*\\n[ \\t]*\\n+", "\n").trim();

        return new Extracted(cleaned, urls);
    }

    // Minimal XML escape for TwiML content
    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // Tiny carrier for body + media list
    private record Extracted(String cleanedBody, List<String> mediaUrls) {}
}
