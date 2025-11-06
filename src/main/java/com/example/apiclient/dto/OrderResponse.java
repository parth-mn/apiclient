package com.example.apiclient.dto;
public record OrderResponse(String code, String statusDisplay, Integer totalItems, Price subTotal, Price totalPrice) {}
