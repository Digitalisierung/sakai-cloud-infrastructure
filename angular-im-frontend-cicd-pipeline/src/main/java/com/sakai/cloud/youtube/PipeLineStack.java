package com.sakai.cloud.youtube;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.StageProps;
import software.amazon.awscdk.pipelines.*;
import software.constructs.Construct;

import java.util.List;

public class PipeLineStack extends Stack {
    public PipeLineStack(Construct scope, String id, StackProps stackProps){
        super(scope, id, stackProps);

        ShellStepProps shellStepProps = ShellStepProps.builder()
                .input(CodePipelineSource.gitHub("Digitalisierung/sakai-cloud-infrastructure", "develop"))
                .commands(List.of("mvn clean package -Dmaven.test.skip=true", "cdk synth"))
                .build();

        ShellStep shellStep = new ShellStep("id", shellStepProps);

        CodePipelineProps pipeLineProps = CodePipelineProps.builder()
                .synth(shellStep) // PipeLine Producer.
                .build();

        // create pipeline
        CodePipeline codePipeLine = new CodePipeline(this, "AwesomePipeline", pipeLineProps);

        StageProps stageProps = StageProps.builder().stageName("testing").build();

        // add pipeline-stage to pipeline
        codePipeLine.addStage(new PipelineStage(this, "Testing", stageProps));
    }
}
