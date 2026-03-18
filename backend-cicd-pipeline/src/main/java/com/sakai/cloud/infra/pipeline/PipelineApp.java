package com.sakai.cloud.infra.pipeline;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class PipelineApp {
    public static void main(String[] args) {
        final App app = new App();
        final String defaultAccount = System.getenv("CDK_DEFAULT_ACCOUNT");
        final String defaultRegion = System.getenv("CDK_DEFAULT_REGION");

        Environment cdkEnv = Environment.builder()
                .account(defaultAccount)
                .region(defaultRegion)
                .build();

        StackProps stackProps = StackProps.builder()
                .env(cdkEnv)
                .build();

        BackendPipelineStack backendPipelineStack = new BackendPipelineStack(app, "InfrastructureStack", stackProps);
        backendPipelineStack.initialize();
        app.synth();
    }
}
