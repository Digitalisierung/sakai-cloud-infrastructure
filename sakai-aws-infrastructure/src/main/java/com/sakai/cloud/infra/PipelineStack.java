package com.sakai.cloud.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.pipelines.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class PipelineStack extends Stack {
    public PipelineStack(Construct app, String id, StackProps stackProps) {
        super(app, id, stackProps);

        BucketProps artBucketProps = BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .versioned(false)
                .enforceSsl(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .expiration(Duration.days(10))
                        .build()))
                .build();

        Bucket artBucket = new Bucket(this, "ArtifactBucketId", artBucketProps);

        ConnectionSourceOptions conSourceOptions = ConnectionSourceOptions.builder()
                .connectionArn("arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b")
                .triggerOnPush(true)
                .build();

        CodePipelineSource pipelineSource = CodePipelineSource.connection("Digitalisierung/sakai-cloud-infrastructure", "develop", conSourceOptions);

        ShellStepProps shellStepProps = ShellStepProps.builder()
                .env(Map.of())
                .input(pipelineSource)
                .primaryOutputDirectory("sakai-aws-infraructure/cdk.out")
                .installCommands(List.of("npm install -g aws-cdk", "cdk --version"))
                .commands(List.of("cd sakai-aws-infrastructure", "npm ci", "cdk synth"))
                .build();

        ShellStep shellStep = new ShellStep("ShellStepId", shellStepProps);

        CodePipelineProps codePipelineProps = CodePipelineProps.builder()
                .synth(shellStep)
                .artifactBucket(artBucket)
                .pipelineName("DEV")
                .selfMutation(true)
                .build();

        CodePipeline codePipeline = new CodePipeline(this, "BackendPipelineId", codePipelineProps);

        Environment appEnv = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();

        StageProps sakaiAppStageProps = StageProps.builder()
                .env(appEnv)
                .build();

        StageDeployment stageDeployment = codePipeline.addStage(new SakaiApplicationStage(this, "SakaiApplicationStage", sakaiAppStageProps)); // APIGateway, Lambda CDK, DynamoDB
        // codePipeline.addStage(null); // Lambda SDK or Cognito or ...
    }
}
