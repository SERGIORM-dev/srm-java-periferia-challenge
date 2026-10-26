package com.periferia.pricing.price.controller;

import com.periferia.pricing.price.dto.PriceResponse;
import com.periferia.pricing.price.service.PriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    private final PriceService service;

    public PriceController(PriceService service) {
        this.service = service;
    }

    @GetMapping
    public PriceResponse getPrice(@RequestParam("sku") String sku) {
        return service.getPrice(sku);
    }
}
