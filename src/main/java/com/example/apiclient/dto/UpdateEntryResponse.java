package com.example.apiclient.dto;
public record UpdateEntryResponse(CartEntry entry, Integer quantity, Integer quantityAdded, String statusCode) {}
