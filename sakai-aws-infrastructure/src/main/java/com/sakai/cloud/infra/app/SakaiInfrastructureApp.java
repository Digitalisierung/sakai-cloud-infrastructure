package com.sakai.cloud.infra.app;

import com.sakai.cloud.infra.config.StageConfigurator;
import com.sakai.cloud.infra.stack.InfrastructurePipelineStack;
import com.sakai.cloud.infra.stack.LambdaDeployPipelineStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * Der Haupteinstiegspunkt für die Sakai Cloud Infrastructure CDK App.
 * Diese Klasse konfiguriert und initialisiert die verschiedenen Stacks für die Infrastruktur
 * und die Lambda-Deployment-Pipelines basierend auf dem aktuellen Stage (z. B. Dev, Test, Prod).
 */
public class SakaiInfrastructureApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(SakaiInfrastructureApp.class);

    /**
     * Die main-Methode, die die CDK-App startet.
     * Sie ermittelt die Umgebungsvariablen für Account und Region, bestimmt den Stage
     * und initialisiert die Pipelines für Lambda-Deployments sowie die AWS-Infrastruktur.
     *
     * @param args Kommandozeilenargumente
     */
    public static void main(String[] args) {
        App app = new App();

        String defaultAccount = System.getenv("CDK_DEFAULT_ACCOUNT");
        String defaultRegion = System.getenv("CDK_DEFAULT_REGION");
        LOGGER.info("CDK_DEFAULT_ACCOUNT: {}", defaultAccount);
        LOGGER.info("CDK_DEFAULT_REGION: {}", defaultRegion);

        // Stage Configurator
        String stageName = (String) app.getNode().tryGetContext("stage");
        if (stageName == null || stageName.isBlank()) stageName = System.getenv("SAKAI_PROJECT_STAGE");
        if (stageName == null || stageName.isBlank()) stageName = "local-env";

        LOGGER.info("SAKAI_PROJECT_STAGE={}", stageName);

        StageConfigurator stageConfig;
        try {
            stageConfig = StageConfigurator.fromStage(stageName);
        } catch (IllegalArgumentException e) {
            LOGGER.error(e.getMessage(), e);
            String branch = (String) app.getNode().tryGetContext("branch");
            stageConfig = StageConfigurator.fromLocal(branch);
        }

        final Environment env = Environment.builder()
                .account(defaultAccount)
                .region(defaultRegion)
                .build();

        // Lambda Deploy
        final StackProps backendServiceStackProps = StackProps.builder()
                .description("SAKAI Service. Pipeline fur Lambda Deploy — Inventory Management System.")
                .env(env)
                .build();

        final LambdaDeployPipelineStack lambdaDeployPipelineStack = new LambdaDeployPipelineStack(app, "SakaiLambdaDeployPipelineStackId", backendServiceStackProps, stageConfig);
        lambdaDeployPipelineStack.initializeStack();

        // AWS Infrastruktur
        final StackProps infraStackProps = StackProps.builder()
                .description("SAKAI Infrastructure. Pipeline fur AWS Infrastruktur — Inventory Management System.")
                .env(env)
                .build();

        final InfrastructurePipelineStack infrastructurePipelineStack = new InfrastructurePipelineStack(app, "SakaiAwsInfrastructurePipelineStackId", infraStackProps, stageConfig);
        infrastructurePipelineStack.initializeStack();

        app.synth();
    }
}
