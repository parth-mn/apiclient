package com.example.apiclient.dto;

import java.util.List;

public record ProductsSearchPage(
        String type,
        CurrentQuery currentQuery,
        String freeTextSearch,
        Pagination pagination,
        List<ProductListItem> products,
        List<SortOption> sorts
) {}
