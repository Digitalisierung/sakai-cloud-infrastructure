package com.sakai.cloud.junie;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.amazon.jsii.JsiiObjectRef;
import software.constructs.Construct;

public class CICDStage extends Stage {

    public CICDStage(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);
    }

    public CICDStage(@NotNull Construct scope, @NotNull String id, @Nullable StageProps props) {
        super(scope, id, props);
    }

    protected CICDStage(InitializationMode initializationMode) {
        super(initializationMode);
    }

    protected CICDStage(JsiiObjectRef objRef) {
        super(objRef);
    }
}
