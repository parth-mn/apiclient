package com.example.apiclient.dto;
import java.util.List;
public record CartResponse(String code, Integer totalItems, Price subTotal, Price totalPrice, List<CartEntry> entries) {}
