package com.sakai.cloud.infra.config;

/**
 * Konfigurationsdaten für ein Amazon API Gateway.
 * Kapselt Informationen wie Name, Beschreibung und die zugehörige Stage-Konfiguration.
 */
public record ApiGatewayConfigurator(
        String restApiName,
        String description,
        StageConfigurator stageConfig
) {
}
