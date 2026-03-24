package com.sakai.cloud.youtube;

import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.StageProps;
import software.amazon.awscdk.pipelines.*;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.List;

public class CdkCicdStack extends Stack {
    public CdkCicdStack(Construct scope, String id, StackProps stackProps){
        super(scope, id, stackProps);

        final Bucket artifactBucket = new Bucket(this, "ArtifactBucketId");

        final ConnectionSourceOptions conSourceOptProps = ConnectionSourceOptions.builder()
                .connectionArn("arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b")
                .build();

        final CodePipelineSource pipelineSource = CodePipelineSource.connection("Digitalisierung/sakai-cloud-infrastructure", "develop", conSourceOptProps);

        final ShellStepProps shellStepProps = ShellStepProps.builder()
                .input(pipelineSource)
                .installCommands(List.of("npm install -g aws-cdk", "cdk --version"))
                .commands(List.of("cd sakai-aws-infrastructure", "npm ci", "cdk synth"))
                .primaryOutputDirectory("sakai-aws-infrastructure/cdk.out")
                .build();

        final ShellStep shellStep = new ShellStep("ShellStepId", shellStepProps);

        final CodePipelineProps codePipelineProps = CodePipelineProps.builder()
                .artifactBucket(artifactBucket)
                .synth(shellStep)
                .build();

        final CodePipeline codePipeline = new CodePipeline(this, "CodePipelineId", codePipelineProps);

        // MyPipelineSage enthält LambdaStack
        StageProps stageProps = StageProps.builder()
                .stageName("TEST")
                .env(Environment.builder()
                        .account(getAccount())
                        .region(getRegion())
                        .build())
                .build();

        MyPipelineStage myPipelineStage = new MyPipelineStage(this, "PipelineStageId", stageProps);

        StageDeployment stageDeployment = codePipeline.addStage(myPipelineStage);
    }
}
