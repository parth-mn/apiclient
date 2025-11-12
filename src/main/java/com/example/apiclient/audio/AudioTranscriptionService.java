package com.example.apiclient.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Arrays;

@Service
public class AudioTranscriptionService {

    private final WebClient webClient;
    private final String googleApiKey;
    private final String twilioAccountSid;
    private final String twilioAuthToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AudioTranscriptionService(
            @Value("${gemini.api-key:${GOOGLE_API_KEY:}}") String googleApiKey,
            @Value("${twilio.accountSid}") String twilioAccountSid,
            @Value("${twilio.authToken}") String twilioAuthToken) {
        // Use the same hardcoded key as IntentDetector if not provided
        if (googleApiKey == null || googleApiKey.isBlank()) {
            this.googleApiKey = "AIzaSyC9qWKdHhDI5UxS3gMHHBcKbWuwVkk50XE";
            System.out.println("[AudioTranscription] Using hardcoded API key");
        } else {
            this.googleApiKey = googleApiKey;
            String mask = googleApiKey.substring(0, Math.min(6, googleApiKey.length())) + "...";
            System.out.println("[AudioTranscription] Using API key: " + mask);
        }
        this.twilioAccountSid = twilioAccountSid;
        this.twilioAuthToken = twilioAuthToken;
        
        // Configure WebClient to follow redirects (Twilio MediaUrls return 307 redirects)
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)  // Enable redirect following
                .responseTimeout(Duration.ofSeconds(30));
        
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB for audio files
                .build();
    }

    /**
     * Downloads audio from Twilio MediaUrl and transcribes it to text using Gemini API
     * @param mediaUrl The Twilio MediaUrl (e.g., from MediaUrl0 parameter)
     * @param mediaContentType Optional content type (e.g., from MediaContentType0 parameter)
     * @param accountSidFromWebhook Optional AccountSid from webhook (if different from config)
     * @return Transcribed text from the audio
     */
    public Mono<String> transcribeAudio(String mediaUrl, String mediaContentType, String accountSidFromWebhook) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return Mono.just("");
        }

        System.out.println("[AudioTranscription] Downloading audio from: " + mediaUrl);
        if (mediaContentType != null && !mediaContentType.isBlank()) {
            System.out.println("[AudioTranscription] Content type: " + mediaContentType);
        }

        // Use AccountSid from webhook if provided, otherwise use from config
        // Also try to extract from MediaUrl if not provided
        String accountSidToUse = accountSidFromWebhook;
        if (accountSidToUse == null || accountSidToUse.isBlank()) {
            // Try to extract from MediaUrl: https://api.twilio.com/2010-04-01/Accounts/{AccountSid}/...
            try {
                int accountsIndex = mediaUrl.indexOf("/Accounts/");
                if (accountsIndex >= 0) {
                    int start = accountsIndex + "/Accounts/".length();
                    int end = mediaUrl.indexOf("/", start);
                    if (end > start) {
                        accountSidToUse = mediaUrl.substring(start, end);
                        System.out.println("[AudioTranscription] Extracted AccountSid from MediaUrl: " + accountSidToUse);
                    }
                }
            } catch (Exception e) {
                System.err.println("[AudioTranscription] Could not extract AccountSid from MediaUrl: " + e.getMessage());
            }
        }
        
        // Fallback to config AccountSid
        if (accountSidToUse == null || accountSidToUse.isBlank()) {
            accountSidToUse = twilioAccountSid;
            System.out.println("[AudioTranscription] Using AccountSid from config: " + accountSidToUse);
        } else {
            System.out.println("[AudioTranscription] Using AccountSid from webhook: " + accountSidToUse);
        }

        // Make final copy for use in lambda expressions
        final String finalAccountSid = accountSidToUse;
        final String finalAuthToken = twilioAuthToken;

        // Download audio from Twilio (requires authentication)
        // Note: Twilio MediaUrls return 307 redirects, WebClient is configured to follow them automatically
        return webClient.get()
                .uri(mediaUrl)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + 
                    Base64.getEncoder().encodeToString((finalAccountSid + ":" + finalAuthToken).getBytes(StandardCharsets.UTF_8)))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(30))
                .flatMap(audioBytes -> {
                    System.out.println("[AudioTranscription] Downloaded " + audioBytes.length + " bytes");
                    if (audioBytes.length == 0) {
                        System.err.println("[AudioTranscription] ERROR: Downloaded audio is empty!");
                        return Mono.just("");
                    }
                    if (audioBytes.length < 100) {
                        System.err.println("[AudioTranscription] WARNING: Audio file is very small (" + audioBytes.length + " bytes). This might be an error page.");
                    }
                    // Check if it's valid audio (OGG files typically start with "OggS" = [79, 103, 103, 83])
                    if (audioBytes.length >= 4) {
                        byte[] header = Arrays.copyOf(audioBytes, Math.min(4, audioBytes.length));
                        System.out.println("[AudioTranscription] Audio file header (first 4 bytes): " + Arrays.toString(header));
                        
                        // Check if it's XML/HTML (starts with <?xm or <htm)
                        if (header[0] == 60 && (header[1] == 63 || header[1] == 104)) { // '<' followed by '?' or 'h'
                            String errorText = new String(audioBytes, 0, Math.min(500, audioBytes.length), StandardCharsets.UTF_8);
                            System.err.println("[AudioTranscription] ERROR: Downloaded file is XML/HTML, not audio! First 500 chars: " + errorText);
                            return Mono.just("");
                        }
                        
                        // Check if it's OGG (starts with "OggS")
                        if (header[0] == 79 && header[1] == 103 && header[2] == 103 && header[3] == 83) {
                            System.out.println("[AudioTranscription] Valid OGG audio file detected");
                        } else {
                            System.out.println("[AudioTranscription] WARNING: Audio file doesn't start with OGG header. Might still be valid audio.");
                        }
                    }
                    return transcribeWithGemini(audioBytes, mediaContentType);
                })
                .onErrorResume(e -> {
                    System.err.println("[AudioTranscription] Error: " + e.getMessage());
                    e.printStackTrace();
                    return Mono.just(""); // Return empty string on error
                });
    }

    /**
     * Downloads audio from Twilio MediaUrl and transcribes it to text (overload without content type and accountSid)
     */
    public Mono<String> transcribeAudio(String mediaUrl) {
        return transcribeAudio(mediaUrl, null, null);
    }
    
    /**
     * Downloads audio from Twilio MediaUrl and transcribes it to text (overload without accountSid)
     */
    public Mono<String> transcribeAudio(String mediaUrl, String mediaContentType) {
        return transcribeAudio(mediaUrl, mediaContentType, null);
    }

    /**
     * Use Gemini API to transcribe audio
     */
    private Mono<String> transcribeWithGemini(byte[] audioBytes, String mediaContentType) {
        try {
            // Encode audio to base64
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            
            String uri = "/v1/models/gemini-2.5-flash:generateContent?key=" +
                    URLEncoder.encode(googleApiKey, StandardCharsets.UTF_8);

            // Determine MIME type - use provided content type or default to OGG (most common for WhatsApp)
            String mimeType = determineMimeType(mediaContentType);
            System.out.println("[AudioTranscription] Using MIME type: " + mimeType);

            return transcribeWithMimeType(audioBase64, uri, mimeType)
                    .flatMap(result -> {
                        if (!result.isBlank()) {
                            return Mono.just(result);
                        }
                        // If failed and we used a default, try other common types
                        if (mediaContentType == null || mediaContentType.isBlank()) {
                            String[] fallbackTypes = {"audio/m4a", "audio/mpeg", "audio/wav"};
                            return tryMimeTypes(audioBase64, uri, fallbackTypes);
                        }
                        return Mono.just("");
                    });

        } catch (Exception e) {
            System.err.println("[AudioTranscription] Error in transcribeWithGemini: " + e.getMessage());
            e.printStackTrace();
            return Mono.just("");
        }
    }

    /**
     * Determines the MIME type from Twilio's MediaContentType or defaults to audio/ogg
     */
    private String determineMimeType(String mediaContentType) {
        if (mediaContentType != null && !mediaContentType.isBlank()) {
            // Twilio may send something like "audio/ogg; codecs=opus" - extract just the MIME type
            String mime = mediaContentType.split(";")[0].trim();
            if (mime.startsWith("audio/")) {
                return mime;
            }
        }
        // Default to OGG (most common for WhatsApp voice notes)
        return "audio/ogg";
    }

    /**
     * Tries multiple MIME types sequentially until one succeeds
     */
    private Mono<String> tryMimeTypes(String audioBase64, String uri, String[] mimeTypes) {
        Mono<String> result = Mono.just("");
        for (String mimeType : mimeTypes) {
            result = result.flatMap(prev -> {
                if (!prev.isBlank()) {
                    return Mono.just(prev);
                }
                return transcribeWithMimeType(audioBase64, uri, mimeType);
            });
        }
        return result;
    }

    /**
     * Helper method to transcribe with a specific MIME type
     */
    private Mono<String> transcribeWithMimeType(String audioBase64, String uri, String mimeType) {
        try {
            // Build request with proper structure for Gemini audio transcription
            // Support both English and Hindi transcription
            /*String requestBody = String.format("""
                {
                  "contents": [{
                    "role": "user",
                    "parts": [
                      {
                        "text": "Listen to this audio message and transcribe what the person said. The audio may be in English, Hindi, or a mix of both. Transcribe it accurately preserving the exact language used. Return only the transcribed text, nothing else."
                      },
                      {
                        "inline_data": {
                          "mime_type": "%s",
                          "data": "%s"
                        }
                      }
                    ]
                  }]
                }
                """, mimeType, audioBase64);*/
            // ✅ Build the JSON body safely with Jackson
            ObjectMapper mapper = new ObjectMapper();

// Root
            ObjectNode root = mapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");

// User message
            ObjectNode user = contents.addObject();
            user.put("role", "user");
            ArrayNode parts = user.putArray("parts");

// Prompt text (multilingual + translation)
            String prompt =
                    "You will receive an audio message that may be in any language (for example English, Hindi, Tamil, etc.). "
                            + "1. Detect the spoken language. "
                            + "2. Transcribe what the speaker said in the original language. "
                            + "3. Translate that transcription into clear English. "
                            + "Return only a single JSON object in this exact format: "
                            + "{\\\"language\\\":\\\"<detected language name or code>\\\","
                            + "\\\"originalText\\\":\\\"<transcription in original language>\\\","
                            + "\\\"englishText\\\":\\\"<translated English text>\\\"}";

            parts.addObject().put("text", prompt);

// Inline audio data
            ObjectNode inline = parts.addObject().putObject("inline_data");
            inline.put("mime_type", mimeType);
            inline.put("data", audioBase64);

// Serialize safely
            String requestBody = mapper.writeValueAsString(root);


            System.out.println("[AudioTranscription] Attempting transcription with MIME type: " + mimeType);

            return webClient.post()
                    .uri("https://generativelanguage.googleapis.com" + uri)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .exchangeToMono(response -> {
                        System.out.println("[AudioTranscription] HTTP Status: " + response.statusCode());
                        if (!response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        System.err.println("[AudioTranscription] HTTP Error Response: " + body);
                                        return Mono.error(new RuntimeException("HTTP " + response.statusCode() + ": " + body));
                                    });
                        }
                        return response.bodyToMono(String.class);
                    })
                    .timeout(Duration.ofSeconds(30))
                    .map(response -> {
                        System.out.println("[AudioTranscription] Gemini response length: " + response.length());
                        System.out.println("[AudioTranscription] Gemini response (first 500 chars): " + 
                            (response.length() > 500 ? response.substring(0, 500) + "..." : response));
                        String transcribed = extractGeminiTranscription(response);
                        System.out.println("[AudioTranscription] Extracted transcription: '" + transcribed + "'");
                        return transcribed;
                    })
                    .onErrorResume(e -> {
                        System.err.println("[AudioTranscription] Error with MIME type " + mimeType + ": " + e.getMessage());
                        e.printStackTrace();
                        return Mono.just("");
                    });

        } catch (Exception e) {
            System.err.println("[AudioTranscription] Error in transcribeWithMimeType: " + e.getMessage());
            return Mono.just("");
        }
    }

    /**
     * Extracts transcription text from Google Speech-to-Text API response
     */
    private String extractTranscription(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode results = root.path("results");
            if (results.isArray() && results.size() > 0) {
                JsonNode firstResult = results.get(0);
                JsonNode alternatives = firstResult.path("alternatives");
                if (alternatives.isArray() && alternatives.size() > 0) {
                    String transcript = alternatives.get(0).path("transcript").asText("");
                    System.out.println("[AudioTranscription] Transcribed: " + transcript);
                    return transcript.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("[AudioTranscription] Error parsing transcription: " + e.getMessage());
        }
        return "";
    }

    /**
     * Extracts transcription text from Gemini API response
     */
    private String extractGeminiTranscription(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            System.out.println("[AudioTranscription] Full JSON structure: " + root.toPrettyString());
            
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                System.out.println("[AudioTranscription] Candidate: " + candidate.toPrettyString());
                
                // Check for finishReason that might indicate an issue
                String finishReason = candidate.path("finishReason").asText("");
                if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
                    System.err.println("[AudioTranscription] Finish reason: " + finishReason);
                }
                
                JsonNode content = candidate.path("content");
                System.out.println("[AudioTranscription] Content: " + content.toPrettyString());
                
                // Try different paths for parts
                JsonNode parts = content.path("parts");
                if (!parts.isArray() || parts.size() == 0) {
                    // Try alternative: content might be directly an array
                    if (content.isArray() && content.size() > 0) {
                        parts = content.get(0).path("parts");
                    }
                }
                
                if (parts.isArray() && parts.size() > 0) {
                    for (int i = 0; i < parts.size(); i++) {
                        JsonNode part = parts.get(i);
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            System.out.println("[AudioTranscription] Gemini transcribed: " + text);
                            return text.trim();
                        }
                    }
                } else {
                    System.err.println("[AudioTranscription] No parts found in content. Content structure: " + content.toPrettyString());
                }
            } else {
                System.err.println("[AudioTranscription] No candidates found in response");
            }
        } catch (Exception e) {
            System.err.println("[AudioTranscription] Error parsing Gemini transcription: " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }
}

