package com.example.apiclient.dto;
import java.util.List;
public record CategoriesResponse(List<CustomerCategory> customerCategories, Integer totalCategoriesCount) {}
