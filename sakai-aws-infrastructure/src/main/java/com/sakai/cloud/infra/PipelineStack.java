package com.sakai.cloud.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.*;
import software.amazon.awscdk.pipelines.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class PipelineStack extends Stack {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStack.class);

    private static final String CONNECTION_ARN = "arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b";
    private static final String BRANCH = "develop";

    public PipelineStack(Construct app, String id, StackProps stackProps) {
        super(app, id, stackProps);

        final Environment env = stackProps.getEnv();
        final String stageName = "Dev";

        if (env == null || env.getAccount() == null || env.getRegion() == null) {
            throw new RuntimeException("Missing Environment in stackProps.");
        }

        final Bucket artBucket = createArtifactBucket();

        final CodePipeline codePipeline = createCodePipeline(artBucket);

        LOGGER.info("Env::getAccount() {}", env.getAccount());
        LOGGER.info("Env::getRegion() {}", env.getRegion());

        final SakaiApplicationStage sakaiAppStage = createSakaiAppStage(env, stageName);

        // APIGateway, Lambda CDK, DynamoDB.
        final StageDeployment stageDeployment = codePipeline.addStage(sakaiAppStage);

        // Lambda SDK or Cognito or ...
        // codePipeline.addStage(null);
    }

    private Bucket createArtifactBucket() {
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

        return new Bucket(this, "ArtifactBucketId", artBucketProps);
    }

    private CodePipeline createCodePipeline(Bucket artifactBucket) {
        final ConnectionSourceOptions conSourceOptions = ConnectionSourceOptions.builder()
                .connectionArn(CONNECTION_ARN)
                .triggerOnPush(true)
                .build();

        final CodePipelineSource pipelineSource = CodePipelineSource.connection("Digitalisierung/sakai-cloud-infrastructure", BRANCH, conSourceOptions);

        final ShellStepProps shellStepProps = ShellStepProps.builder()
                .env(Map.of())
                .input(pipelineSource)
                .primaryOutputDirectory("sakai-aws-infrastructure/cdk.out")
                .installCommands(List.of("npm install -g aws-cdk", "cdk --version"))
                .commands(List.of("cd sakai-aws-infrastructure", "npm ci", "cdk synth"))
                .env(Map.of("STAGE_NAME", "Dev", "ARTIFACT_BUCKET", "aws-sakai-bucket", "OBJECT_KEY", "asset-service-1.0-SNAPSHOT.jar"))
                .build();

        final ShellStep shellStep = new ShellStep("ShellStepId", shellStepProps);

        final CodePipelineProps codePipelineProps = CodePipelineProps.builder()
                .synth(shellStep)
                .artifactBucket(artifactBucket)
                .pipelineName("DEV")
                .selfMutation(true)
                .build();

        return new CodePipeline(this, "BackendPipelineId", codePipelineProps);
    }

    private SakaiApplicationStage createSakaiAppStage(Environment appEnvironment, String name) {

        final StageProps sakaiAppStageProps = StageProps.builder()
                .stageName(name)
                .env(appEnvironment)
                .build();

        return new SakaiApplicationStage(this, "SakaiApplicationStage", sakaiAppStageProps);
    }
}
