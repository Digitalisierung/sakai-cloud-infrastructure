package com.sakai.cloud.infra.config;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;

import java.util.List;

public record StageConfigurator(
        String stageName,
        String branch,
        String connectionArn,
        RemovalPolicy removalPolicy,
        Boolean dynamoDbPitrEnabled,
        Boolean apiGatewayDataTraceEnabled,
        Boolean autoDeleteObjects,
        Integer lambdaTimeout,
        Integer lambdaMemorySize,
        String logLevel,
        List<String> corsAllowedOrigins,
        String cdkSynthCommand,
        CorsOptions stageOptions
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
                    true,
                    30,
                    1024,
                    "DEBUG",
                    List.of("*"),
                    "cdk synth -c stage=Dev",
                    CorsOptions.builder()
                            .allowOrigins(Cors.ALL_ORIGINS) // Später mit List.of()
                            .allowMethods(Cors.ALL_METHODS)
                            .allowHeaders(Cors.DEFAULT_HEADERS)
                            .build()
            );
            case "test", "Test", "TEST" -> new StageConfigurator(
                    "Test",
                    "not-defined",
                    System.getenv("CONNECTION_ARN_TEST_ACCOUNT"),
                    RemovalPolicy.RETAIN,
                    true,
                    false,
                    true,
                    30,
                    1024,
                    "INFO",
                    List.of("*"),
                    "cdk synth -c stage=Test",
                    CorsOptions.builder()
                            .allowOrigins(List.of("*")) // TODO: Warum? Ist das richtig? Ist es notwendig??
                            .allowMethods(Cors.ALL_METHODS)
                            .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                            .build()
            );
            case "prod", "Prod", "PROD" -> new StageConfigurator(
                    "Prod",
                    "main",
                    System.getenv("CONNECTION_ARN_PROD_ACCOUNT"),
                    RemovalPolicy.RETAIN,
                    true,
                    false,
                    false,
                    30,
                    1024,
                    "ERROR",
                    List.of("*"),
                    "cdk synth -c stage=Prod",
                    CorsOptions.builder()
                            .allowOrigins(List.of("*")) // TODO: Warum? Ist das richtig? Ist es notwendig?? (auf bekannte Domains einschränken)
                            .allowMethods(Cors.ALL_METHODS)
                            .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                            .build()
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
                true,
                30,
                1024,
                "DEBUG",
                List.of("*"),
                "cdk synth -c branch=" + branch,
                CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS) // Später mit List.of()
                        .allowMethods(Cors.ALL_METHODS)
                        .allowHeaders(Cors.DEFAULT_HEADERS)
                        .build()
        );
    }

    public boolean isLocal() {
        return !isDev() && !isProd() && !isTest();
    }

    public boolean isProd() {
        return stageName.equalsIgnoreCase("prod");
    }

    public boolean isDev() {
        return stageName.equalsIgnoreCase("dev");
    }

    public boolean isTest() {
        return stageName.equalsIgnoreCase("test");
    }
}
