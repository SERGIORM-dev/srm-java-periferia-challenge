package com.periferia.pricing.support;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

public final class MySqlTestContainers {

    private MySqlTestContainers() {
    }

    public static MySQLContainer<?> pricingDatabase() {
        return new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("pricing")
                .withUsername("pricing_test")
                .withPassword("pricing_test_password")
                .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_0900_as_cs")
                .withUrlParam("connectionTimeZone", "UTC")
                .withUrlParam("forceConnectionTimeZoneToSession", "true")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("database-init/01_create_product_rules.sql"),
                        "/docker-entrypoint-initdb.d/01_create_product_rules.sql")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("database-init/02_insert_demo_products.sql"),
                        "/docker-entrypoint-initdb.d/02_insert_demo_products.sql");
    }
}
