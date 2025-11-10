package com.example.apiclient.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IntentDetector {

    private final WebClient http;
    private final String apiKey;
    private final ObjectMapper om = new ObjectMapper();

    public IntentDetector(@Value("${gemini.api-key:${GOOGLE_API_KEY:}}") String apiKey) {
        this.apiKey = "AIzaSyBNgXf5p4tRtjOvC71Q2xcs98tIqmGspG4";
        String mask = apiKey.substring(0, Math.min(6, apiKey.length())) + "...";
        System.out.println("[Gemini] Using key: " + mask);
        this.http = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /*private static final String PROMPT = """
            You are an intent + entity labeler for a B2B commerce WhatsApp bot.
            Return ONE JSON object ONLY (no prose, no markdown). Keys must be exactly:\s
            [intent, category, code, page, size, qty, entry]
            
            Rules:
            - If the user sends a voice note, convert it into text and follow the rules.
            - If unsure or ambiguous, set intent="HELP" and leave other fields null.
            - NEVER invent or normalize to a category that isn’t in the allowed list.
            - Extract only what is clearly present. If a number is written as a word ("two"), map to a digit when trivial.
            - page is 1-based if present. size is the requested page size if present.
            - Do NOT include any key besides the six listed.
            
            Allowed intents (choose one):
            VIEW_CATEGORIES, VIEW_PRODUCTS, SHOW_PRODUCT, ADD_TO_CART, ADJUST_QTY, SHOW_CART, PLACE_ORDER, SHOW_PENDING, SHOW_HEADER, HELP
            
            Allowed categories (must match EXACTLY if used):
            interior, exterior, luxury, wood-finishes, enamel, water-proofing, premium, others, emulsion, wallpaper, colourant, strainer, tiles, tools
            
            Category normalization hints (map user words → allowed code):
            - "wood finishes", "wood-finish", "wood finish" → wood-finishes
            - "waterproofing", "water proofing" → water-proofing
            - "colorant", "colorants", "colourants" → colourant
            
            Mapping guidance:
            - Category/index/browse queries → intent=VIEW_PRODUCTS with category if specified.
            - “categories”, “show categories” → intent=VIEW_CATEGORIES.
            - “product 51707”, “open 51707” → intent=SHOW_PRODUCT with code="51707".
            - “add 517179999320 2” → intent=ADD_TO_CART code="517179999320", qty=2.
            - “set first item to 3”, “change entry 1 to 5” → intent=ADJUST_QTY entry=<0-based>, qty=<int>.
            - “cart”, “what’s in my cart” → intent=SHOW_CART.
            - “place order” → intent=PLACE_ORDER.
            - “pending orders”, “approval awaited” → intent=SHOW_PENDING.
            - “outstanding”, “available credit”, “account summary” → intent=SHOW_HEADER.
            
            Output format (STRICT; example structure):
            {"intent":"VIEW_PRODUCTS","category":"interior","code":null,"page":1,"size":12,"qty":null,"entry":null}
            
            User: %s
            
        """;*/
    private static final String PROMPT = """
    You are an intent + entity labeler for a B2B commerce WhatsApp bot.
    Return ONE JSON object ONLY (no prose, no markdown).
    Keys must be exactly:
    [intent, category, code, page, size, qty, entry]

    Rules:
    - If unsure or ambiguous, set intent="HELP" and leave other fields null.
    - NEVER invent or normalize to a category that isn’t in the allowed list.
    - Extract only what is clearly present. If a number is written as a word ("two"), map to a digit when trivial.
    - page is 1-based if present. size is the requested page size if present.
    - Do NOT include any key besides the six listed.

    Allowed intents (choose one):
    VIEW_CATEGORIES, VIEW_PRODUCTS, SHOW_PRODUCT, ADD_TO_CART, ADJUST_QTY,
    SHOW_CART, PLACE_ORDER, SHOW_PENDING, SHOW_HEADER, SEARCH_PRODUCTS, INFO, HELP

    Allowed categories (must match EXACTLY if used):
    interior, exterior, luxury, wood-finishes, enamel, water-proofing, premium,
    others, emulsion, wallpaper, colourant, strainer, tiles, tools

    Category normalization hints (map user words → allowed code):
    - "wood finishes", "wood-finish", "wood finish" → wood-finishes
    - "waterproofing", "water proofing" → water-proofing
    - "colorant", "colorants", "colourants" → colourant

    Mapping guidance:
    - Category/index/browse queries → intent=VIEW_PRODUCTS with category if specified.
    - “categories”, “show categories” → intent=VIEW_CATEGORIES.
    - “product 51707”, “open 51707” → intent=SHOW_PRODUCT with code="51707".
    - “add 517179999320 2” → intent=ADD_TO_CART code="517179999320", qty=2.
    - “set first item to 3”, “change entry 1 to 5” → intent=ADJUST_QTY entry=<0-based>, qty=<int>.
    - “cart”, “what’s in my cart” → intent=SHOW_CART.
    - “place order” → intent=PLACE_ORDER.
    - “pending orders”, “approval awaited” → intent=SHOW_PENDING.
    - “outstanding”, “available credit”, “account summary” → intent=SHOW_HEADER.
    - Generic product name or keyword searches → intent=SEARCH_PRODUCTS
        Example: “search thinner”, “show me ceiling paint”, “find super bright”
        → intent=SEARCH_PRODUCTS, category=null
    - SKU / short code lookups → intent=INFO
        Example: “check silver 8001”, “sku 51707”, “show info for 8001”
        → intent=INFO, category=null
        SEARCH_PRODUCTS — free-text search like “search thinner”, “find super bright”, “show ceiling paint”.
                    Output:
            
                    {"intent":"SEARCH_PRODUCTS","category":null,"code":null,"page":<user page or 1>,"size":<user size or 12>,"qty":null,"entry":null}
            
            
                    Notes:
            
                    If user says “page 2”, set page=2; otherwise page=1.
            
                    Do NOT put the search text into category or code.
            
                    INFO — SKU or short-code lookup like “check silver 8001”, “sku 8001”, “info silver”.
                    Put the entire lookup text (one or two tokens) into code.
                    Output:
            
                    {"intent":"INFO","category":null,"code":"silver 8001","page":null,"size":null,"qty":null,"entry":null}

    Output format (STRICT; example structure):
    {"intent":"VIEW_PRODUCTS","category":"interior","code":null,"page":1,"size":12,"qty":null,"entry":null}

    User: %s
""";


    public NluResult detect(String user) {
        try {
            String uri = "/v1/models/gemini-2.5-flash:generateContent?key=" +
                    URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

// build request with response_mime_type = application/json
            String jsonBody = """
{
  "contents": [{
    "role": "user",
    "parts": [{ "text": %s }]
  }]
}
""".formatted(toJsonString(PROMPT.formatted(user)));



            String resp = http.post().uri(uri)
                    .bodyValue(jsonBody)
                    .exchangeToMono(r -> r.bodyToMono(String.class)
                            .map(body -> {
                                if (!r.statusCode().is2xxSuccessful()) {
                                    System.err.println("[Gemini] HTTP " + r.statusCode().value() + " body=" + body);
                                }
                                return body;
                            })
                    )
                    .timeout(Duration.ofSeconds(20))
                    .block(Duration.ofSeconds(25));

// --- extract model text robustly ---
            JsonNode root = om.readTree(resp);
            String modelText = null;
            JsonNode cand0 = root.path("candidates").path(0);

// common path
            JsonNode partsArr = cand0.path("content").path("parts");
            if (partsArr.isArray() && partsArr.size() > 0) {
                modelText = partsArr.get(0).path("text").asText(null);
            }
// fallback: some responses serialize content as an array
            if ((modelText == null || modelText.isBlank()) && cand0.path("content").isArray()) {
                JsonNode content0 = cand0.path("content").get(0);
                JsonNode parts2 = content0.path("parts");
                if (parts2.isArray() && parts2.size() > 0) {
                    modelText = parts2.get(0).path("text").asText(null);
                }
            }

            if (modelText == null) modelText = "";

// TEMP debug
            System.out.println("[Gemini->text] " + modelText);

// strip code fences if present
            String json = modelText.trim();
            if (json.startsWith("```")) {
                int first = json.indexOf('\n');
                int last = json.lastIndexOf("```");
                if (first >= 0 && last > first) json = json.substring(first + 1, last).trim();
            }

            // --- Regex parse the JSON the model returned ---
            json = modelText;
            Intent intent = Intent.UNKNOWN;
            String category = pick(json, "\"category\"\\s*:\\s*\"([^\"]+)\"");
            String code     = pick(json, "\"code\"\\s*:\\s*\"?(\\d+)\"?");
            Integer page    = parseInt(pick(json, "\"page\"\\s*:\\s*(\\d+)"));
            Integer size    = parseInt(pick(json, "\"size\"\\s*:\\s*(\\d+)"));
            Integer qty     = parseInt(pick(json, "\"qty\"\\s*:\\s*(\\d+)"));
            Integer entry   = parseInt(pick(json, "\"entry\"\\s*:\\s*(\\d+)"));

            String in = pick(json, "\"intent\"\\s*:\\s*\"([A-Z_]+)\"");
            if (in != null) try { intent = Intent.valueOf(in); } catch (Exception ignored) {}

            return new NluResult(intent, category, code, page, size, qty, entry);

        } catch (Exception e) {
            e.printStackTrace();
            return new NluResult(Intent.UNKNOWN, null, null, null, null, null, null);
        }
    }

    // Helpers
    private static String pick(String s, String rx) {
        if (s == null) return null;
        Matcher m = Pattern.compile(rx).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static Integer parseInt(String s) {
        try { return s == null ? null : Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    // escape a Java string into valid JSON string
    private static String toJsonString(String s) {
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "\"" + esc + "\"";
    }
}
