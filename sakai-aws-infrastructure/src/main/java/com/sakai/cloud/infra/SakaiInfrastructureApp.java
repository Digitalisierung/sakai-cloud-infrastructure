package com.sakai.cloud.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class SakaiInfrastructureApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(SakaiInfrastructureApp.class);

    public static void main(String[] args) {
        App app = new App();

        String defaultAccount = System.getenv("CDK_DEFAULT_ACCOUNT");
        String defaultRegion = System.getenv("CDK_DEFAULT_REGION");
        LOGGER.info("CDK_DEFAULT_ACCOUNT: {}", defaultAccount);
        LOGGER.info("CDK_DEFAULT_REGION: {}", defaultRegion);

        final Environment env = Environment.builder()
                .account(defaultAccount)
                .region(defaultRegion)
                .build();

        final StackProps stackProps = StackProps.builder()
                .description("SAKAI Service. Pipeline-Stack für Backend-Infrastruktur (APIGateway, Lambda, DynamoDB) für SAKAI Khachi — ein Inventory Management System.")
                .env(env)
                .build();

        final PipelineStack pipelineStack = new PipelineStack(app, "SakaiPipelineStackId", stackProps);

//        InfrastructureStack sakaiInfraStack = new InfrastructureStack(app, "SakaiInfraStackId", stackProps);
//        sakaiInfraStack.initializeStack();
        app.synth();
    }
}
