package com.example.apiclient.dto;

import java.util.List;

public record ProductListItem(
        String baseProduct,
        List<String> categoryUrlName,
        String code,
        boolean configurable,
        boolean favouriteProduct,
        String firstVariantImage,
        List<Image> images,
        boolean isBundleAvailableFlag,
        String name,
        String premiumness,
        Price price,           // present for some items
        Object priceRange,     // structure not needed now; keep as Object
        Stock stock,
        String subBrandName,
        String url,
        boolean variantProduct,
        boolean volumePricesFlag
) {}
