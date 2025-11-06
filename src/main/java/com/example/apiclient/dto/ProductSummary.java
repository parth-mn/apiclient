// dto/ProductSummary.java  (update only if your current record lacks these fields)
package com.example.apiclient.dto;
import java.util.List;

public record ProductSummary(
        String code,
        String name,
        String url,
        Price price,
        List<Image> images,
        Stock stock
) {}
