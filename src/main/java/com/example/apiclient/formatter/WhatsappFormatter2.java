package com.example.apiclient.formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhatsappFormatter2 {

    private static final int WHATSAPP_SAFE_LIMIT = 1500;

    private static int si(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    public static String format(String original, String rawCommand) {
        if (original == null || original.isBlank()) return "⚠️ No data.";

        String s = original.trim();
        s = NumberText.deScientific(s);
        s = NumberText.normalizeInr(s);

        String lower = rawCommand == null ? "" : rawCommand.trim().toLowerCase();
        if (lower.startsWith("categories")) return fmtCategories(s);
        if (lower.startsWith("products"))   return fmtProducts(s, rawCommand);
        //if (lower.startsWith("product"))    return fmtProductDetails(s);
        if (lower.startsWith("product"))    return "📜 *Product Details* \n" + s;
        if (lower.startsWith("add"))        return fmtAdd(s);
        if (lower.startsWith("cart"))       return fmtCart(s);
        if (lower.startsWith("order"))      return fmtOrder(s);
        if (lower.startsWith("pending"))    return fmtPending(s);
        if (lower.startsWith("header"))     return fmtHeader(s);
        if (lower.startsWith("help"))       return "ℹ️ *Help*\n" + s.replaceAll("(?m)^- ", "• ");

        return s.replaceAll("(?m)^- ", "• ");
    }

    private static final int DEFAULT_PAGE_SIZE = 20; // set this to your desired "max"



    private static int extractPageFromCmd(String raw) {
        if (raw == null) return 1;
        String[] a = raw.trim().split("\\s+");
        // products <cat> [page] [size] [sort]
        return (a.length >= 3) ? si(a[2], 1) : 1; // 1-based
    }

    private static int extractSizeFromCmd(String raw) {
        if (raw == null) return DEFAULT_PAGE_SIZE;
        String[] a = raw.trim().split("\\s+");
        return (a.length >= 4) ? si(a[3], DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    }


    // ========= CATEGORIES (6) =========
    private static String fmtCategories(String s) {
        // Input lines look like: "- interior | Interior"
        List<String[]> rows = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^[-•]\\s*([^|]+)\\|\\s*(.+)$").matcher(s);
        while (m.find()) rows.add(new String[]{m.group(1).trim(), m.group(2).trim()});

        if (rows.isEmpty()) return "🗂️ *Categories*\n" + s.replaceAll("(?m)^- ", "• ");
        MonoTable t = new MonoTable().header("CODE", "NAME");
        rows.forEach(r -> t.row(r[0], r[1]));
        return "🗂️ *Categories*\n" + t.render();
    }

    // ========= PRODUCTS LIST (2,5) =========
    private static String fmtProducts(String s, String rawCmd) {
        // Support BOTH header styles:
        //   A) "Results: N (page P/T)"
        //   B) "Products (N):" [older]

        int total = 0, cur = 1, totalPages = 1;

// New header style: "Results: N (page P/T)"
        Matcher a = Pattern.compile("(?im)^results:\\s*(\\d+)\\s*\\(page\\s*(\\d+)/(\\d+)\\)").matcher(s);
        if (a.find()) {
            total = si(a.group(1), 0);
            cur = si(a.group(2), 1);
            totalPages = si(a.group(3), 1);
        } else {
            // Old header style: "Products (N):"
            Matcher b = Pattern.compile("(?im)^products\\s*\\((\\d+)\\)\\s*:").matcher(s);
            if (b.find()) total = si(b.group(1), 0);

            // Derive from user's command if not printed in response
            int cmdPage = extractPageFromCmd(rawCmd);   // 1-based
            int cmdSize = extractSizeFromCmd(rawCmd);   // default = DEFAULT_PAGE_SIZE
            cur = (cmdPage > 0) ? cmdPage : 1;

            if (total > 0 && cmdSize > 0) {
                totalPages = Math.max(1, (int) Math.ceil(total / (double) cmdSize));
            }

            // If the text itself contains "page X of Y", prefer it
            Matcher p = Pattern.compile("(?im)\\bpage\\s*(\\d+)\\s*(?:of|/)\\s*(\\d+)").matcher(s);
            if (p.find()) {
                cur = si(p.group(1), cur);
                totalPages = si(p.group(2), totalPages);
            }
        }


    /*    int total = 0, cur = 1, totalPages = 1;

        Matcher a = Pattern.compile("(?im)^results:\\s*(\\d+)\\s*\\(page\\s*(\\d+)/(\\d+)\\)").matcher(s);
        if (a.find()) {
            total = si(a.group(1), 0);
            cur = si(a.group(2), 1);
            totalPages = si(a.group(3), 1);
        }
    } else {
        // Old header style: "Products (N):"
        Matcher b = Pattern.compile("(?im)^products\\s*\\((\\d+)\\)\\s*:").matcher(s);
        if (b.find()) total = si(b.group(1), 0);

        // Derive page & size from the user’s command if present, else defaults
        int cmdPage = extractPageFromCmd(rawCmd);   // 1-based
        int cmdSize = extractSizeFromCmd(rawCmd);   // default 12 (or your max)
        if (cmdPage > 0) cur = cmdPage;
        if (cmdSize <= 0) cmdSize = 12;

        // Compute total pages if we know total and size
        if (total > 0 && cmdSize > 0) {
            totalPages = Math.max(1, (int)Math.ceil(total / (double)cmdSize));
        }

        // If somewhere we already have "page X of Y" in the text, prefer it
        Matcher p = Pattern.compile("(?im)\\bpage\\s*(\\d+)\\s*(?:of|/)\\s*(\\d+)").matcher(s);
        if (p.find()) {
            cur = si(p.group(1), cur);
            totalPages = si(p.group(2), totalPages);
        }
    } */

        /* else {
            Matcher b = Pattern.compile("(?im)^products\\s*\\((\\d+)\\)\\s*:").matcher(s);
            if (b.find()) total = si(b.group(1), 0);
            // try to pull page line if present elsewhere
            Matcher p = Pattern.compile("(?im)\\bpage\\s*(\\d+)\\s*(?:of|/)\\s*(\\d+)").matcher(s);
            if (p.find()) { cur = si(p.group(1), 1); totalPages = si(p.group(2), 1); }
        } */

        // Parse rows from either "- code | name | price" OR bare "code   name   price"
        List<String[]> rows = new ArrayList<>();
        Matcher m1 = Pattern.compile("(?m)^[-•]\\s*(\\S+)\\s*\\|\\s*([^|]+?)(?:\\s*\\|\\s*(.*))?$").matcher(s);
        while (m1.find()) rows.add(new String[]{m1.group(1).trim(), m1.group(2).trim(), nnPrice(m1.group(3))});

        if (rows.isEmpty()) {
            // fallback: table-like lines "CODE  NAME  PRICE"
            Matcher m2 = Pattern.compile(
                    "(?m)^(\\d{4,})(?!\\s*-\\s*\\d{4,})\\s+([A-Za-z].*?)(?:\\s+(₹\\s?\\d[\\d,]*\\.?\\d*))?$"
            ).matcher(s);
            while (m2.find()) {
                String code  = m2.group(1).trim();
                String name  = m2.group(2).trim();
                String price = nnPrice(m2.group(3));      // ensures ₹ and handles blanks
                rows.add(new String[]{code, name, price});
            }

         /*   Matcher m2 = Pattern.compile("(?m)^(\\d{4,})\\s+([A-Z0-9].*?)(?:\\s+₹?\\d[\\d,]*\\.?\\d*)?$").matcher(s);
            while (m2.find()) {
                String line = m2.group(0);
                // try to sniff price at end
                Matcher pr = Pattern.compile("(₹\\s?\\d[\\d,]*\\.?\\d*)$").matcher(line);
                String price = pr.find() ? pr.group(1) : "";
                rows.add(new String[]{m2.group(1).trim(), m2.group(2).trim(), price});
            }*/
        }

        StringBuilder out = new StringBuilder();
        out.append("🛍️ *Products* — Results: ").append(total).append(" | Page ").append(cur).append(" of ").append(totalPages).append("\n");

        MonoTable t = new MonoTable().header("CODE", "NAME", "PRICE");
        if (!rows.isEmpty()) rows.forEach(r -> t.row(r[0], r[1], r[2]));
        out.append(t.render());

        if (cur < totalPages) out.append("\n\n").append("➡️ ").append(nextProductsCmd(rawCmd, cur + 1));

        return out.toString();
    }

    private static String nextProductsCmd(String raw, int nextPage) {
        if (raw == null) return "Next page command unavailable";
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) return "Next page command unavailable";
        String cat  = parts[1];
        String size = (parts.length >= 4) ? parts[3] : "20";
        String sort = (parts.length >= 5) ? parts[4] : "name-asc";
        return "Next: products " + cat + " " + nextPage + " " + size + " " + sort;
    }

    // ========= PRODUCT DETAILS (1) =========
    private static String fmtProductDetails(String s) {
        // Strip trailing " | null"
        String clean = s.replaceAll("\\s*\\|\\s*null", "");
        // Try to extract SKUs rows if present: "• <code> | <desc> | <pack> | <price?>"
        List<String[]> rows = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^[-•]\\s*(\\S+)\\s*\\|\\s*([^|]+)(?:\\s*\\|\\s*([^|]+))?(?:\\s*\\|\\s*(.*))?$").matcher(clean);
        while (m.find()) rows.add(new String[]{m.group(1).trim(), nn(m.group(2)), nn(m.group(3)), nnPrice(m.group(4))});

        if (rows.isEmpty()) return "🧾 *Product Details*\n" + clean.replaceAll("(?m)^- ", "• ");

        MonoTable t = new MonoTable().header("SKU", "DESC", "PACK", "PRICE");
        rows.forEach(r -> t.row(r[0], r[1], r[2], r[3]));
        // Keep the top two lines (product code | name) above the table if present
        String head = clean.split("\\R", 3)[0] + "\n" + clean.split("\\R", 3)[1];
        return "🧾 *Product Details*\n" + head + "\n" + t.render();
    }

    // ========= ADD (3) =========
    private static String fmtAdd(String s) {
        String t = s.replace(" x", " ×");
        return "✅ " + t;
    }

    // ========= CART (3) =========
    private static String fmtCart(String s) {
        // Expect first line like "*Cart: 0000597018 | totalItems 1*"
        String first = firstLine(s);
        // Extract cart code and total
        Matcher m = Pattern.compile("(?i)cart\\s*[:]?\\s*(\\S+)\\s*\\|\\s*totalItems\\s*(\\d+)").matcher(first);
        String code = "", total = "";
        if (m.find()) { code = m.group(1); total = m.group(2); }

        StringBuilder out = new StringBuilder();
        out.append("🛒 *Cart* ").append(code.isBlank() ? "" : code + " ").append("| ").append("Total Items: ").append(total.isBlank() ? "0" : total).append("\n");

        String body = s.substring(first.length()).trim();
        body = body.replaceAll("(?m)^- ", "• ");
        // “• #0 x1 | Name” → “• 1) ×1 — Name”
        Pattern idxQty = Pattern.compile("#(\\d+)\\s*x(\\d+)");
        body = idxQty.matcher(body).replaceAll(mr -> (si(mr.group(1), 0) + 1) + ") ×" + mr.group(2));
        body = body.replace(" | ", " — ");

        out.append(body);
        return out.toString().trim();
    }

    // ========= ORDER =========
    private static String fmtOrder(String s) {
        String t = s.replaceFirst("(?i)^\\*?order placed\\*?\\s*", "");
        return "✅ *Order Placed*\n" + t;
    }

    // ========= PENDING (table when parsable) =========
    private static String fmtPending(String s) {
        String head = "📦 *Pending/Approved Orders*";
        List<String[]> rows = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^[-•]\\s*(\\S+)\\s*\\|\\s*([^|]+)\\s*\\|\\s*items\\s*(\\d+)\\s*\\|\\s*qty\\s*(\\d+)\\s*$").matcher(s);
        while (m.find()) rows.add(new String[]{m.group(1), m.group(2).trim(), m.group(3), m.group(4)});
        if (rows.isEmpty()) return head + "\n" + s.replaceAll("(?m)^- ", "• ");
        MonoTable t = new MonoTable().header("ORDER", "STATUS", "ITEMS", "QTY");
        rows.forEach(r -> t.row(r));
        return head + "\n" + t.render();
    }

    // ========= HEADER / ACCOUNT SUMMARY (4) =========
    private static String fmtHeader(String s) {
        // Extract numeric triplet in any order
        // e.g., "Outstanding 1415871.0 | Due today 1415871.0 | Avl credit 251662645"
        Matcher o = Pattern.compile("(?i)outstanding\\s+([0-9.,]+)").matcher(s);
        Matcher d = Pattern.compile("(?i)due today\\s+([0-9.,]+)").matcher(s);
        Matcher a = Pattern.compile("(?i)(?:avl\\s*credit|available\\s*credit)\\s+([0-9.,]+)").matcher(s);
        String out = o.find() ? NumberText.formatINR(o.group(1)) : "";
        String due = d.find() ? NumberText.formatINR(d.group(1)) : "";
        String avl = a.find() ? NumberText.formatINR(a.group(1)) : "";

        StringBuilder b = new StringBuilder();
        b.append("🧾 *Account Summary*\n");
        if (!out.isBlank()) b.append("Outstanding: ").append(out).append('\n');
        if (!due.isBlank()) b.append("Due today: ").append(due).append('\n');
        if (!avl.isBlank()) b.append("Available credit: ").append(avl);
        return b.toString().trim();
    }

    // ========= utils =========
    public static List<String> chunk(String formatted) {
        List<String> chunks = new ArrayList<>();
        if (formatted == null) return chunks;
        String[] lines = formatted.split("\\r?\\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.length() + line.length() + 1 > WHATSAPP_SAFE_LIMIT) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append("\n");
            current.append(line);
        }
        if (current.length() > 0) chunks.add(current.toString());
        if (chunks.size() > 1) {
            int total = chunks.size();
            for (int i = 0; i < total; i++) chunks.set(i, "(" + (i + 1) + "/" + total + ") " + chunks.get(i));
        }
        return chunks;
    }

    //private static int si(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
    private static String nn(String s) { return NumberText.nn(s); }
    private static String nnPrice(String p) {
        if (p == null || p.equalsIgnoreCase("null") || p.isBlank()) return "";
        // ensure ₹ present when value looks numeric
        if (!p.contains("₹") && p.matches("^[0-9.,]+$")) return NumberText.formatINR(p);
        return p.trim();
    }
    private static String firstLine(String s) { int i = s.indexOf('\n'); return i >= 0 ? s.substring(0, i) : s; }
}
