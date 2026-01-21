package com.sakai.cloud.infra;

import com.sakai.cloud.junie.CodeBuildStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class Main {
    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .region(System.getenv("AWS_REGION"))
                .account(System.getenv("AWS_ACCOUNT_ID"))
                .build();

        StackProps stackProps = StackProps.builder()
                .env(env)
                .build();

        new com.sakai.cloud.infra.ImFrontendCICDStackL1(app, "ImFrontendCICDStackL1", stackProps);
        app.synth();
    }
}