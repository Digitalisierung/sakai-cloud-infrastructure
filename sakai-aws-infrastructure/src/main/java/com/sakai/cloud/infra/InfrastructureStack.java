package com.sakai.cloud.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class InfrastructureStack extends Stack {
    public InfrastructureStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
    }
}
