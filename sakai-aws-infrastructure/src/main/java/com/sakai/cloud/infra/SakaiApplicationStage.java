package com.sakai.cloud.infra;

import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;

public class SakaiApplicationStage extends Stage {
    public SakaiApplicationStage(Construct scope, String id, StageProps stageProps) {
        super(scope, id, stageProps);

        SakaiServiceStack sakaiServiceStack = new SakaiServiceStack(this, "SakaiServiceStack", null);
        sakaiServiceStack.initializeStack();
    }
}
