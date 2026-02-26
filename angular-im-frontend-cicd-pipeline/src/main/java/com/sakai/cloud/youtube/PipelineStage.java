package com.sakai.cloud.youtube;

import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;

public class PipelineStage extends Stage {
    public PipelineStage(Construct scope, String id, StageProps props) {
        super(scope, id, props);
    }
}
