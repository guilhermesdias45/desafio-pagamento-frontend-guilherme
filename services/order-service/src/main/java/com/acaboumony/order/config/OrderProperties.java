package com.acaboumony.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code order.*} properties from application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "order")
public record OrderProperties(int expirationMinutes) {}
