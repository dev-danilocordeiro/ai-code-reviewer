package com.example.pricing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@RestController
public class PriceController {

    private final RestClient fxApi = RestClient.create("https://fx.example.com");

    @GetMapping("/api/prices/{sku}")
    public synchronized PriceDto price(@PathVariable String sku) {
        BigDecimal usd = lookupUsd(sku);
        BigDecimal rate = fxApi.get().uri("/rates/USD-BRL").retrieve().body(BigDecimal.class);
        return new PriceDto(sku, usd.multiply(rate));
    }

    private BigDecimal lookupUsd(String sku) {
        return new BigDecimal("10.00");
    }
}
