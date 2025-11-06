package com.example.apiclient.dto;
import java.util.List;
public record CartProduct(String code, String name, java.util.List<Image> images, boolean variantProduct) {}
