package com.example.apiclient.ai;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class NaturalLanguageRouter {

    private final IntentDetector detector;

    public NaturalLanguageRouter(IntentDetector detector) {
        this.detector = detector;
    }

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "interior", "exterior", "luxury", "wood-finishes", "enamel",
            "water-proofing", "premium", "others", "emulsion", "wallpaper",
            "colourant", "strainer", "tiles", "tools"
    );

    private static boolean isValidCategory(String cat) {
        return cat != null && ALLOWED_CATEGORIES.contains(cat);
    }

    private static String safeInt(Integer value, int def) {
        return String.valueOf((value == null || value <= 0) ? def : value);
    }

    private static String safeQty(Integer value) {
        return String.valueOf((value == null || value <= 0) ? 1 : value);
    }

    /**
     * Convert a natural-language message into a console-style command string
     * understood by your CommandRouter (e.g., "products interior 1 12").
     */
    public String toCommand(String userText) {
        // If it already looks like a command, pass through
        String lower = userText == null ? "" : userText.trim().toLowerCase();
        //if (lower.matches("^(help|categories|products|product|add|setqty|cart|order|pending|header)\\b.*")) {return userText.trim();}
        if (lower.matches("^(help|categories|products|product|add|setqty|cart|order|pending|header|search|info)\\b.*"))
            return userText.trim();


        // Otherwise detect with AI and synthesize a command
        NluResult n = detector.detect(userText);

        String cmd;
        Intent intent = n.intent();
        String cat = n.category();
        String code = n.code();
        Integer page = n.page();
        Integer size = n.size();
        Integer qty = n.qty();
        Integer entry = n.entry();

        // 🧠 Validate category
        if (cat != null && !isValidCategory(cat)) cat = null;

        switch (intent) {
            case VIEW_CATEGORIES -> cmd = "categories";
            case VIEW_PRODUCTS -> {
                if (cat == null) {
                    cmd = "help";
                    break;
                }
                cmd = "products " + cat + " " + safeInt(page, 1) + " " + safeInt(size, 12);
            }
            case SHOW_PRODUCT -> cmd = "product " + (code != null ? code : "");
            case ADD_TO_CART -> {
                if (code == null) {
                    cmd = "help";
                    break;
                }
                cmd = "add " + code + " " + safeQty(qty);
            }
            case ADJUST_QTY -> cmd = "setqty " + safeInt(entry, 0) + " " + safeQty(qty);
            case SHOW_CART -> cmd = "cart";
            case PLACE_ORDER -> cmd = "order";
            case SHOW_PENDING -> cmd = "pending " + safeInt(page, 1) + " " + safeInt(size, 12);
            case SHOW_HEADER -> cmd = "header";
            default -> cmd = "help";
        }

        System.out.println("[AI] Routed cmd=" + cmd);
        return cmd;
    }
}
