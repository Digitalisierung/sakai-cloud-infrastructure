package com.sakai.cloud.infra;

import com.sakai.cloud.infra.stack.SakaiServiceStack;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

class SakaiServiceStackTest {
    @Test
    void test_LambdaExecRole() {
        App app = App.Builder.create()
                .postCliContext(Map.of(
                        "stage", "TEST",
                        "artifactBucketName", "test-bucket-name",
                        "artifactObjectKey", "test-object-key"
                ))
                .build();

        StackProps props = StackProps.builder()
                .stackName("TestStack")
                .build();

        SakaiServiceStack stack = new SakaiServiceStack(app, "TestStackId", props);
        stack.initializeStack();

        Template template = Template.fromStack(stack);

        // Anzahl Ressourcen im Template
        template.resourceCountIs("AWS::IAM::Role", 2);

        // Prüfen, dass die Lambda-Rolle die BasicExecution-Policy enthält
        template.hasResourceProperties("AWS::IAM::Role", Map.of(
                "ManagedPolicyArns", List.of(Map.of(
                        "Fn::Join", List.of("", List.of(
                                "arn:",
                                Map.of("Ref", "AWS::Partition"),
                                ":iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
                        ))
                ))
        ));
    }

    @Test
    void test_createApiGateway() {
        App app = App.Builder.create()
                .postCliContext(Map.of(
                        "stage", "TEST",
                        "artifactBucketName", "test-bucket-name",
                        "artifactObjectKey", "test-object-key"))
                .build();

        StackProps stackProps = StackProps.builder()
                .stackName("TestStack")
                .build();

        SakaiServiceStack stack = new SakaiServiceStack(app, "TestStackId", stackProps);
        stack.initializeStack();

        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::ApiGateway::RestApi", 1);
    }

    @Test
    void testStack() {
        App app = App.Builder.create()
                .postCliContext(Map.of(
                        "stage", "TEST",
                        "artifactBucketName", "test-bucket-name",
                        "artifactObjectKey", "test-object-key"))
                .build();

        StackProps stackProps = StackProps.builder()
                .stackName("TestStack")
                .build();

        SakaiServiceStack stack = new SakaiServiceStack(app, "TestStackId", stackProps);
        stack.initializeStack();

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