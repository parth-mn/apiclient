package com.example.apiclient.dto;
public record HeaderInfoResponse(Double availableCreditLimit, Double dueToday, Double netOutstanding,
                                 Double totalOutstanding, Double totalOverdue, Double unutilizedCredit) {}
