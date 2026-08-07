package com.sakai.cloud.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.PipelineProps;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildActionProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildActionType;
import software.amazon.awscdk.services.codepipeline.actions.CodeStarConnectionsSourceAction;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static software.amazon.awscdk.services.codebuild.BuildEnvironmentVariableType.PLAINTEXT;
import static software.amazon.awscdk.services.codebuild.ComputeType.SMALL;
import static software.amazon.awscdk.services.codebuild.LinuxBuildImage.AMAZON_LINUX_2_5;

public class ImFrontendCICDStackL2 extends Stack {
    private final String connectionId;
    private final String connectionArn;
    private final String branchName;
    private final String repoOwner;
    private final String repoName;

    public ImFrontendCICDStackL2(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        // connectionId = "0eb84fa4-1c3f-4b0b-8434-a3f94184c621"; // main (root) account
        connectionId = "5b463871-e022-42cc-831b-be409b55e94b";
        connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/" + connectionId;

        repoOwner = "Digitalisierung";
        repoName = "im-frontend";
        branchName = "be-4-backend-anbindung-furr-products";

        Bucket artifactBucket = createArtifactBucket();
        Bucket imFrontendBucket = createImFrontendBucket();
        Project codeBuildProject = createCodeBuiltProject(imFrontendBucket, artifactBucket);

        Pipeline pipeline = createImFrontendPipeline(codeBuildProject, artifactBucket);
    }

    private Bucket createArtifactBucket() {
        BucketProps props = BucketProps.builder()
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .id("DeleteRuleForOldArtifactsId")
                        .expiration(Duration.days(1))
                        .abortIncompleteMultipartUploadAfter(Duration.days(2))
                        .build()))
                .versioned(true)
                .build();

        Bucket bucket = new Bucket(this, "ImFrontendArtifactBucketId", props);

        return bucket;
    }

    private Bucket createImFrontendBucket() {
        BucketProps props = BucketProps.builder()
                .blockPublicAccess(BlockPublicAccess.Builder.create()
                        .blockPublicAcls(false)
                        .blockPublicPolicy(false)
                        .ignorePublicAcls(false)
                        .restrictPublicBuckets(false)
                        .build())
                .versioned(false)
                .websiteIndexDocument("index.html")
                .websiteErrorDocument("index.html")
                .encryption(BucketEncryption.S3_MANAGED)
                .build();

        Bucket bucket = new Bucket(this, "ImFrontendWebHostingBucketId", props);

        // Public read policy for website hosting
        bucket.addToResourcePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .principals(List.of(new AnyPrincipal()))
                .actions(List.of("s3:GetObject"))
                .resources(List.of(bucket.getBucketArn() + "/*"))
                .build());

        return bucket;
    }

    private Project createCodeBuiltProject(Bucket imFrontendBucket, Bucket artifactBucket) {
        // Role to build and deploy Website to S3 bucket.
        RoleProps roleProps = RoleProps.builder()
                .assumedBy(ServicePrincipal.Builder.create("codebuild.amazonaws.com").build())
                .build();

        Role role = new Role(this, "ImFrontendCodeBuildRoleID", roleProps);
        role.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                .resources(List.of("arn:aws:logs:" + getRegion() + ":" + getAccount() + ":*"))
                .effect(Effect.ALLOW)
                .build());

        role.addToPolicy(PolicyStatement.Builder.create()
                .resources(List.of(imFrontendBucket.getBucketArn()))
                .actions(List.of("s3:ListBucket", "s3:GetBucketLocation"))
                .effect(Effect.ALLOW)
                .build());

        imFrontendBucket.grantReadWrite(role);
        imFrontendBucket.grantDelete(role);
        artifactBucket.grantRead(role);

        // CodeBuild Project
        ProjectProps props = ProjectProps.builder()
                .role(role)
                .source(Source.s3(S3SourceProps.builder()
                        .bucket(artifactBucket)
                        .path("source.zip")
                        .build()))
                .logging(LoggingOptions.builder()
                        .cloudWatch(CloudWatchLoggingOptions.builder()
                                .logGroup(LogGroup.Builder.create(this, "ImFrontendBuildLogGroup")
                                        .retention(RetentionDays.ONE_WEEK)
                                        .build())
                                .build())
                        .build())
                .autoRetryLimit(0)
                .environment(BuildEnvironment.builder()
                        .computeType(SMALL)
                        .buildImage(AMAZON_LINUX_2_5)
                        .build())
                .environmentVariables(Map.of(
                        "S3_BUCKET", BuildEnvironmentVariable.builder()
                                .type(PLAINTEXT)
                                .value(imFrontendBucket.getBucketName())
                                .build()))
                .badge(false)
                .queuedTimeout(Duration.minutes(480))
                .buildSpec(BuildSpec.fromSourceFilename("buildspec.yaml"))
                .timeout(Duration.minutes(20))
                .build();

        Project project = new Project(this, "ImFrontendBuildProjectId", props);

        return project;
    }

    private Pipeline createImFrontendPipeline(Project codeBuildProject, Bucket artifactBucket) {
        Artifact sourceOutput = new Artifact("SourceOutput");

        StageProps sourceProps = StageProps.builder()
                .stageName("Source")
                .actions(List.of(CodeStarConnectionsSourceAction.Builder.create()
                        .actionName("CodeStarConnectionsSourceAction")
                        .connectionArn(connectionArn)
                        .owner(repoOwner)
                        .repo(repoName)
                        .branch(branchName)
                        .output(sourceOutput)
                        .build()))
                .build();

        StageProps buildProps = StageProps.builder()
                .stageName("CodeBuild")
                .actions(List.of(
                        new CodeBuildAction(CodeBuildActionProps.builder()
                                .actionName("CodeBuildAction")
                                .project(codeBuildProject)
                                .type(CodeBuildActionType.BUILD)
                                .input(sourceOutput)
                                .build())
                ))
                .build();

        PipelineProps pipelineProps = PipelineProps.builder()
                .artifactBucket(artifactBucket)
                .stages(List.of(sourceProps, buildProps))
                .build();

        Pipeline pipeline = new Pipeline(this, "ImFrontendPipelineId", pipelineProps);

        pipeline.addToRolePolicy(PolicyStatement.Builder.create()
                .resources(List.of(connectionArn))
                .actions(List.of("codestar-connections:UseConnection"))
                .effect(Effect.ALLOW)
                .build());

        return pipeline;
    }
}
