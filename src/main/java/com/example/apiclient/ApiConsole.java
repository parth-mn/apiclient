package com.example.apiclient;

import com.example.apiclient.dto.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Scanner;

@Component
public class ApiConsole {
    private final TokenService tokens;
    private final CommerceApi api;

    public ApiConsole(TokenService tokens, CommerceApi api) {
        this.tokens = tokens; this.api = api;
    }

   /* public void runInteractive() {
        System.out.println("== Direct OCC Client ==");
        System.out.println("Type a command (help to list):");

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!sc.hasNextLine()) {          // <— key fix: handle EOF gracefully
                    System.out.println("\nInput closed. Bye!");
                    break;
                }
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;
                if (line.equalsIgnoreCase("help")) { printHelp(); continue; }
                try { handle(line); } catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
            }
        }
    } */


         public void runInteractive() {
        System.out.println("== Direct OCC Client ==");
        System.out.println("Type a command (help to list):");

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine().trim();
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;
                if (line.equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }
                try {
                    handle(line);
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        }
    }

    private void printHelp() {
        System.out.println("""
        Commands:
          categories
          products <categoryCode> [page=0] [size=12] [sort=name-asc|name-desc|price-asc|price-desc]
          product <productCode>
          cart
          add <skuCode> <qty>
          setqty <entryNumber> <qty>
          order
          header
          pending [page=0] [size=10]
          exit
        """);
    }

    private void handle(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length == 0) return;

        switch (parts[0].toLowerCase()) {
            case "categories" -> withToken(t -> api.getCategories(t)
                    .doOnNext(res -> {
                        System.out.println("Categories (" + res.totalCategoriesCount() + "):");
                        if (res.customerCategories() != null)
                            res.customerCategories().forEach(c -> System.out.println(" - " + c.code() + " | " + c.name()));
                    }));

            // ApiConsole.handle(...)
            case "products" -> {
                if (parts.length < 2) {
                    System.out.println("Usage: products <categoryCode> [page=1] [size=12] [sort=name-asc|name-desc|price-asc|price-desc|relevance]");
                    break;
                }
                String cat  = parts[1];
                int pageInput = (parts.length >= 3) ? safeInt(parts[2], 1) : 1;  // user-facing (1-based)
                int page      = Math.max(0, pageInput - 1);                      // API-facing (0-based)
                int size      = (parts.length >= 4) ? safeInt(parts[3], 12) : 12;
                String sort   = (parts.length >= 5) ? parts[4] : "name-asc";


                withToken(t -> api.searchProductsByCategory(t, cat, page, size, sort)
                        .map(res -> {
                            int total = 0, totalPages = 1, currentPage = page;
                            if (res.pagination() != null) {
                                if (res.pagination().totalResults() != null) total = res.pagination().totalResults();
                                if (res.pagination().totalPages() != null) totalPages = res.pagination().totalPages();
                                if (res.pagination().currentPage() != null) currentPage = res.pagination().currentPage();
                            } else if (res.products() != null) {
                                total = res.products().size();
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Results: ").append(total)
                                    .append(" (page ").append(currentPage + 1).append("/").append(totalPages).append(")\n");

                            if (res.products() == null || res.products().isEmpty()) {
                                sb.append("(Tip: use an exact category from 'categories', e.g., wood-finishes / interior)\n");
                                return sb.toString();
                            }

                            res.products().forEach(p -> {
                                String priceStr = (p.price() != null && p.price().formattedValue() != null)
                                        ? " | " + p.price().formattedValue()
                                        : "";
                                sb.append("- ").append(p.code()).append(" | ").append(p.name()).append(priceStr).append("\n");
                            });

                            // Pagination hint
                            if (currentPage + 1 < totalPages) {
                                sb.append("Next: products ").append(cat).append(" ").append(currentPage + 2)
                                        .append(" ").append(size).append(" ").append(sort).append("\n");
                            }
                            return sb.toString();
                        })
                        .doOnNext(System.out::print)
                );
            }


            case "products-static" -> withToken(t ->
                    api.searchWoodFinishesStatic(t)
                            .map(page -> {
                                int total = (page.pagination() != null && page.pagination().totalResults() != null)
                                        ? page.pagination().totalResults()
                                        : (page.products() != null ? page.products().size() : 0);

                                StringBuilder sb = new StringBuilder("Results: " + total + " (query="
                                        + (page.currentQuery() != null && page.currentQuery().query() != null
                                        ? page.currentQuery().query().value() : "")
                                        + ")\n");

                                if (page.products() == null || page.products().isEmpty()) {
                                    sb.append("(No products returned.)\n");
                                    return sb.toString();
                                }

                                page.products().forEach(p -> {
                                    String priceStr = (p.price() != null && p.price().formattedValue() != null)
                                            ? " | " + p.price().formattedValue() : "";
                                    sb.append("- ").append(p.code()).append(" | ").append(p.name()).append(priceStr).append("\n");
                                });

                                return sb.toString();
                            })
                            .doOnNext(System.out::print)   // <-- PRINT IT
            );


            case "product" -> {
                if (parts.length < 2) { System.out.println("Usage: product <productCode>"); break; }
                String code = parts[1];
                withToken(t -> api.getProductDetails(t, code)
                        .doOnNext(p -> {
                            System.out.println(p.code() + " | " + p.name() + " | from " + (p.price()!=null?p.price().formattedValue():"N/A"));
                            if (p.variantOptions() != null) {
                                System.out.println("  SKUs:");
                                p.variantOptions().forEach(v -> System.out.println("   - " + v.code() + " | " + v.name()
                                        + " | " + v.packSize() + " | " + (v.priceData()!=null?v.priceData().formattedValue():"")));
                            }
                        }));
            }

            case "info" -> {
                if (parts.length < 2) { System.out.println("Usage: sku <searchCode>"); break; }
                String searchCode = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)).trim();
                withToken(t -> api.searchProductBySkuOrShortName(t, searchCode)
                        .doOnNext(resp -> {
                            var list = (resp != null) ? resp.variantSearches() : null;
                            if (list == null || list.isEmpty()) {
                                System.out.println("No matches found.");
                                return;
                            }
                            System.out.println("Matches: " + list.size());
                            list.forEach(v -> {
                                System.out.println("- " + safe(v.skuCode()) + " | " + safe(v.baseProductName())
                                        + " | " + safe(v.productShortName()) + " | " + safe(v.packSize())
                                        + " | " + safe(v.shadeName()));
                                if (v.productImage() != null && v.productImage().url() != null) {
                                    String full = CommerceApi.toFullMediaUrl(v.productImage().url());
                                    if (full != null) System.out.println("  Image: " + full);
                                }
                            });
                        })
                        .onErrorResume(e -> { System.out.println("Error: " + e.getMessage()); return Mono.empty(); })
                );
            }


