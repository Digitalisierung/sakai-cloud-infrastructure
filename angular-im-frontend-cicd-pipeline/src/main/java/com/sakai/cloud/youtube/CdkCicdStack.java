package com.sakai.cloud.youtube;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.pipelines.*;
import software.constructs.Construct;

import java.util.List;

public class CdkCicdStack extends Stack {
    public CdkCicdStack(Construct scope, String id, StackProps stackProps){
        super(scope, id, stackProps);

        ShellStepProps shellStepProps = ShellStepProps.builder()
                .input(CodePipelineSource.gitHub("Digitalisierung/sakai-cloud-infrastructure", "learn-cloud-stage"))
                .commands(List.of("npm install -g aws-cdk", "cdk synth"))
                .build();

        ShellStep synthShellStep = new ShellStep("SynthShellStep", shellStepProps);

        CodePipelineProps codePipeLineProps = CodePipelineProps.builder()
                .pipelineName("MyCodePipeLine")
                .synth(synthShellStep)
                .build();

        new CodePipeline(this, "CodePipeLineId", codePipeLineProps);
    }
}
