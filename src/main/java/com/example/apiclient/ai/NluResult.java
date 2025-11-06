package com.example.apiclient.ai;
public record NluResult(Intent intent, String category, String code, Integer page, Integer size, Integer qty, Integer entry) {}