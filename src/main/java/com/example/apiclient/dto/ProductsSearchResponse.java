package com.example.apiclient.dto;
import java.util.List;
public record ProductsSearchResponse(List<ProductSummary> products, Pagination pagination) {}
