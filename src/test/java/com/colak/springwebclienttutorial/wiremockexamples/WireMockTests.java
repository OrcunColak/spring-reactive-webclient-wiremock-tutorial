package com.colak.springwebclienttutorial.wiremockexamples;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

// See https://medium.com/@rijogeorge7/how-to-use-wiremock-in-spring-boot-application-to-mock-external-rest-api-calls-for-testing-eed05628b30a
// Start Spring and WireMock on different ports. Access to wiremock port via environment variable
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
class WireMockTests {

    @Autowired
    private Environment environment;

    @Test
    void shouldPlaceOrder() {
        stubInventoryCall("iphone_15", 1);

        String baseUrl = "http://localhost:" + this.environment.getProperty("wiremock.server.port");
        WebClient webClient = WebClient.create(baseUrl);

        String responseBodyString = webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("api/inventory")
                        .queryParam("skuCode", "iphone_15")
                        .queryParam("quantity", "1")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(responseBodyString).isEqualTo("Order placed successfully");
    }

    public static void stubInventoryCall(String skuCode, Integer quantity) {
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/api/inventory?skuCode=" + skuCode + "&quantity=" + quantity))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("Order placed successfully")));
    }
}
