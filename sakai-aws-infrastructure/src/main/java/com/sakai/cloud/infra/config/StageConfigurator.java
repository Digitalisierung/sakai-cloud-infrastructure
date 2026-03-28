package com.sakai.cloud.infra.config;

import software.amazon.awscdk.RemovalPolicy;

import java.util.List;

public record StageConfigurator(
        String stageName,
        RemovalPolicy dynamoDbRemovalPolicy,
        Boolean dynamoDbPitrEnabled,
        Boolean apiGatewayDataTraceEnabled,
        Integer lambdaTimeout,
        Integer lambdaMemorySize,
        String logLevel,
        List<String> corsAllowedOrigins
) {
    public static StageConfigurator fromStage(String stage) {
        return switch (stage) {
            case "dev", "Dev", "DEV" -> new StageConfigurator(
                    "Dev",
                    RemovalPolicy.DESTROY,
                    false,
                    true,
                    30,
                    1024,
                    "DEBUG",
                    List.of("*")
            );
            case "test", "Test", "TEST" -> new StageConfigurator(
                    "Test",
                    RemovalPolicy.RETAIN,
                    true,
                    false,
                    30,
                    1024,
                    "INFO",
                    List.of("*")
            );
            case "prod", "Prod", "PROD" -> new StageConfigurator(
                    "Prod",
                    RemovalPolicy.RETAIN,
                    true,
                    false,
                    30,
                    1024,
                    "ERROR",
                    List.of("*")
            );
            default -> throw new IllegalArgumentException("Invalid stage: " + stage);
        };
    }
}
