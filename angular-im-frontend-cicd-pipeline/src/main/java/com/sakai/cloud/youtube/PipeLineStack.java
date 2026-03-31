package com.sakai.cloud.youtube;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class PipeLineStack extends Stack {
    public PipeLineStack(Construct scope, String id, StackProps stackProps){
        super(scope, id, stackProps);
    }
}
