package com.example.apiclient.dto;
public record Price(String currencyIso, String formattedValue, String priceType, Double value) {}
