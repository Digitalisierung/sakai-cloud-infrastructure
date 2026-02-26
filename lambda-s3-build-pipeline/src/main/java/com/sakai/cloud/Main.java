package com.sakai.cloud;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class Main {
    public static void main(String[] args) {
        App app = new App();
        Environment stackEnv = Environment.builder()
                .region("eu-central-1")
                .account("315735600242")
                .build();

        StackProps props = StackProps.builder()
                .env(stackEnv)
                .description("Build Pipeline for Lambda Functions.")
                .stackName("lambda-s3-build-pipeline")
                .build();

        new PipelineStack(app, "lambda-s3-build-pipeline-id", props);

        app.synth();
    }
}