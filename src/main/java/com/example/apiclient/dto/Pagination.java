package com.example.apiclient.dto;
public record Pagination(Integer currentPage, Integer pageSize, Integer totalPages, Integer totalResults,
                         Integer count, Boolean hasNext, Boolean hasPrevious, Integer page, Integer totalCount) {}
