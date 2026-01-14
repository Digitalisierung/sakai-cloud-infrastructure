package com.sakai.cloud.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class Main {
    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .region("eu-central-1")
                .account("315735600242")
                .build();

        StackProps stackProps = StackProps.builder()
                .env(env)
                .build();

        new ImFrontendCICDStack(app, "ImFrontendCICDStack", stackProps);
        app.synth();
    }
}