package com.periferia.pricing.provider.client;

import com.periferia.pricing.provider.exception.ProviderException;
import com.periferia.pricing.provider.exception.ProviderException.Reason;
import com.periferia.pricing.provider.model.ProviderId;
import com.periferia.pricing.provider.model.ProviderPrice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderClientsTest {

    private static final String SKU = "GAS-EXTRA";
    private static final String URL = "http://provider.test/api/prices?sku=" + SKU;
    private static final Instant UPDATED = Instant.parse("2026-10-25T12:00:00Z");
    private static final String A_BODY = """
            {"sku":"GAS-EXTRA","basePrice":100.125,"currency":"USD",
             "lastUpdated":"2026-10-25T12:00:00Z"}
            """;
    private static final String B_BODY = """
            {"itemCode":"GAS-EXTRA","cost":100.125,"curr":"USD","timestamp":%d}
            """.formatted(UPDATED.getEpochSecond());

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void translatesBothContractsWithoutRoundingTheBasePrice(ProviderId provider) {
        Fixture fixture = fixture(provider);
        fixture.server().expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", "application/json"))
                .andRespond(withSuccess(body(provider), MediaType.APPLICATION_JSON));

        ProviderPrice price = fixture.client().fetchPrice(SKU);

        assertThat(price.sku()).isEqualTo(SKU);
        assertThat(price.basePrice()).isEqualByComparingTo("100.125");
        assertThat(price.currency()).isEqualTo("USD");
        assertThat(price.lastUpdated()).isEqualTo(UPDATED);
        assertThat(price.provider()).isEqualTo(provider);
        assertThat(fixture.client().provider()).isEqualTo(provider);
        fixture.server().verify();
    }

    @ParameterizedTest
    @MethodSource("httpFailures")
    void translatesHttpErrorsWithoutExposingTheUpstreamBody(
            ProviderId provider, int status, Reason reason) {
        Fixture fixture = fixture(provider);
        fixture.server().expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .body("private_upstream_details").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.client().fetchPrice(SKU))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getProvider()).isEqualTo(provider);
                    assertThat(exception.getReason()).isEqualTo(reason);
                    assertThat(exception.getHttpStatus()).isEqualTo(status);
                    assertThat(exception.getMessage()).doesNotContain("private_upstream_details");
                });
        fixture.server().verify();
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void translatesConnectionFailures(ProviderId provider) {
        Fixture fixture = fixture(provider);
        IOException cause = new IOException("private_network_details");
        fixture.server().expect(requestTo(URL)).andRespond(withException(cause));

        assertFailure(fixture, provider, Reason.UNAVAILABLE);
        fixture.server().verify();
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void identifiesTimeouts(ProviderId provider) {
        Fixture fixture = fixture(provider);
        fixture.server().expect(requestTo(URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertFailure(fixture, provider, Reason.TIMEOUT);
        fixture.server().verify();
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsUnusableResponses(ProviderId provider, String response) {
        Fixture fixture = fixture(provider);
        fixture.server().expect(requestTo(URL))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertFailure(fixture, provider, Reason.INVALID_RESPONSE);
        fixture.server().verify();
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void rejectsAnUnexpectedContentType(ProviderId provider) {
        Fixture fixture = fixture(provider);
        fixture.server().expect(requestTo(URL))
                .andRespond(withSuccess("<html>not a price</html>", MediaType.TEXT_HTML));

        assertFailure(fixture, provider, Reason.INVALID_RESPONSE);
        fixture.server().verify();
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void encodesTheSkuAsOneQueryParameter(ProviderId provider) {
        Fixture fixture = fixture(provider);
        String unusualSku = "GAS &EXTRA+";
        fixture.server().expect(requestTo("http://provider.test/api/prices?sku=GAS%20%26EXTRA%2B"))
                .andRespond(withSuccess(body(provider).replace(SKU, unusualSku),
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchPrice(unusualSku).sku()).isEqualTo(unusualSku);
        fixture.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsEmptySkuBeforeSendingAnyRequest(String sku) {
        Fixture fixture = fixture(ProviderId.PROVIDER_A);

        assertThatThrownBy(() -> fixture.client().fetchPrice(sku))
                .isInstanceOf(IllegalArgumentException.class);
        fixture.server().verify();
    }

    @Test
    void preservesTheUpstreamDateEvenWhenItIsOld() {
        Fixture fixture = fixture(ProviderId.PROVIDER_A);
        fixture.server().expect(requestTo(URL)).andRespond(withSuccess(
                A_BODY.replace("2026-10-25T12:00:00Z", "2000-01-01T00:00:00Z"),
                MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchPrice(SKU).lastUpdated())
                .isEqualTo(Instant.parse("2000-01-01T00:00:00Z"));
        fixture.server().verify();
    }

    private static void assertFailure(Fixture fixture, ProviderId provider, Reason reason) {
        assertThatThrownBy(() -> fixture.client().fetchPrice(SKU))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getProvider()).isEqualTo(provider);
                    assertThat(exception.getReason()).isEqualTo(reason);
                    assertThat(exception.getHttpStatus()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("private_network_details");
                });
    }

    private static Fixture fixture(ProviderId provider) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PriceProviderClient client = provider == ProviderId.PROVIDER_A
                ? new ProviderAClient(builder.build()) : new ProviderBClient(builder.build());
        return new Fixture(client, server);
    }

    private static String body(ProviderId provider) {
        return provider == ProviderId.PROVIDER_A ? A_BODY : B_BODY;
    }

    private static Stream<Arguments> httpFailures() {
        return Stream.of(ProviderId.values()).flatMap(provider -> Stream.of(
                Arguments.of(provider, 404, Reason.NOT_FOUND),
                Arguments.of(provider, 429, Reason.RATE_LIMITED),
                Arguments.of(provider, 400, Reason.HTTP_ERROR),
                Arguments.of(provider, 503, Reason.HTTP_ERROR)));
    }

    private static Stream<Arguments> invalidResponses() {
        Stream<Arguments> common = Stream.of(ProviderId.values()).flatMap(provider -> Stream.of(
                Arguments.of(provider, ""),
                Arguments.of(provider, "{"),
                Arguments.of(provider, "[]"),
                Arguments.of(provider, "{}"),
                Arguments.of(provider, body(provider).replace(SKU, "OTHER-SKU")),
                Arguments.of(provider, body(provider).replace("100.125", "-1")),
                Arguments.of(provider, body(provider).replace("100.125", "null")),
                Arguments.of(provider, body(provider).replace("USD", "NOT-A-CURRENCY"))));
        return Stream.concat(common, Stream.of(
                Arguments.of(ProviderId.PROVIDER_A,
                        A_BODY.replace("\"2026-10-25T12:00:00Z\"", "null")),
                Arguments.of(ProviderId.PROVIDER_A,
                        A_BODY.replace("2026-10-25T12:00:00Z", "invalid-date")),
                Arguments.of(ProviderId.PROVIDER_B,
                        B_BODY.replace(Long.toString(UPDATED.getEpochSecond()), "null")),
                Arguments.of(ProviderId.PROVIDER_B,
                        B_BODY.replace(Long.toString(UPDATED.getEpochSecond()),
                                Long.toString(Long.MAX_VALUE)))));
    }

    private record Fixture(PriceProviderClient client, MockRestServiceServer server) {
    }
}
