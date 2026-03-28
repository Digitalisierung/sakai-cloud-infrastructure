package com.sakai.cloud.infra.config;

public record ApiGatewayConfigurator(
        String restApiName,
        String description,
        String stage
) {
}
