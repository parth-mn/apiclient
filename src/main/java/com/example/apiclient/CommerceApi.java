package com.example.apiclient;

import com.example.apiclient.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.apiclient.ApiProperties;

@Component
public class CommerceApi {

    private final ApiClient client;
    private final ApiProperties props;
    private HttpClient http() { return HttpClient.newHttpClient(); }
    private static final String MEDIA_BASE = "https://api.cc01erru2b-grasimind1-s1-public.model-t.cc.commerce.ondemand.com/medias/";

    public static String toFullMediaUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String tail = url.startsWith("/medias/") ? url.substring("/medias/".length())
                : url.replaceFirst("^/+", "");
        return MEDIA_BASE + tail;
    }
    public CommerceApi(ApiClient client, ApiProperties props) { this.client = client; this.props = props;}

    public Mono<CategoriesResponse> getCategories(String token) {
        return client.get("/occ/v2/cpp/categories/categoriesForCustomer?fields=DEFAULT", token, CategoriesResponse.class);
    }

    public Mono<ProductsSearchResponse> searchProductsByCategory(
            String token, String categoryCode, int page, int size, String sortCode) {

        String sort = normalizeSort(sortCode); // name→name-asc, price-desc etc.
        // OCC query: keep category inside the 'query' param; pass 'sort' separately
        String query = String.format(":relevance:allCategories:%s", categoryCode);

        String fields =
                "products(code,name,url,price(FULL),images(DEFAULT),stock(FULL))," +
                        "pagination(DEFAULT),sorts(DEFAULT),freeTextSearch,currentQuery";

        String uri = UriComponentsBuilder.fromPath("/occ/v2/cpp/products/search")
                .queryParam("fields", fields)
                .queryParam("currentPage", Math.max(0, page))
                .queryParam("pageSize", Math.max(1, size))
                .queryParam("sort", sort)
                .queryParam("query", query)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        return client.get(uri, token, ProductsSearchResponse.class);
    }

    // --- keep these private helpers inside CommerceApi ---
    private String normalizeSort(String s) {
        if (s == null || s.isBlank()) return "name-asc";
        String v = s.toLowerCase();
        return switch (v) {
            case "name" -> "name-asc";
            case "name-asc", "nameasc" -> "name-asc";
            case "name-desc", "namedesc" -> "name-desc";
            case "price", "price-asc", "priceasc" -> "price-asc";
            case "price-desc", "pricedesc" -> "price-desc";
            case "relevance", "rel" -> "relevance";
            default -> "name-asc"; // safe fallback
        };
    }

    public Mono<com.example.apiclient.dto.ProductsSearchPage> searchWoodFinishesStatic(String token) {
        // Static path from your phase-1 requirement
        String path = "/occ/v2/cpp/products/search"
                + "?currentPage=0"
                + "&fields=DEFAULT"
                + "&pageSize=20"
                + "&query=:relevance:allCategories:exterior";

        // Headers (Authorization, Distribution-channel-code, unitCode) are injected by ApiClient
        return client.get(path, token, com.example.apiclient.dto.ProductsSearchPage.class);
    }


    public Mono<ProductDetailResponse> getProductDetails(String token, String productCode) {
        String fields = "code,name,price(FULL),images(DEFAULT),variantOptions(code,name,packSize,priceData(FULL),images(DEFAULT))";
        String uri = String.format("/occ/v2/cpp/users/current/products/%s?fields=%s",
                URLEncoder.encode(productCode, StandardCharsets.UTF_8), fields);
        return client.get("/occ/v2/cpp/users/current/products/" + productCode, token, ProductDetailResponse.class);
    }

    public Mono<CartResponse> getCurrentCart(String token) {
        String fields =
                "DEFAULT," +
                        "entries(totalPrice(formattedValue),product(images(FULL),stock(FULL)),basePrice(formattedValue,value),updateable)," +
                        "totalPrice(formattedValue),totalItems,totalPriceWithTax(formattedValue)," +
                        "totalDiscounts(value,formattedValue),subTotal(formattedValue)," +
                        "deliveryItemsQuantity,deliveryCost(formattedValue),totalTax(formattedValue,value)," + // <-- fixed here
                        "pickupItemsQuantity,net,appliedVouchers,productDiscounts(formattedValue),user,saveTime,name,description";

        String uri = UriComponentsBuilder.fromPath("/occ/v2/cpp/users/current/carts/current")
                .queryParam("fields", fields)
                .toUriString();

        return client.get(uri, token, CartResponse.class); // keep GET
    }

    // CommerceApi.java
    public Mono<AddToCartResponse> addSkuToCart(String token, String skuCode, int quantity) {
        String path = "/occ/v2/cpp/orgUsers/current/carts/current/entries"; // keep orgUsers/ path

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", skuCode);
        form.add("quantity", String.valueOf(quantity)); // ✅ OCC expects form param 'quantity'

        System.out.println("[API] POST " + path + " form=" + form);

        return client.postForm(path, token, form, AddToCartResponse.class);
    }
    public String addSkuToCartSync(String token, String skuCode, int quantity) {
        try {
            String url = props.getBaseUrl() + "/occ/v2/cpp/orgUsers/current/carts/current/entries";
            String form = "code=" + skuCode + "&quantity=" + quantity;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Distribution-channel-code", props.getDistChannel())
                    .header("unit-Code", props.getUnitCode())
                    .header("Content-Type", "application/x-www-form-urlencoded") // ✅ form data
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("[ADD] HTTP " + resp.statusCode() + " body=" + resp.body());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return "OK";
            return "API error: OCC " + resp.statusCode() + " " + resp.body();
        } catch (Exception e) {
            return "API error: " + e.getMessage();
        }
    }
    /*public String addSkuToCartSync(String token, String skuCode, int quantity) {
        try {
            String url = props.getBaseUrl() + "/occ/v2/cpp/orgUsers/current/carts/current/entries";
            String json = """
        {"code":"%s","quantity":%d}
        """.formatted(skuCode, quantity); // ← only `quantity`

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("X-Distribution-Channel", props.getDistChannel())
                    .header("X-unitCode", props.getUnitCode())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            System.out.println("[ADD] HTTP " + resp.statusCode() + " body=" + resp.body());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return "OK";
            return "API error: OCC " + resp.statusCode() + " " + resp.body();
        } catch (Exception e) {
            return "API error: " + e.getMessage();
        }
    }*/



    /*public Mono<AddToCartResponse> addSkuToCart(String token, String skuCode, int quantity) {
        String path = "/occ/v2/cpp/users/current/carts/current/entries"; // swap from orgUsers → users

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", skuCode);
        form.add("quantity", String.valueOf(quantity)); // B2C standard
        form.add("qty", String.valueOf(quantity));      // some B2B/custom controllers

        // Ensure this really posts as x-www-form-urlencoded
        return client.postForm(path, token, form, AddToCartResponse.class);

    }*/
    public Mono<VariantSkuSearchResponse> searchProductBySkuOrShortName(String token, String searchCode) {
        String uri = UriComponentsBuilder
                .fromPath("/occ/v2/cpp/users/current/search/searchProductSkuShortName")
                .queryParam("fields", "DEFAULT")
                .queryParam("searchCode", searchCode)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        System.out.println("[API] GET " + uri);
        return client.get(uri, token, VariantSkuSearchResponse.class);
    }


    public Mono<UpdateEntryResponse> updateCartEntryQuantity(String token, int entryNumber, int quantity) {
        String uri = String.format("/occ/v2/cpp/orgUsers/current/carts/current/entries/%d?quantity=%d", entryNumber, quantity);
        return client.put(uri, token, UpdateEntryResponse.class);
    }

    public Mono<OrderResponse> placeOrderFromCart(String token) {
        return client.post("/occ/v2/cpp/orgUsers/current/orders?cartId=current", token, OrderResponse.class);
    }

    public Mono<HeaderInfoResponse> fetchHeaderInfo(String token) {
        return client.get("/occ/v2/cpp/users/current/accounts/headerInfo", token, HeaderInfoResponse.class);
    }

    public Mono<PendingOrdersResponse> fetchPendingOrders(String token, int page, int size) {
        String uri = UriComponentsBuilder.fromPath("/occ/v2/cpp/orgUsers/current/orders")
                .queryParam("currentPage", page)
                .queryParam("pageSize", size)
                .queryParam("sort", "date-desc")
                .queryParam("orderStatuses", "ON_HOLD")
                .queryParam("orderStatuses", "PENDING_APPROVAL")
                .queryParam("orderStatuses", "APPROVED")
                .toUriString();
        return client.get(uri, token, PendingOrdersResponse.class);
    }
    public Mono<ProductsSearchResponse> searchProductsByText(
            String token, String text, int page, int size, String sortCode) {
        String sort = (sortCode == null || sortCode.isBlank()) ? "name-asc" : sortCode;
        // Same fields as used for category browsing, so output looks identical
        String fields =
                "products(code,name,url,price(FULL),images(DEFAULT),stock(FULL))," +
                        "pagination(DEFAULT),sorts(DEFAULT),freeTextSearch,currentQuery";

        // NOTE: we pass the user's text directly as the 'query' parameter (free text)
        String uri = UriComponentsBuilder.fromPath("/occ/v2/cpp/products/search")
             //   .queryParam("fields", fields)
                .queryParam("query",text)
                .queryParam("currentPage", Math.max(0, page))   // expects 0-based here
                .queryParam("pageSize", Math.max(1, size))
                .queryParam("sort", sort)
                //.queryParam("query",text + ":allCategories:")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        System.out.println("[API] GET " + uri);
        return client.get(uri, token, ProductsSearchResponse.class);
    }


    public Mono<byte[]> fetchImageBytes(String token, String mediaPathWithContext) {
        return client.getBytes("/medias/" + mediaPathWithContext, token);
    }

}
