package com.example.apiclient;

import com.example.apiclient.CommandRouter;
import com.example.apiclient.ai.NaturalLanguageRouter;
import com.example.apiclient.formatter.WhatsappFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import com.example.apiclient.audio.AudioTranscriptionService;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.regex.*;
import java.util.*;

@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {

    private final CommandRouter router;
    private final NaturalLanguageRouter nl;
    private final WhatsappFormatter formatter;
    // import com.example.apiclient.audio.AudioTranscriptionService;

    private final AudioTranscriptionService audioTranscriptionService;
    @Autowired
    public TwilioWebhookController(
            CommandRouter router,
            NaturalLanguageRouter nl,
            WhatsappFormatter formatter,
            AudioTranscriptionService audioTranscriptionService
    ) {
        this.router = router;
        this.nl = nl;
        this.formatter = formatter;
        this.audioTranscriptionService = audioTranscriptionService;
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

        String numMedia = formData.getOrDefault("NumMedia", "0");
        String mediaUrl0 = formData.get("MediaUrl0");
        String mediaContentType0 = formData.get("MediaContentType0");
        String accountSidFromWebhook = formData.get("AccountSid");

        try {
            int mediaCount = Integer.parseInt(numMedia);
            boolean hasAudio =
                    mediaCount > 0 &&
                            mediaContentType0 != null &&
                            mediaContentType0.toLowerCase().startsWith("audio/");

            if (hasAudio && mediaUrl0 != null && !mediaUrl0.isBlank()) {
                String transcript = audioTranscriptionService
                        .transcribeAudio(mediaUrl0, mediaContentType0, accountSidFromWebhook)
                        .block();  // or block(Duration.ofSeconds(30));

                if (transcript != null && !transcript.isBlank()) {
                    // Try to parse JSON if present (language/originalText/englishText)
                    String userMsgCandidate = transcript.trim();
                    try {
                        if (transcript.trim().startsWith("{")) {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode root = mapper.readTree(transcript.trim());
                            String englishText = root.path("englishText").asText("");
                            String originalLang = root.path("language").asText("");
                            String originalText = root.path("originalText").asText("");

                            if (englishText != null && !englishText.isBlank()) {
                                System.out.println("[Twilio] Detected language: " + originalLang);
                                System.out.println("[Twilio] Original: " + originalText);
                                System.out.println("[Twilio] English: " + englishText);
                                userMsgCandidate = englishText.trim();

                                // (Optional) echo back what was heard in original language
                                // reply later: "You said (Tamil): <originalText>"
                            }
                        }
                    } catch (Exception parseEx) {
                        System.out.println("[Twilio] Transcription not JSON, using raw text.");
                    }

                    userMsg = userMsgCandidate;
                    System.out.println("[Twilio] Using transcribed message: " + userMsg);
                } else {
                    System.out.println("[Twilio] Transcription empty, falling back to text Body.");
                }


                /*if (transcript != null && !transcript.isBlank()) {
                    System.out.println("[Twilio] Using transcribed audio as user message: " + transcript);
                    userMsg = transcript.trim();   // 👈 override Body with transcript
                } else {
                    System.out.println("[Twilio] Transcription empty, falling back to text Body.");
                }*/
            }
        } catch (Exception e) {
            System.err.println("[Twilio] Error during audio transcription: " + e.getMessage());
            e.printStackTrace();
        }
    
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