            case "search" -> {
                if (parts.length < 2) {
                    System.out.println("Usage: search <text> [page] [size] [sort]");
                    break;
                }
                // Join all parts except the ones that are numbers or sorting codes
                int firstNumericIndex = -1;
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].matches("\\d+")) {
                        firstNumericIndex = i;
                        break;
                    }
                }
                int endIndex = (firstNumericIndex == -1) ? parts.length : firstNumericIndex;
                String term = String.join(" ", Arrays.copyOfRange(parts, 1, endIndex));

                int page = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 0;
                int size = (parts.length >= 4) ? Integer.parseInt(parts[3]) : 12;
                String sort = (parts.length >= 5) ? parts[4] : "name-asc";

                withToken(t -> api.searchProductsByText(t, term, page, size, sort)
                        .doOnNext(r -> {
                            System.out.println("Results: " + (r.pagination() != null ? r.pagination().totalResults() : 0));
                            if (r.products() != null) {
                                r.products().forEach(p -> System.out.println(
                                        "- " + p.code() + " | " + p.name() +
                                                (p.price() != null ? " | " + p.price().formattedValue() : "")
                                ));
                            }
                            if (r.pagination() != null && (r.pagination().currentPage() + 1) < r.pagination().totalPages()) {
                                System.out.println("Next: search " + term + " " + (r.pagination().currentPage() + 1) + " " + size + " " + sort);
                            }
                        })
                        .onErrorResume(e -> {
                            System.out.println("Error: " + e.getMessage());
                            return Mono.empty();
                        })
                );
            }


            case "cart" -> withToken(t -> api.getCurrentCart(t)
                    .doOnNext(c -> {
                        System.out.println("Cart " + c.code() + " | Total Items: " + c.totalItems());
                        if (c.entries() != null) {
                            c.entries().forEach(e -> System.out.println("  #" + e.entryNumber()+ 1 + " x" + e.quantity()
                                    + " | " + (e.product()!=null?e.product().name():"") + " | " + (e.totalPrice()!=null?e.totalPrice().value():"")));
                        }
                    }));

            case "add" -> {
                if (parts.length < 3) { System.out.println("Usage: add <skuCode> <qty>"); break; }
                String sku = parts[1]; int qty = Integer.parseInt(parts[2]);
                withToken(t -> api.addSkuToCart(t, sku, qty)
                        .doOnNext(r -> System.out.println("Add status: " + r.statusCode() + " | entry #" + (r.entry()!=null?r.entry().entryNumber():null))));
            }

            case "setqty" -> {
                if (parts.length < 3) { System.out.println("Usage: setqty <entryNumber> <qty>"); break; }
                int entry = Integer.parseInt(parts[1]); int qty = Integer.parseInt(parts[2]);
                withToken(t -> api.updateCartEntryQuantity(t, entry, qty)
                        .doOnNext(r -> System.out.println("Update status: " + r.statusCode() + " | qty now " + r.quantity())));
            }

            case "order" -> withToken(t -> api.placeOrderFromCart(t)
                    .doOnNext(o -> System.out.println("Order placed: " + o.code() + " | " + o.statusDisplay())));

            case "header" -> withToken(t -> api.fetchHeaderInfo(t)
                    .doOnNext(h -> System.out.println("Outstanding: " + h.totalOutstanding() + " | Due today: " + h.dueToday()
                            + " | Credit limit avail: " + h.availableCreditLimit())));

            case "pending" -> {
                int page = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
                int size = parts.length >= 3 ? Integer.parseInt(parts[2]) : 10;
                withToken(t -> api.fetchPendingOrders(t, page, size)
                        .doOnNext(po -> {
                            if (po.orders() != null) {
                                po.orders().forEach(o -> System.out.println(o.displayCode() + " | " + o.statusDisplay()
                                        + " | items " + o.pendingItemCount() + " | qty " + o.pendingTotalQuantity()
                                        + " | " + (o.total()!=null?o.total().formattedValue():"")));
                            }
                        }));
            }

            default -> System.out.println("Unknown command. Type 'help'.");
        }
    }

    private void withToken(java.util.function.Function<String, Mono<?>> fn) {
        tokens.getAccessToken()
                .flatMap(fn)
                .doOnError(e -> System.err.println("API error: " + e.getMessage()))
                .block();
    }

    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private String safe(String s) {
        return (s == null || s.isBlank()) ? "" : s.trim();
    }

}
