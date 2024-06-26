package com.colak.springwebclienttutorial.springretry;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import wiremock.org.eclipse.jetty.http.HttpStatus;

// Instead of using Reactor retry we can also use Spring Boot Retry
// See https://medium.com/@dixitsatish34/system-resiliencey-webclient-retry-in-spring-boot-0729085c7500
// Wiremock returns FORBIDDEN_403 and SpringRetry retries

// No need to load entire application context with @SpringBootTest
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@ExtendWith(WireMockExtension.class)
@WireMockTest
@Slf4j
class SpringRetryTest {
    @Autowired
    private RetryTemplate retryTemplate;

    // The class needs to be "static class"
    @Configuration
    @EnableRetry
    static class RetryConfig {
        @Bean
        public RetryTemplate retryTemplate() {
            RetryTemplate retryTemplate = new RetryTemplate();

            FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
            fixedBackOffPolicy.setBackOffPeriod(500); // milliseconds
            retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
            retryPolicy.setMaxAttempts(3);
            retryTemplate.setRetryPolicy(retryPolicy);

            return retryTemplate;
        }
    }

    @Test
    void testSpringRetryTemplate(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        // Wiremock setup
        ResponseDefinitionBuilder responseDefinitionBuilder = WireMock.aResponse()
                .withStatus(HttpStatus.FORBIDDEN_403).withBody("Forbidden, WireMock!");
        WireMock.stubFor(WireMock.get("/").willReturn(responseDefinitionBuilder));

        // Webclient setup
        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        WebClient webClient = WebClient.create(baseUrl);

        String execute = retryTemplate.execute(
                (RetryCallback<String, Exception>) _ -> getRequest(webClient),
                _ -> this.handleRetryExhausted());
        Assertions.assertEquals("Retry exhausted", execute);

    }

    private String getRequest(WebClient webClient) {
        log.info("Begin getRequest");
        return webClient.get()
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(String.class)
                .block();
    }

    private Mono<Throwable> handleErrorResponse(ClientResponse clientResponse) {
        // Handle specific HTTP status codes here
        HttpStatusCode httpStatusCode = clientResponse.statusCode();
        String string = STR."Retry due to error : \"\{ httpStatusCode }" ;

        throw new RuntimeException(string);
    }

    private String handleRetryExhausted() {
        log.info("Begin handleRetryExhausted");
        return "Retry exhausted";
    }
}
