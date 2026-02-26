package com.sakai.cloud.awsq;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class LambdaCICDApp {
    
    public static void main(String[] args) {
        App app = new App();

        new LambdaCICDStack(app, "LambdaCICDStackAmQ", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                .build());

        app.synth();
    }
}
