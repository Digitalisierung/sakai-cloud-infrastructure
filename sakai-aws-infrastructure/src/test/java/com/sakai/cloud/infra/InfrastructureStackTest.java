package com.sakai.cloud.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

import static org.junit.jupiter.api.Assertions.*;

class InfrastructureStackTest {
    @Test
    void testStack() {
        App app = new App();
        StackProps stackProps = StackProps.builder()
                .stackName("TestStack")
                .build();

        InfrastructureStack stack = new InfrastructureStack(app, "TestStackId", stackProps);
        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::DynamoDB::Table", 0);
    }
}