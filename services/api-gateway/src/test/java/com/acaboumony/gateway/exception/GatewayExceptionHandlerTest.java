package com.acaboumony.gateway.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayExceptionHandlerTest {

    GatewayExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayExceptionHandler();
    }

    @Test
    void deve_retornar_401_para_TokenValidationException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());

        StepVerifier.create(
                handler.handle(exchange, new TokenValidationException("expired"))
        ).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deve_retornar_404_para_ResponseStatusException_404() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/auth/validate-token").build());

        StepVerifier.create(
                handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "No static resource"))
        ).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deve_retornar_503_para_excecao_generica() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transactions/pay").build());

        StepVerifier.create(
                handler.handle(exchange, new RuntimeException("downstream down"))
        ).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
