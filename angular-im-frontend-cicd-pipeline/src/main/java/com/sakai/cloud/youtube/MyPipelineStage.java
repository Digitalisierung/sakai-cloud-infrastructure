package com.sakai.cloud.youtube;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;

public class MyPipelineStage extends Stage {
    public MyPipelineStage(Construct scope, String id, StageProps props) {
        super(scope, id, props);

        StackProps myLambdaStackProps = StackProps.builder()
                .build();

        Stack myLambdak = new MyLambdaStack(this, "LambdaStackId", myLambdaStackProps);
    }
}
