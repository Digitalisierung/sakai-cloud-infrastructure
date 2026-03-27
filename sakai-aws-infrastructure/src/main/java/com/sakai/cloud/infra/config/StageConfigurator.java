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
                    "dev",
                    RemovalPolicy.DESTROY,
                    false,
                    true,
                    30,
                    1024,
                    "INFO",
                    List.of("*")
            );
            case "test", "Test", "TEST" -> new StageConfigurator(
                    "test",
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
