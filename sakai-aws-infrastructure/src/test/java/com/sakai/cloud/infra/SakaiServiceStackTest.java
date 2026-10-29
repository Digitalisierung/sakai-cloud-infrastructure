package com.sakai.cloud.infra;

import com.sakai.cloud.infra.config.StageConfigurator;
import com.sakai.cloud.infra.stack.SakaiServiceStack;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

/**
 * java.lang.RuntimeException: AssertionError: Expected 2 resources of type AWS::IAM::Role but found 3
 *
 * @jsii/kernel.RuntimeError: AssertionError: Expected 2 resources of type AWS::IAM::Role but found 3
 * at Kernel._Kernel_ensureSync (/tmp/jsii-java-runtime3564890119325800135/lib/program.js:1:71414)
 * at Kernel.invoke (/tmp/jsii-java-runtime3564890119325800135/lib/program.js:1:55595)
 * at KernelHost.processRequest (/tmp/jsii-java-runtime3564890119325800135/lib/program.js:1:228006)
 * at KernelHost.run (/tmp/jsii-java-runtime3564890119325800135/lib/program.js:1:226990)
 * at Immediate._onImmediate (/tmp/jsii-java-runtime3564890119325800135/lib/program.js:1:227034)
 * at process.processImmediate (node:internal/timers:504:21)
 */
class SakaiServiceStackTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("SakaiServiceStack");

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

        LOGGER.info("Stage {}", app.getStageName()); // TODO: Warum null hier?
        StageConfigurator stageConfig = StageConfigurator.fromStage("test");
        SakaiServiceStack stack = new SakaiServiceStack(app, "TestStackId", props, stageConfig);
        stack.initializeStack();

        Template template = Template.fromStack(stack);

        // Anzahl Ressourcen im Template
        template.resourceCountIs("AWS::IAM::Role", 3);

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

        LOGGER.info("Stage {}", app.getStageName()); // TODO: Warum null hier??
        StageConfigurator stageConfig = StageConfigurator.fromStage("test");
        SakaiServiceStack stack = new SakaiServiceStack(app, "TestStackId", stackProps, stageConfig);
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

        LOGGER.info("Stage {}", app.getStageName()); // TODO: Warum null hier??
        StageConfigurator stageConfig = StageConfigurator.fromStage("test");
        SakaiServiceStack stack = new SakaiServiceStack(app, "TestStackId", stackProps, stageConfig);
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
                "AttributeDefinitions", Match.arrayWith(List.of(
                                Map.of(
                                        "AttributeName", "partitionKey",
                                        "AttributeType", "S"
                                ),
                                Map.of(
                                        "AttributeName", "sortKey",
                                        "AttributeType", "S"
                                )
                )),
                        "BillingMode", "PAY_PER_REQUEST"
                )
        );
    }
}