package com.sakai.cloud.youtube;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.apigateway.LambdaRestApiProps;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

import java.util.Map;

public class LambdaStack extends Stack {
    public LambdaStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        FunctionProps funcProps = FunctionProps.builder()
                .architecture(Architecture.X86_64)
                .memorySize(1024)
                .environment(Map.of("DB_TABLE", "ARTICLES"))
                .handler("com.sakai.cloud.youtube.GetArticlesHandler::handleRequest")
                .runtime(Runtime.JAVA_21)
                .tracing(Tracing.ACTIVE) // X-Ray tracing aktivieren.
                .build();

        Function getArticlesFunction = new Function(this, "GetArticlesFunctionId", funcProps);

        LambdaRestApiProps restApiProps = LambdaRestApiProps.builder()
                .handler(getArticlesFunction)
                .proxy(false)
                .build();

        LambdaRestApi api = new LambdaRestApi(this, "Endpoint", restApiProps);

        api.getRoot()
                .addResource("articles")
                .addMethod("GET");
    }
}
