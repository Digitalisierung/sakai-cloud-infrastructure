package com.sakai.cloud.infra.config;

import software.amazon.awscdk.RemovalPolicy;

import java.util.List;

public record StageConfigurator(
        String stageName,
        String branch,
        String connectionArn,
        RemovalPolicy dynamoDbRemovalPolicy,
        Boolean dynamoDbPitrEnabled,
        Boolean apiGatewayDataTraceEnabled,
        Integer lambdaTimeout,
        Integer lambdaMemorySize,
        String logLevel,
        List<String> corsAllowedOrigins,
        String cdkSynthCommand
) {
    public static StageConfigurator fromStage(String stage) {
        return switch (stage) {
            case "dev", "Dev", "DEV" -> new StageConfigurator(
                    "Dev",
                    "develop",
                    System.getenv("CONNECTION_ARN_DEV_ACCOUNT"),
                    RemovalPolicy.DESTROY,
                    false,
                    true,
                    30,
                    1024,
                    "DEBUG",
                    List.of("*"),
                    "cdk synth -c stage=Dev"
            );
            case "test", "Test", "TEST" -> new StageConfigurator(
                    "Test",
                    "not-defined",
                    System.getenv("CONNECTION_ARN_TEST_ACCOUNT"),
                    RemovalPolicy.RETAIN,
                    true,
                    false,
                    30,
                    1024,
                    "INFO",
                    List.of("*"),
                    "cdk synth -c stage=Test"
            );
            case "prod", "Prod", "PROD" -> new StageConfigurator(
                    "Prod",
                    "main",
                    System.getenv("CONNECTION_ARN_PROD_ACCOUNT"),
                    RemovalPolicy.RETAIN,
                    true,
                    false,
                    30,
                    1024,
                    "ERROR",
                    List.of("*"),
                    "cdk synth -c stage=Prod"
            );
            default -> throw new IllegalArgumentException("Invalid stage: " + stage);
        };
    }

    public static StageConfigurator fromLocal(String branch) {
        if (branch == null || branch.isBlank()) throw new IllegalArgumentException("Invalid branch name: " + branch);

        return new StageConfigurator(
                "Dev",
                branch,
                System.getenv("CONNECTION_ARN_SANDBOX_ACCOUNT"),
                RemovalPolicy.DESTROY,
                false,
                true,
                30,
                1024,
                "DEBUG",
                List.of("*"),
                "cdk synth -c branch=" + branch
        );
    }
}
