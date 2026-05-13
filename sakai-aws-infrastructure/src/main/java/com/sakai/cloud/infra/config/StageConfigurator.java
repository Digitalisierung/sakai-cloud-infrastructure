package com.sakai.cloud.infra.config;

import software.amazon.awscdk.RemovalPolicy;

import java.util.List;

/**
 * Der StageConfigurator hält alle stag-spezifischen Einstellungen für die Infrastruktur.
 * Er bietet statische Factory-Methoden, um Konfigurationen für vordefinierte Stages (Dev, Test, Prod)
 * oder für lokale Umgebungen basierend auf Git-Branches zu erstellen.
 */
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
        String cdkSynthCommand
) {
    /**
     * Erstellt einen StageConfigurator für einen der vordefinierten Stages (dev, test, prod).
     *
     * @param stage Der Name des Stages (Groß-/Kleinschreibung wird ignoriert).
     * @return Ein konfigurierter StageConfigurator.
     * @throws IllegalArgumentException Wenn der übergebene Stage-Name ungültig ist.
     */
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
                    "cdk synth -c stage=Dev"
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
                    "cdk synth -c stage=Test"
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
                    "cdk synth -c stage=Prod"
            );
            case "local-dev" -> new StageConfigurator(
                    "local-dev",
                    stage, // ist Branch
                    System.getenv("CONNECTION_ARN_SANDBOX_ACCOUNT"),
                    RemovalPolicy.DESTROY,
                    false,
                    true,
                    true,
                    30,
                    1024,
                    "DEBUG",
                    List.of("*"),
                    "cdk synth -c branch=" + stage
            );
            default -> throw new IllegalArgumentException("Invalid stage: " + stage);
        };
    }

    /**
     * Erstellt einen StageConfigurator für die lokale Entwicklung basierend auf einem Branch-Namen.
     * Verwendet standardmäßig Sandbox-Einstellungen und Zerstörungsrichtlinien.
     *
     * @param branch Der Name des Git-Branches.
     * @return Ein konfigurierter StageConfigurator für die lokale Entwicklung.
     * @throws IllegalArgumentException Wenn der Branch-Name null oder leer ist.
     */
    public static StageConfigurator __fromLocal(String branch) {
        if (branch == null || branch.isBlank()) throw new IllegalArgumentException("Invalid branch name: " + branch);

        return new StageConfigurator(
                "local-dev",
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
                "cdk synth -c branch=" + branch
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
