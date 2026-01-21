package com.sakai.cloud.infra;

import com.sakai.cloud.junie.CodeBuildStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class Main {
    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .build();

        StackProps stackProps = StackProps.builder()
                .env(env)
                .build();

        new com.sakai.cloud.infra.ImFrontendCICDStack(app, "ImFrontendCICDStack", stackProps);
        app.synth();
    }
}