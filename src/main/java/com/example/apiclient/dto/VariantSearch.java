package com.example.apiclient.dto;

public record VariantSearch(
        String baseProduct,
        String baseProductName,
        String packCode,
        String packSize,
        ProductImage productImage,
        String productShortName,
        String shadeCode,
        String shadeName,
        String skuCode,
        String url
) {}
