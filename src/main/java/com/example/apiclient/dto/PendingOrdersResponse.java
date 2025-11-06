package com.example.apiclient.dto;
import java.util.List;
public record PendingOrdersResponse(java.util.List<PendingOrder> orders, Pagination pagination) {}
