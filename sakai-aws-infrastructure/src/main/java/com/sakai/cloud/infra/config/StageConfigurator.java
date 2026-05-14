package com.sakai.cloud.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(StageConfigurator.class);

    private static final String CONNECTION_ARN_DEV_ACCOUNT = "arn:aws:codeconnections:eu-central-1:672296383273:connection/928fe30b-f26c-4070-9ca3-31ad39780b4f";
    private static final String CONNECTION_ARN_TEST_ACCOUNT = "";
    private static final String CONNECTION_ARN_PROD_ACCOUNT = "";
    private static final String CONNECTION_ARN_SANDBOX_ACCOUNT = "arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b";

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
                    CONNECTION_ARN_DEV_ACCOUNT,
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
                    CONNECTION_ARN_TEST_ACCOUNT,
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
                    CONNECTION_ARN_PROD_ACCOUNT,
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
    public static StageConfigurator fromLocal(String branch) {
        LOGGER.info("Branch={}, Stage=local-env", branch);
        if (branch == null || branch.isBlank()) throw new IllegalArgumentException("Invalid branch name: " + branch);

        return new StageConfigurator(
                "local-env",
                branch,
                CONNECTION_ARN_SANDBOX_ACCOUNT,
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
