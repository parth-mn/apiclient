package com.example.apiclient;

import com.example.apiclient.dto.*;
import com.example.apiclient.formatter.IndianNumberFormatter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.example.apiclient.formatter.WhatsappFormatter;
import com.example.apiclient.CommerceApi;

import static com.example.apiclient.CommerceApi.toFullMediaUrl;

@Service
public class CommandRouter {
    private final TokenService tokens;
    private final CommerceApi api;

    public CommandRouter(TokenService tokens, CommerceApi api) {
        this.tokens = tokens; this.api = api;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static int parseQty(String s) {
        if (s == null) return 1;
        // remove everything that is not 0-9 (handles fullwidth digits, stray spaces, emoji, etc.)
        String digits = s.replaceAll("\\D+", "");
        if (digits.isBlank()) return 1;
        try {
            int q = Integer.parseInt(digits);
            return q > 0 ? q : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    public Mono<String> handle(String line) {
        if (line == null || line.isBlank()) return Mono.just("Send *help* to see commands.");
        String[] parts = line.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        return tokens.getAccessToken().flatMap(t -> switch (cmd) {

            case "hi","hey","hello" -> Mono.just("""
        👋 Hi! How can I help you today?
        1. Searching and ordering
        2. View pending orders
        3. Outstanding information
        \u200B
        Type *help* to see a full list of commands.
        """.trim());

            case "1" -> Mono.just("""
                    🔎 *Searching and Ordering Products*

        You can use these simple messages to explore and order products:
                    \u200B
        • Type *categories* to see all available product groups  
        • Type *products interior* or *products exterior* to browse within a category  
        • Type *search <product name>* — for example:  
          👉 `search super bright` or `search red primer`  
        • Type *product <code>* to view details — e.g. `product 51707`  
        • Type *add <sku> <qty>* to add to your cart — e.g. `add 51707 2`  
        • Type *cart* anytime to view what you’ve added  
        • When you’re ready, type *order* to place it!
                    \u200B
        💡 *Tip:* You can also type only part of a name —  
        like `search bright` — to find matching items quickly.
        """.trim());

            case "help" -> Mono.just("""
                    🤖 *Bot Commands*
                    \n\u200B\n
                    • *categories*
                    • *products* <categoryCode> [page] [size]
                    • *search* <text> [page] [size] [sort]
                    • *product* <productCode>
                    • *info* <sku / short name>
                    • *add* <skuCode> <qty>
                    • *setqty* <entryNumber> <qty>
                    • *cart*
                    • *order*
                    • *header*
                    • *pending* [page] [size]
                    """);

            case "categories" -> api.getCategories(t).map(res -> {
                StringBuilder sb = new StringBuilder("📂 *Available Categories:*\n\u200B\n");
                if (res.customerCategories() != null) {
                    res.customerCategories().forEach(c ->
                            sb.append("- ").append(c.name()).append("\n")
                    );
                } else {
                    sb.append("(no categories found)\n");
                }
                return sb.toString().trim();
            });

            case "products" -> {
                if (parts.length < 2) yield Mono.just("Usage: *products* <categoryCode> [page] [size] [sort]");
                String cat  = parts[1];

                // user-facing (1-based) page & defaults
                int userPage = parts.length >= 3 ? parseIntSafe(parts[2], 1) : 1;   // default 1
                int size     = parts.length >= 4 ? parseIntSafe(parts[3], 12) : 12; // default 12
                String sort  = parts.length >= 5 ? parts[4] : "name-asc";

                // API expects 0-based page index
                int apiPage  = Math.max(0, userPage - 1);

                yield api.searchProductsByCategory(t, cat, apiPage, size, sort).map(r -> {
                    int totalResults = (r.pagination() != null && r.pagination().totalResults() != null)
                            ? r.pagination().totalResults() : 0;

                    int totalPages = (r.pagination() != null && r.pagination().totalPages() != null)
                            ? r.pagination().totalPages()
                            : Math.max(1, (int) Math.ceil(totalResults / (double) Math.max(1, size)));

                    StringBuilder sb = new StringBuilder();
                    sb.append("🛍️ *Products in* `").append(cat).append("`\n")
                      .append("Total: ").append(totalResults)
                      .append(" — Page ").append(userPage).append("/").append(totalPages).append("\n\u200B\n");

                    if (r.products() != null && !r.products().isEmpty()) {
                        r.products().forEach(p -> {
                            String code = p.code() != null ? p.code() : "-";
                            String name = p.name() != null && !p.name().isBlank() ? p.name() : "";
                            sb.append("- *").append(name).append("*")
                              .append(" (").append(code).append(")")
                              .append("\n");
                        });
                    } else {
                        sb.append("(no products on this page)\n");
                    }

                    if (userPage < totalPages) {
                        sb.append("\n\u200B\n_Next:_ `products ")
                          .append(cat).append(" ")
                          .append(userPage + 1).append(" ")
                          .append(size).append(" ").append(sort)
                          .append("`");
                    }

                    return sb.toString().trim();
                });
            }

            case "search" -> {
                if (parts.length < 2) yield Mono.just("Usage: *search* <text> [page] [size] [sort]");
                String term = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)).trim();

                int userPage = (parts.length >= 3) ? parseIntSafe(parts[2], 1) : 1;   // user page starts at 1
                int size     = (parts.length >= 4) ? parseIntSafe(parts[3], 12) : 12;
                String sort  = (parts.length >= 5) ? parts[4] : "name-asc";

                int apiPage = Math.max(0, userPage - 1); // convert to 0-based

                yield api.searchProductsByText(t, term, apiPage, size, sort).map(r -> {
                    int total = (r.pagination() != null && r.pagination().totalResults() != null)
                            ? r.pagination().totalResults() : 0;
                    int curr  = (r.pagination() != null && r.pagination().currentPage() != null)
                            ? r.pagination().currentPage() : apiPage;
                    int pages = (r.pagination() != null && r.pagination().totalPages() != null)
                            ? r.pagination().totalPages() : 1;

                    StringBuilder sb = new StringBuilder();
                    sb.append("🔎 *Search:* `").append(term).append("`\n")
                      .append("Results: ").append(total)
                      .append(" — Page ").append(curr + 1).append("/").append(pages).append("\n\u200B\n");

                    if (r.products() != null && !r.products().isEmpty()) {
                        r.products().forEach(p -> {
                            sb.append("- *")
                              .append(p.name() != null ? p.name() : "")
                              .append("* (").append(p.code()).append(")");
                            if (p.price() != null && p.price().formattedValue() != null) {
                                sb.append("\n  Price: ").append(p.price().formattedValue());
                            }
                            sb.append("\n");
                        });
                    } else {
                        sb.append("(no results)\n");
                    }

                    if (curr + 1 < pages) {
                        sb.append("\n\u200B\n_Next:_ `search ")
                          .append(term).append(" ")
                          .append(curr + 2).append(" ")
                          .append(size).append(" ").append(sort)
                          .append("`");
                    }
                    return sb.toString().trim();
                }).onErrorResume(e -> Mono.just("API error: " + e.getMessage()));
            }

            case "info" -> {
                if (parts.length < 2) yield Mono.just("Usage: *info* <searchCode> (e.g., sku silver 8001)");

                String searchCode = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)).trim();
                System.out.println(searchCode);
                yield api.searchProductBySkuOrShortName(t, searchCode)
                        .map(resp -> {
                            var list = (resp != null) ? resp.variantSearches() : null;
                            if (list == null || list.isEmpty()) return "No matches found.";

                            StringBuilder sb = new StringBuilder();
                            sb.append("🔎 SKU Search: ").append(searchCode).append("\n\u200B\n");
                            for (var v : list) {
                                String sku = v.skuCode() != null ? v.skuCode() : "";
                                String name = v.productShortName() != null
                                        ? v.productShortName()
                                        : (v.baseProductName() != null ? v.baseProductName() : "");
                                String packSize = v.packSize() != null ? v.packSize() : "";
                                String shade = v.shadeName() != null ? v.shadeName() : "";

                                sb.append("\n\u200B\n- SKU: ").append(sku).append("\n");
                                if (!name.isEmpty()) sb.append("Name: ").append(name).append("\n");
                                if (!packSize.isEmpty()) sb.append("Pack Size: ").append(packSize).append("\n");
                                if (!shade.isEmpty()) sb.append("Shade: ").append(shade).append("\n");

                                if (v.productImage() != null && v.productImage().url() != null) {
                                    String full = toFullMediaUrl(v.productImage().url());
                                    if (full != null) sb.append("Image: ").append(full).append("\n");
                                }
                            }
                            return sb.toString().trim();
                        })
                        .onErrorResume(e -> Mono.just("API error: " + e.getMessage()));
            }

            // v2 product with better formatting & no SKUs list
            case "product" -> {
                if (parts.length < 2) yield Mono.just("Usage: *product* <productCode>");
                String code = parts[1].trim();

                yield api.getProductDetails(t, code).map(p -> {
                    StringBuilder sb = new StringBuilder();

                    sb.append("🧾 *Product Details*\n");
                    sb.append("*").append(p.name()).append("* (").append(p.code()).append(")\n");

                    // Image link (unchanged logic)
                    if (p.images() != null && !p.images().isEmpty()) {
                        String rawUrl = p.images().get(0).url();  // first image
                        if (rawUrl != null && !rawUrl.isBlank()) {
                            String base = "https://api.cc01erru2b-grasimind1-s1-public.model-t.cc.commerce.ondemand.com";
                            String fullUrl = rawUrl.startsWith("/") ? base + rawUrl : base + "/" + rawUrl;
                            sb.append("\n\u200B\nImage: ").append(fullUrl).append("\n");
                        }
                    }
                    return sb.toString().trim();
                });
            }

            case "add" -> {
                if (parts.length < 3) yield Mono.just("Usage: *add* <skuCode> <qty>");
                String sku = parts[1].trim();
                int qty = parseQty(parts[2]);
                System.out.println("[ADD] sku=" + sku + " qty=" + qty);

                yield Mono.fromSupplier(() -> api.addSkuToCartSync(t, sku, qty))
                        .map(result -> result.equals("OK")
                                ? "✅ *Added to Cart!*\n\u200B\nSKU: " + sku + "\nQuantity: " + qty
                                : result);
            }
            /*case "add" -> {
                if (parts.length < 3) yield Mono.just("Usage: add <skuCode> <qty>");
                String sku = parts[1].trim();
                int qty = parseQty(parts[2]);
                System.out.println("[ADD] sku=" + sku + " qty=" + qty);

                yield api.addSkuToCart(t, sku, qty)
                        .map(resp -> "✅ Added to cart\nSKU: " + sku + "\nQty: " + qty)
                        .onErrorResume(e -> Mono.just("API error: " + e.getMessage()));
            }*/


            case "setqty" -> {
                if (parts.length < 3) yield Mono.just("Usage: *setqty* <entryNumber> <qty>");
                int entry = parseIntSafe(parts[1], 0);
                int qty   = parseIntSafe(parts[2], 1);
                yield api.updateCartEntryQuantity(t, entry-1, qty)
                        .map(r -> "✏️ *Updated Cart Entry!*\n\u200B\nEntry: #" + entry + "\nQuantity: " + qty +
                                "\nStatus: " + r.statusCode());
            }

            case "cart" -> api.getCurrentCart(t).map(c -> {
                int totalItems = c.totalItems() != null ? c.totalItems() : 0;
                StringBuilder sb = new StringBuilder();
                sb.append("🛒 *Your Cart*\n\u200B\n");
                sb.append("Cart Code: ").append(c.code()).append("\n");
                sb.append("Total Items: ").append(totalItems).append("\n\u200B\n");

                if (c.entries() != null && !c.entries().isEmpty()) {
                    c.entries().forEach(e -> sb.append("- #")
                            .append(e.entryNumber() + 1)
                            .append("  x").append(e.quantity())
                            .append(" — ")
                            .append(e.product() != null ? e.product().name() : "")
                            .append("\n"));
                } else {
                    sb.append("(cart is empty)");
                }
                return sb.toString().trim();
            });

            case "order" -> api.placeOrderFromCart(t)
                    .map(o -> "✅ *Order Placed!*\n\u200B\nOrder: " + o.code() + "\nStatus: " + o.statusDisplay());

            case "header","3" -> api.fetchHeaderInfo(t).map(h -> {
                String outstanding    = IndianNumberFormatter.formatIndianNumberWithDecimals(h.totalOutstanding());
                String netOutstanding = IndianNumberFormatter.formatIndianNumberWithDecimals(h.netOutstanding());
                String overdue        = IndianNumberFormatter.formatIndianNumberWithDecimals(h.totalOverdue());
                String dueToday       = IndianNumberFormatter.formatIndianNumberWithDecimals(h.dueToday());
                String dueLater       = IndianNumberFormatter.formatIndianNumberWithDecimals(h.dueLater());
                String availableCredit= IndianNumberFormatter.formatIndianNumberWithDecimals(h.availableCreditLimit());
                String unutilizedCred = IndianNumberFormatter.formatIndianNumberWithDecimals(h.unutilizedCredit());
                String daysDue        = (h.dueInDays() == null) ? "0" : String.valueOf(h.dueInDays().intValue());

                return """
                        💰 *Dealer Account Details*
                        Outstanding: *₹%s*
                        Net Outstanding: *₹%s*
                        Overdue: *₹%s*
                        \u200B
                        📅 *Dues*
                        • Due Today: *₹%s*
                        • Due Later: *₹%s*
                        • Days Due: *%s*
                        \u200B
                        💳 *Credit*
                        • Available Credit: *₹%s*
                        • Unutilized Credit: *₹%s*
                        """.formatted(
                        outstanding,
                        netOutstanding,
                        overdue,
                        dueToday,
                        dueLater,
                        daysDue,
                        availableCredit,
                        unutilizedCred
                ).trim();
            });

            case "pending","2" -> {
                int page = parts.length >= 2 ? parseIntSafe(parts[1], 0) : 0;
                int size = parts.length >= 3 ? parseIntSafe(parts[2], 10) : 10;
                yield api.fetchPendingOrders(t, page, size).map(po -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("📦 *Pending / Approved Orders*\n");
                    sb.append("Page: ").append(page+1).append("  Size: ").append(size).append("\n\u200B\n");

                    if (po.orders() != null && !po.orders().isEmpty()) {
                        po.orders().forEach(o -> sb.append("- *")
                                .append(o.displayCode()).append("*")
                                .append("\n  Status: ").append(o.statusDisplay())
                                .append("\n  Items: ").append(o.pendingItemCount())
                                .append(" | Qty: ").append(o.pendingTotalQuantity())
                                .append("\n\u200B\n"));
                    } else {
                        sb.append("(no pending/approved orders)");
                    }
                    return sb.toString().trim();
                });
            }

            default -> Mono.just("Unknown command. Send *help*.");
        });
    }
}
