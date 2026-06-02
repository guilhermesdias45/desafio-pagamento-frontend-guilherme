package com.acaboumony.gateway.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String errorCode;
        String message;

        if (ex instanceof TokenValidationException) {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "INVALID_TOKEN";
            message = "Token inválido ou expirado";
        } else if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 404) {
            status = HttpStatus.NOT_FOUND;
            errorCode = "NOT_FOUND";
            message = "Recurso não encontrado";
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICE_UNAVAILABLE";
            message = "Serviço temporariamente indisponível. Tente novamente em instantes.";
            log.error("Unhandled gateway exception on {}: {}", exchange.getRequest().getURI(), ex.getMessage());
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"errorCode\":\"%s\",\"message\":\"%s\"}".formatted(errorCode, message);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
