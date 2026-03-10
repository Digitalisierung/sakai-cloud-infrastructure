package com.sakai.cloud.infra;

import software.amazon.awscdk.RemovalPolicy;

import java.util.List;

public record StageConfig(
        String stageName,
        RemovalPolicy dynamoDbRemovalPolicy,
        Boolean dynamoDbPitrEnabled,
        Boolean apiGatewayDataTraceEnabled,
        Integer lambdaTimeout,
        Integer lambdaMemorySize,
        String logLevel,
        List<String> corsAllowedOrigins
) {
    public static StageConfig fromStage(String stage) {
        return switch (stage) {
            case "dev", "Dev", "DEV" -> new StageConfig(
                    "dev",
                    RemovalPolicy.DESTROY,
                    false,
                    true,
                    30,
                    1024,
                    "INFO",
                    List.of("*")
            );
            case "test", "Test", "TEST" -> new StageConfig(
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
