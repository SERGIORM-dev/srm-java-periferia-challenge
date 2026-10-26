package com.periferia.pricing.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public final class WireMockTestContainers {

    private WireMockTestContainers() {
    }

    public static GenericContainer<?> provider(String name) {
        return new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2"))
                .withExposedPorts(8080)
                .withCommand("--global-response-templating")
                .withCopyFileToContainer(MountableFile.forClasspathResource("providers/" + name),
                        "/home/wiremock")
                .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200));
    }

    public static String baseUrl(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(8080);
    }
}
