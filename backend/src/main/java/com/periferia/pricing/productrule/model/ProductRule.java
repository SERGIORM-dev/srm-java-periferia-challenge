package com.periferia.pricing.productrule.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_rules")
public class ProductRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "margin_percentage", nullable = false, precision = 7, scale = 2)
    private BigDecimal marginPercentage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "preferred_provider", nullable = false, length = 16)
    private PreferredProvider preferredProvider;

    @Column(nullable = false)
    private boolean active;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false,
            updatable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    protected ProductRule() {
    }

    public ProductRule(String sku, String name, BigDecimal marginPercentage,
                       PreferredProvider preferredProvider, boolean active) {
        this.sku = sku;
        this.name = name;
        this.marginPercentage = marginPercentage;
        this.preferredProvider = preferredProvider;
        this.active = active;
    }

    public void updateDetails(String name, BigDecimal marginPercentage,
                              PreferredProvider preferredProvider, boolean active) {
        this.name = name;
        this.marginPercentage = marginPercentage;
        this.preferredProvider = preferredProvider;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMarginPercentage() {
        return marginPercentage;
    }

    public PreferredProvider getPreferredProvider() {
        return preferredProvider;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
