package com.periferia.pricing.productrule.controller;

import com.periferia.pricing.productrule.dto.CreateProductRuleRequest;
import com.periferia.pricing.productrule.dto.ProductRuleResponse;
import com.periferia.pricing.productrule.dto.UpdateProductRuleRequest;
import com.periferia.pricing.productrule.model.ProductRule;
import com.periferia.pricing.productrule.service.ProductRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-rules")
public class ProductRuleController {

    private final ProductRuleService service;

    public ProductRuleController(ProductRuleService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductRuleResponse> create(
            @Valid @RequestBody CreateProductRuleRequest request) {
        ProductRule created = service.create(request.sku(), request.name(),
                request.marginPercentage(), request.preferredProvider(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductRuleResponse.from(created));
    }

    @GetMapping
    public List<ProductRuleResponse> findAll() {
        return service.findAll().stream().map(ProductRuleResponse::from).toList();
    }

    @PutMapping(value = "/{sku}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductRuleResponse update(@PathVariable("sku") String sku,
                                      @Valid @RequestBody UpdateProductRuleRequest request) {
        ProductRule updated = service.update(sku, request.name(), request.marginPercentage(),
                request.preferredProvider(), request.active());
        return ProductRuleResponse.from(updated);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> delete(@PathVariable("sku") String sku) {
        service.delete(sku);
        return ResponseEntity.noContent().build();
    }
}
