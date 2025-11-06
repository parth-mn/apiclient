package com.example.apiclient.dto;
public record PendingOrder(String displayCode, String statusDisplay, Integer pendingItemCount,
                           Integer pendingTotalQuantity, String placed, Price total) {}
