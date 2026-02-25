package com.sakai.cloud.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

public class SakaiInfrastructureApp {
    public static void main(String[] args) {
        App app = new App();

        String account = System.getenv("CDK_DEFAULT_ACCOUNT");
        String region = System.getenv("CDK_DEFAULT_REGION");
        Environment env = Environment.builder()
                .account(account)
                .region(region)
                .build();

        StackProps stackProps = StackProps.builder()
                .env(env)
                .build();

        String context = (String) app.getNode().tryGetContext("stage");
        if (context == null) {
            context = "dev";
        }

        Stack sakaiInfraStack = new Stack(app, "SakaiInfraStack-" + context, stackProps);
        app.synth();
    }
}
