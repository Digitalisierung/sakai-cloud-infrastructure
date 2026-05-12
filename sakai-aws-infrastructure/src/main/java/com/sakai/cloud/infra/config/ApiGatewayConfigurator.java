package com.sakai.cloud.infra.config;

public record ApiGatewayConfigurator(
        String restApiName,
        String description,
        StageConfigurator stageConfig
) {
}
