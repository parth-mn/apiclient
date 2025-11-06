package com.example.apiclient.dto;
public record CartEntry(Integer entryNumber, Integer quantity, Price basePrice, Price totalPrice, CartProduct product) {}
