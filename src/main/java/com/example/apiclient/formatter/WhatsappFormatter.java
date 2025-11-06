package com.example.apiclient.formatter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.apiclient.dto.ProductDetailResponse; // whatever your DTO is
//import static com.example.apiclient.CommerceApi.toFullMediaUrl;

@Component
public final class WhatsappFormatter {

    private static final int WHATSAPP_SAFE_LIMIT = 1500;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private WhatsappFormatter() {}

    // ==========================================================
    // Public entry points
    // ==========================================================

    public static String format(String original, String rawCommand) {
        if (original == null || original.isBlank()) return "⚠️ No data.";
        String s = original.trim().replace("INR", "₹");

        String lower = rawCommand == null ? "" : rawCommand.toLowerCase();

        if (lower.startsWith("categories")) return fmtCategories(s);
        if (lower.startsWith("products")) return fmtProducts(s, rawCommand);
        if (lower.startsWith("cart")) return fmtCart(s);
        if (lower.startsWith("pending")) return fmtPending(s);
        if (lower.startsWith("header")) return fmtHeader(s);
        if (lower.startsWith("add")) return "✅ " + s;
        if (lower.startsWith("order")) return "✅ *Order Placed*\n" + s;

        // default
        return s.replaceAll("(?m)^- ", "• ");
    }

    public static List<String> chunk(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) return parts;
        String[] lines = text.split("\\r?\\n");
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            if (cur.length() + line.length() + 1 > WHATSAPP_SAFE_LIMIT) {
                parts.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append('\n');
            cur.append(line);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

    // ==========================================================
    // Formatters
    // ==========================================================
    public String formatReply(String cmd, String content) {
        if (content == null || content.isBlank()) return "⚠️ No data found.";
        // optional: add emojis depending on the command
        if (cmd.startsWith("products")) return "🛍️ " + content;
        if (cmd.equals("cart")) return "🛒 " + content;
        if (cmd.equals("order")) return "✅ Order placed.\n" + content;
        return content;
    }
    private static String fmtCategories(String s) {
        StringBuilder out = new StringBuilder("🗂️ *Categories*\n");
        Pattern p = Pattern.compile("(?m)^[-•]?\\s*(\\S.*?)\\s*\\|\\s*(\\S.*?)$");
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.append("• ").append(m.group(1).trim())
                    .append(" — ").append(m.group(2).trim()).append("\n");
        }
        if (out.toString().equals("🗂️ *Categories*\n"))
            out.append(s.replaceAll("(?m)^- ", "• "));
        return out.toString().trim();
    }

    private static String fmtProducts(String s, String rawCmd) {
        // 1) normalize
        String body = s.trim();

        // remove any “Next: …” hint from the end to avoid echoing it
        body = body.replaceAll("(?im)^next:\\s*products.*$", "").trim();

        // 2) detect totals & pages from "Results: 22 (page 1/2)"
        int total = 0, page = extractPageFromCmd(rawCmd), totalPages = 1;
        Matcher mRes = Pattern.compile("(?im)^results:\\s*(\\d+)\\s*\\(page\\s*(\\d+)\\/(\\d+)\\)").matcher(body);
        if (mRes.find()) {
            total = parseIntSafe(mRes.group(1), 0);
            page = parseIntSafe(mRes.group(2), page);
            totalPages = parseIntSafe(mRes.group(3), 1);
            // drop this header line from body
            body = body.replaceFirst("(?im)^results:\\s*\\d+\\s*\\(page\\s*\\d+\\/\\d+\\)\\s*", "").trim();
        } else {
            // fallback: "Products (N):"
            Matcher mHead = Pattern.compile("(?im)^products\\s*\\((\\d+)\\)\\s*:").matcher(body);
            if (mHead.find()) {
                total = parseIntSafe(mHead.group(1), 0);
                body = body.replaceFirst("(?im)^products\\s*\\(\\d+\\)\\s*:\\s*", "").trim();
            }
        }

        // 3) rows: "- CODE | NAME [| PRICE]"
        List<String[]> rows = new ArrayList<>();
        Matcher mRow = Pattern.compile("(?m)^[-•]?\\s*(\\d{4,})\\s*\\|\\s*([^|]*)\\s*(?:\\|\\s*(₹?\\s?[0-9.,]+))?\\s*$").matcher(body);
        while (mRow.find()) {
            String code  = mRow.group(1).trim();
            String name  = mRow.group(2) == null ? "" : mRow.group(2).trim();
            String price = mRow.group(3) == null ? "" : mRow.group(3).replace("INR", "₹").trim();
            rows.add(new String[]{code, name, price});
        }

        // If total wasn’t parsed, infer it from both pages or from row count
        int pageSize = extractSizeFromCmd(rawCmd);
        if (total <= 0 && !rows.isEmpty()) total = rows.size();
        if (totalPages <= 1 && pageSize > 0) {
            totalPages = Math.max(1, (int)Math.ceil(total / (double)pageSize));
        }

        // 4) header
        StringBuilder out = new StringBuilder();
        out.append("🛍️ *Products* — Results: ").append(total)
                .append(" | Page ").append(page).append(" of ").append(totalPages).append("\n");

        // 5) render rows (simple bullets for safety)
        if (rows.isEmpty()) {
            out.append("(No products on this page)");
        } else {
            for (String[] r : rows) {
                String code = r[0];
                String name = (r[1] == null || r[1].isBlank()) ? "—" : r[1]; // show mdash for empty names
                String price = (r[2] == null || r[2].isBlank()) ? "" : " — " + r[2];
                out.append("• ").append(code).append(" — ").append(name).append(price).append("\n");
            }
        }

        // 6) next page hint (only if more pages exist)
        if (page < totalPages) {
            out.append("\n➡️ ").append(nextProductsCmd(rawCmd, page + 1));
        }

        return out.toString().trim();
    }


