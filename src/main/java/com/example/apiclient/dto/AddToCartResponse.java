package com.example.apiclient.dto;
public record AddToCartResponse(CartEntry entry, Integer quantity, Integer quantityAdded, String statusCode) {}
