package com.periferia.pricing.productrule.service;

import com.periferia.pricing.productrule.exception.DuplicateSkuException;
import com.periferia.pricing.productrule.exception.ProductRuleNotFoundException;
import com.periferia.pricing.productrule.exception.ProductRuleValidationException;
import com.periferia.pricing.productrule.event.ProductRuleChanged;
import com.periferia.pricing.productrule.model.PreferredProvider;
import com.periferia.pricing.productrule.model.ProductRule;
import com.periferia.pricing.productrule.repository.ProductRuleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ProductRuleService {

    private static final BigDecimal MAX_MARGIN = new BigDecimal("99999.99");
    private static final String SKU_UNIQUE_CONSTRAINT = "uk_product_rules_sku";
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    private final ProductRuleRepository repository;
    private final ApplicationEventPublisher events;

    public ProductRuleService(ProductRuleRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public List<ProductRule> findAll() {
        return repository.findAll(Sort.by("sku"));
    }

    public ProductRule findBySku(String sku) {
        return requireRule(normalizeSku(sku));
    }

    @Transactional
    public ProductRule create(String sku, String name, BigDecimal marginPercentage,
                              PreferredProvider preferredProvider, boolean active) {
        String normalizedSku = normalizeSku(sku);
        String normalizedName = normalizeName(name);
        BigDecimal validatedMargin = validateMargin(marginPercentage);
        requireProvider(preferredProvider);

        if (repository.existsBySku(normalizedSku)) {
            throw new DuplicateSkuException(normalizedSku);
        }

        ProductRule rule = new ProductRule(normalizedSku, normalizedName,
                validatedMargin, preferredProvider, active);
        try {
            ProductRule created = repository.saveAndFlush(rule);
            events.publishEvent(new ProductRuleChanged(normalizedSku));
            return created;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateSku(exception)) {
                throw new DuplicateSkuException(normalizedSku, exception);
            }
            throw exception;
        }
    }

    @Transactional
    public ProductRule update(String sku, String name, BigDecimal marginPercentage,
                              PreferredProvider preferredProvider, boolean active) {
        String normalizedSku = normalizeSku(sku);
        String normalizedName = normalizeName(name);
        BigDecimal validatedMargin = validateMargin(marginPercentage);
        requireProvider(preferredProvider);

        ProductRule rule = requireRule(normalizedSku);
        rule.updateDetails(normalizedName, validatedMargin, preferredProvider, active);
        ProductRule updated = repository.saveAndFlush(rule);
        events.publishEvent(new ProductRuleChanged(normalizedSku));
        return updated;
    }

    @Transactional
    public void delete(String sku) {
        String normalizedSku = normalizeSku(sku);
        repository.delete(requireRule(normalizedSku));
        events.publishEvent(new ProductRuleChanged(normalizedSku));
    }

    private ProductRule requireRule(String sku) {
        return repository.findBySku(sku)
                .orElseThrow(() -> new ProductRuleNotFoundException(sku));
    }

    private static String normalizeSku(String sku) {
        String normalized = requireText(sku, "sku", "El SKU es obligatorio.")
                .toUpperCase(Locale.ROOT);
        if (normalized.codePointCount(0, normalized.length()) > 64) {
            throw new ProductRuleValidationException("sku",
                    "El SKU no puede superar 64 caracteres.");
        }
        return normalized;
    }

    private static String normalizeName(String name) {
        String normalized = requireText(name, "name", "El nombre es obligatorio.");
        if (normalized.codePointCount(0, normalized.length()) > 150) {
            throw new ProductRuleValidationException("name",
                    "El nombre no puede superar 150 caracteres.");
        }
        return normalized;
    }

    private static String requireText(String value, String field, String message) {
        if (value == null) {
            throw new ProductRuleValidationException(field, message);
        }
        String normalized = EDGE_WHITESPACE.matcher(value).replaceAll("");
        if (normalized.isEmpty()) {
            throw new ProductRuleValidationException(field, message);
        }
        return normalized;
    }

    private static BigDecimal validateMargin(BigDecimal margin) {
        if (margin == null) {
            throw new ProductRuleValidationException("marginPercentage",
                    "El margen es obligatorio.");
        }
        if (margin.signum() < 0 || margin.compareTo(MAX_MARGIN) > 0) {
            throw new ProductRuleValidationException("marginPercentage",
                    "El margen debe estar entre 0 y 99999.99.");
        }
        if (margin.stripTrailingZeros().scale() > 2) {
            throw new ProductRuleValidationException("marginPercentage",
                    "El margen admite como máximo dos decimales.");
        }
        return margin.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void requireProvider(PreferredProvider provider) {
        if (provider == null) {
            throw new ProductRuleValidationException("preferredProvider",
                    "El proveedor preferido es obligatorio.");
        }
    }

    private static boolean isDuplicateSku(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                return SKU_UNIQUE_CONSTRAINT.equals(constraint)
                        || (constraint != null
                        && constraint.endsWith("." + SKU_UNIQUE_CONSTRAINT));
            }
        }
        return false;
    }
}
