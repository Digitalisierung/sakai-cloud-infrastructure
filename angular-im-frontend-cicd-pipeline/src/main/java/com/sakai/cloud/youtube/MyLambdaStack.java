package com.sakai.cloud.youtube;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

public class MyLambdaStack extends Stack {
    public MyLambdaStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        final FunctionProps funcProps = FunctionProps.builder()
                .timeout(Duration.minutes(5))
                .architecture(Architecture.X86_64)
                .memorySize(1024)
                .runtime(Runtime.JAVA_21)
                .handler("com.sakai.cloud.youtube.LambdaHandler::handleRequest")
                .description("My Pipeline Lambda Function for Stage Learning. For learning CI/CD pipelines")
                .build();

        final Function func = new Function(this, "MyPipelineLambdaFunctionID", funcProps);
    }
}
