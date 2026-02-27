package com.sakai.cloud.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

class InfrastructureStackTest {
    @Test
    void test_LambdaExecRole() {
        App app = new App();
        StackProps props = StackProps.builder()
                .stackName("TestStack")
                .build();

        InfrastructureStack stack = new InfrastructureStack(app, "TestStackId", props);
        Template template = Template.fromStack(stack);

        // Anzahl Ressourcen im Template
        template.resourceCountIs("AWS::IAM::Role", 1);

        // Prüfen, dass die Lambda-Rolle die BasicExecution-Policy enthält
        template.hasResourceProperties("AWS::IAM::Role", Map.of(
                "ManagedPolicyArns", List.of(Map.of(
                        "Fn::Join", List.of("", List.of("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"))
                ))
        ));
    }

    @Test
    void test_createApiGateway() {
        App app = new App();
        StackProps stackProps = StackProps.builder()
                .stackName("TestStack")
                .build();

        InfrastructureStack stack = new InfrastructureStack(app, "TestStackId", stackProps);
        Template template = Template.fromStack(stack);

        template.resourceCountIs("AWS::ApiGateway::RestApi", 1);
    }

    @Test
    void testStack() {
        App app = new App();
        StackProps stackProps = StackProps.builder()
                .stackName("TestStack")
                .build();

        InfrastructureStack stack = new InfrastructureStack(app, "TestStackId", stackProps);
        Template template = Template.fromStack(stack);

        // Prüfen, dass genau eine DynamoDB-Tabelle definiert wurde.
        template.resourceCountIs("AWS::DynamoDB::Table", 1);

        // Prüfen der Tabelleneigenschaften.
        template.hasResourceProperties("AWS::DynamoDB::Table", Map.of(
                        "KeySchema", List.of(
                                Map.of(
                                        "AttributeName", "partitionKey",
                                        "KeyType", "HASH"
                                ),
                                Map.of(
                                        "AttributeName", "sortKey",
                                        "KeyType", "RANGE"
                                )
                        ),
                        "AttributeDefinitions", List.of(
                                Map.of(
                                        "AttributeName", "partitionKey",
                                        "AttributeType", "S"
                                ),
                                Map.of(
                                        "AttributeName", "sortKey",
                                        "AttributeType", "S"
                                )
                        ),
                        "BillingMode", "PAY_PER_REQUEST"
                )
        );
    }
}