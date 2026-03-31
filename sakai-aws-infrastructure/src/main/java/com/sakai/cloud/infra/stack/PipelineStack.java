package com.sakai.cloud.infra.stack;

import com.sakai.cloud.infra.config.StageConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.*;
import software.amazon.awscdk.pipelines.*;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class PipelineStack extends Stack {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStack.class);

    //private static final String CONNECTION_ARN = "arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b";
    private static final String REPO_STRING = "Digitalisierung/sakai-cloud-infrastructure";
    //private static final String BRANCH = "develop";

    public PipelineStack(Construct app, String id, StackProps stackProps) {
        super(app, id, stackProps);

        final Environment env = stackProps.getEnv();

        if (env == null || env.getAccount() == null || env.getRegion() == null) {
            throw new RuntimeException("Missing Environment in stackProps.");
        }
        LOGGER.info("Env::getAccount() {}", env.getAccount());
        LOGGER.info("Env::getRegion() {}", env.getRegion());

        final Bucket artBucket = createArtifactBucket();

        final StageConfigurator stageConfig = initializeStageConfiguration();
        LOGGER.info("Stage name: {}, branch: {}", stageConfig.stageName(), stageConfig.branch());
        LOGGER.info("Connection arn: {}", stageConfig.connectionArn());

        final CodePipeline codePipeline = createCodePipeline(artBucket, stageConfig);

        final SakaiApplicationStage sakaiAppStage = createSakaiAppStage(env, stageConfig);

        // APIGateway, Lambda CDK, DynamoDB.
        final StageDeployment stageDeployment = codePipeline.addStage(sakaiAppStage);

        // Pipeline braucht UseConnection-Berechtigung
        codePipeline.buildPipeline(); // Muss vor getRole() aufgerufen werden!
        codePipeline.getPipeline().getRole().addToPrincipalPolicy(
                PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(List.of("codestar-connections:UseConnection"))
                        .resources(List.of(stageConfig.connectionArn()))
                        .build()
        );


        // Lambda SDK or Cognito or ...
        // codePipeline.addStage(null);
    }

    private StageConfigurator initializeStageConfiguration() {
        String stageName = (String) this.getNode().tryGetContext("stage");
        if (stageName == null || stageName.isBlank()) stageName = System.getenv("SAKAI_PROJECT_STAGE");
        if (stageName == null || stageName.isBlank()) stageName = "local-env";

        try {
            return StageConfigurator.fromStage(stageName);
        } catch (IllegalArgumentException e) {
            LOGGER.error(e.getMessage(), e);
            String branch = (String) this.getNode().tryGetContext("branch");
            return StageConfigurator.fromLocal(branch);
        }
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

    private CodePipeline createCodePipeline(Bucket artifactBucket, StageConfigurator stageConfig) {
        final ConnectionSourceOptions conSourceOptions = ConnectionSourceOptions.builder()
                .connectionArn(stageConfig.connectionArn())
                .triggerOnPush(true)
                .build();

        final CodePipelineSource pipelineSource = CodePipelineSource.connection(REPO_STRING, stageConfig.branch(), conSourceOptions);

        final ShellStepProps shellStepProps = ShellStepProps.builder()
                .input(pipelineSource)
                .installCommands(List.of(
                        "npm install -g aws-cdk",
                        "cdk --version",
                        "yum install -y java-21-amazon-corretto-devel",
                        "export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto",
                        "export PATH=$JAVA_HOME/bin:$PATH",
                        "java -version"
                ))
                .commands(List.of(
                        "export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto",
                        "export PATH=$JAVA_HOME/bin:$PATH",
                        "cd sakai-aws-infrastructure",
                        "cdk synth"
                ))
                .primaryOutputDirectory("sakai-aws-infrastructure/cdk.out")
                .env(Map.of(
                        "STAGE_NAME", stageConfig.stageName(),
                        "ARTIFACT_BUCKET", "aws-sakai-bucket-dev",
                        "OBJECT_KEY", "asset-service-1.0-SNAPSHOT.jar"
                ))
                .build();

        final ShellStep shellStep = new ShellStep("ShellStepId", shellStepProps);

        final CodeBuildOptions codeBuildOptions = getCodeBuildOptions();

        final CodePipelineProps codePipelineProps = CodePipelineProps.builder()
                .synth(shellStep)
                .artifactBucket(artifactBucket)
                .pipelineName(stageConfig.stageName())
                .selfMutation(true)
                .codeBuildDefaults(codeBuildOptions)
                .synthCodeBuildDefaults(codeBuildOptions)
                .build();

        return new CodePipeline(this, "BackendPipelineId", codePipelineProps);
    }

    private SakaiApplicationStage createSakaiAppStage(Environment appEnvironment, StageConfigurator stageConfig) {

        final StageProps sakaiAppStageProps = StageProps.builder()
                .stageName(stageConfig.stageName())
                .env(appEnvironment)
                .build();

        return new SakaiApplicationStage(this, "SakaiApplicationStage", sakaiAppStageProps, stageConfig);
    }

    private CodeBuildOptions getCodeBuildOptions() {
        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.STANDARD_7_0)
                .build();

        return CodeBuildOptions.builder()
                .buildEnvironment(buildEnvironment)
                .build();
    }
}
