package com.example.apiclient.dto;
import java.util.List;
public record ProductDetailResponse(String code, String name, Price price, List<Image> images,
                                    List<VariantOption> variantOptions, boolean variantProduct) {}