    private static String fmtCart(String s) {
        StringBuilder out = new StringBuilder("🛒 *Cart*\n");
        Pattern p = Pattern.compile("(?m)^[-•]?\\s*(\\S.*?)\\s*\\|\\s*(\\S.*?)$");
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.append("• ").append(m.group(1).trim())
                    .append(" — ").append(m.group(2).trim()).append("\n");
        }
        if (out.toString().equals("🛒 *Cart*\n"))
            out.append(s.replaceAll("(?m)^- ", "• "));
        return out.toString().trim();
    }

    private static String fmtPending(String s) {
        StringBuilder out = new StringBuilder("📦 *Pending Orders*\n");
        Pattern p = Pattern.compile("(?m)^[-•]?\\s*(\\S.*?)\\s*\\|\\s*(\\S.*?)$");
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.append("• ").append(m.group(1).trim())
                    .append(" — ").append(m.group(2).trim()).append("\n");
        }
        if (out.toString().equals("📦 *Pending Orders*\n"))
            out.append(s.replaceAll("(?m)^- ", "• "));
        return out.toString().trim();
    }

    private static String fmtHeader(String s) {
        Matcher o = Pattern.compile("(?i)outstanding\\s*[:]?\\s*([0-9.,]+)").matcher(s);
        Matcher d = Pattern.compile("(?i)due today\\s*[:]?\\s*([0-9.,]+)").matcher(s);
        Matcher a = Pattern.compile("(?i)(?:available|avl)\\s*credit\\s*[:]?\\s*([0-9.,]+)").matcher(s);

        StringBuilder out = new StringBuilder("🧾 *Account Summary*\n");
        if (o.find()) out.append("Outstanding: ₹").append(formatIndian(o.group(1))).append("\n");
        if (d.find()) out.append("Due today: ₹").append(formatIndian(d.group(1))).append("\n");
        if (a.find()) out.append("Available credit: ₹").append(formatIndian(a.group(1))).append("\n");
        return out.toString().trim();
    }

    // ==========================================================
    // Helpers
    // ==========================================================

    private static String nextProductsCmd(String raw, int nextPage) {
        if (raw == null) return "Next page unavailable.";
        String[] a = raw.trim().split("\\s+");
        if (a.length < 2) return "Next page unavailable.";
        String cat  = a[1];
        String size = (a.length >= 4) ? a[3] : String.valueOf(DEFAULT_PAGE_SIZE);
        String sort = (a.length >= 5) ? a[4] : "name-asc";
        return "Next: products " + cat + " " + nextPage + " " + size + " " + sort;
    }

    private static int extractPageFromCmd(String raw) {
        if (raw == null) return 1;
        String[] a = raw.trim().split("\\s+");
        return (a.length >= 3) ? parseIntSafe(a[2], 1) : 1;
    }

    private static int extractSizeFromCmd(String raw) {
        if (raw == null) return DEFAULT_PAGE_SIZE;
        String[] a = raw.trim().split("\\s+");
        return (a.length >= 4) ? parseIntSafe(a[3], DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static String formatIndian(String num) {
        try {
            long n = Long.parseLong(num.replaceAll("[^0-9]", ""));
            String str = String.valueOf(n);
            int len = str.length();
            if (len <= 3) return str;
            String last3 = str.substring(len - 3);
            String rest = str.substring(0, len - 3);
            StringBuilder sb = new StringBuilder();
            int mod = rest.length() % 2;
            sb.append(rest, 0, mod);
            for (int i = mod; i < rest.length(); i += 2) {
                if (sb.length() > 0) sb.append(',');
                sb.append(rest, i, i + 2);
            }
            sb.append(',').append(last3);
            return sb.toString();
        } catch (Exception e) {
            return num;
        }
    }
    /* public static WhatsappMessage productDetailResponse(ProductDetailResponse p) {
        String price = (p.price() != null && p.price().formattedValue() != null)
                ? p.price().formattedValue() : "N/A";

        // main image
        String imgUrl = null;
        if (p.images() != null && !p.images().isEmpty()) {
            imgUrl = toFullMediaUrl(p.images().get(0).url());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🧾 *Product*\n")
                .append("*Code:* ").append(p.code()).append('\n')
                .append("*Name:* ").append(p.name() != null ? p.name() : "-").append('\n')
                .append("*Price:* ").append(price).append('\n');

        if (imgUrl != null) sb.append("\nImage: ").append(imgUrl);

        // (Optional) show a couple of variants inline
        if (p.variantOptions() != null && !p.variantOptions().isEmpty()) {
            sb.append("\n\n*Variants:*");
            p.variantOptions().stream().limit(3).forEach(v -> {
                String vPrice = (v.priceData() != null && v.priceData().formattedValue() != null)
                        ? v.priceData().formattedValue() : "";
                sb.append("\n- ").append(v.code()).append(" | ")
                        .append(v.name() != null ? v.name() : "")
                        .append(v.packSize() != null ? " | " + v.packSize() : "")
                        .append(vPrice.isBlank() ? "" : " | " + vPrice);
            });
            if (p.variantOptions().size() > 3) sb.append("\n…");
        }

        if (imgUrl != null) {
            return WhatsappMessage.withMedia(sb.toString(), imgUrl);
        } else {
            return WhatsappMessage.text(sb.toString());
        }
    } */
}