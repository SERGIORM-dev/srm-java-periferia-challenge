package com.periferia.pricing.productrule.repository;

import com.periferia.pricing.productrule.model.ProductRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRuleRepository extends JpaRepository<ProductRule, Long> {

    Optional<ProductRule> findBySku(String sku);

    boolean existsBySku(String sku);
}
