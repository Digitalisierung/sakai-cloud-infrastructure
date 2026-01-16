package com.sakai.cloud.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.codebuild.CfnProject;
import software.amazon.awscdk.services.codepipeline.CfnPipeline;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class ImFrontendCICDStackL1 extends Stack {
    private static String connectionId;
    private static String connectionArn;

    public ImFrontendCICDStackL1(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        // arn:aws:codeconnections:eu-central-1:251183416711:connection/0eb84fa4-1c3f-4b0b-8434-a3f94184c621
        connectionId = "0eb84fa4-1c3f-4b0b-8434-a3f94184c621";
        connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/" + connectionId;

        Bucket bucket = createS3Bucket();
        CfnProject cfnProject = createCodeBuildProject(bucket);
        createPipeline(cfnProject, bucket);
    }

    private Bucket createS3Bucket() {
        // Create S3 bucket to deploy frontend application
        BucketProps bucketProps = BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(false)
                .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .websiteIndexDocument("index.html")
                .websiteErrorDocument("index.html")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS_ONLY)
                .publicReadAccess(true)
                .build();
        Bucket bucket = new Bucket(this, "BucketForImFrontendCICDStackL1", bucketProps);

        PolicyStatement s3PolicyStatement = PolicyStatement.Builder.create()
                .actions(List.of("s3:GetObject"))
                .resources(List.of(bucket.getBucketArn() + "/*"))
                .effect(Effect.ALLOW)
                .principals(List.of(new AnyPrincipal()))
                .build();

        bucket.addToResourcePolicy(s3PolicyStatement);
        return bucket;
    }

    private CfnProject createCodeBuildProject(Bucket bucket) {

        CfnProject.SourceProperty codeBuildSourceProp = CfnProject.SourceProperty.builder()
                .buildSpec("buildspec.yaml")
                .type("CODEPIPELINE")
                .build();

        CfnProject.CloudWatchLogsConfigProperty cloudWatchLogsConfigProperty = CfnProject.CloudWatchLogsConfigProperty.builder()
                .status("ENABLED")
                .groupName("/aws/codebuild/ImFrontendCICDStack")
                .build();

        CfnProject.S3LogsConfigProperty s3LogsConfigProperty = CfnProject.S3LogsConfigProperty.builder()
                .status("DISABLED")
                .build();

        CfnProject.LogsConfigProperty logsConfigProperty = CfnProject.LogsConfigProperty.builder()
                .cloudWatchLogs(cloudWatchLogsConfigProperty)
                .s3Logs(s3LogsConfigProperty)
                .build();

        // Service Role
        RoleProps roleProps = RoleProps.builder()
                .assumedBy(new ServicePrincipal("codebuild.amazonaws.com"))
                .build();

        Role codeBuildRole = new Role(this, "CodeBuildRole", roleProps);

        // Berechtigungen für CloudWatch Logs
        codeBuildRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                .resources(List.of("arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:" + cloudWatchLogsConfigProperty.getGroupName(), "arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:" + cloudWatchLogsConfigProperty.getGroupName() + ":*"))
                .effect(Effect.ALLOW)
                .build());

        // Berechtigung für CodeConnection
        codeBuildRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("codeconnections:UseConnection"))
                .resources(List.of(connectionArn))
                .build());

        // Berechtigungen für CodeBuild Reports (Testberichte und Code Coverage)
//        codeBuildRole.addToPolicy(PolicyStatement.Builder.create()
//                .actions(List.of("codebuild:CreateReportGroup", "codebuild:CreateReport", "codebuild:UpdateReport", "codebuild:BatchPutTestCases", "codebuild:BatchPutCodeCoverages"))
//                .resources(List.of("arn:aws:codebuild:" + getRegion() + ":" + getAccount() + ":report-group/" + cfnCodeBuildProject.getName() + "-*"))
//                .effect(Effect.ALLOW)
//                .build());

        // Berechtigung für S3 Bucket (Upload/Sync)
        bucket.grantReadWrite(codeBuildRole);

        // CodeBuild Project
        CfnProject cfnCodeBuildProject = CfnProject.Builder.create(this, "ImFrontendCICDBuildProjectL1")
                .artifacts(CfnProject.ArtifactsProperty.builder()
                        .type("CODEPIPELINE")
                        .build())
                .serviceRole(codeBuildRole.getRoleArn())
                .badgeEnabled(false)
                .cache(CfnProject.ProjectCacheProperty.builder()
                        .type("NO_CACHE")
                        .build())
                .encryptionKey("alias/aws/s3")
                .environment(CfnProject.EnvironmentProperty.builder()
                        .computeType("BUILD_GENERAL1_SMALL")
                        .image("aws/codebuild/standard:5.0")
                        .imagePullCredentialsType("CODEBUILD")
                        .privilegedMode(false)
                        .type("LINUX_CONTAINER")
                        .environmentVariables(List.of(
                                CfnProject.EnvironmentVariableProperty.builder()
                                        .name("S3_BUCKET")
                                        .value(bucket.getBucketName())
                                        .type("PLAINTEXT")
                                .build()))
                        .build())
                .logsConfig(logsConfigProperty)
                .visibility("PRIVATE")
                .queuedTimeoutInMinutes(480)
                .timeoutInMinutes(20)
                .autoRetryLimit(0)
                .source(codeBuildSourceProp)
                .build();

        return cfnCodeBuildProject;
    }

    private void createPipeline(CfnProject codeBuildProject, Bucket bucket) {
        // Pipeline Artifact Bucket
        Bucket artifactBucket = new Bucket(this, "PipelineArtifactBucket", BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build());

        // Pipeline Role
        Role pipelineRole = new Role(this, "PipelineRole", RoleProps.builder()
                .assumedBy(new ServicePrincipal("codepipeline.amazonaws.com"))
                .build());

        artifactBucket.grantReadWrite(pipelineRole);
        bucket.grantReadWrite(pipelineRole);

        pipelineRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "codeconnections:UseConnection",
                        "codestar-connections:UseConnection"
                ))
                .resources(List.of(connectionArn))
                .build());

        pipelineRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "codebuild:BatchGetBuilds",
                        "codebuild:StartBuild"))
                .resources(List.of(codeBuildProject.getAttrArn()))
                .build());

        // Pipeline
        CfnPipeline.Builder.create(this, "ImFrontendPipeline")
                .roleArn(pipelineRole.getRoleArn())
                .artifactStore(CfnPipeline.ArtifactStoreProperty.builder()
                        .type("S3")
                        .location(artifactBucket.getBucketName())
                        .build())
                .stages(List.of(
                        CfnPipeline.StageDeclarationProperty.builder()
                                .name("Source")
                                .actions(List.of(
                                        CfnPipeline.ActionDeclarationProperty.builder()
                                                .name("GitHub_Source")
                                                .actionTypeId(CfnPipeline.ActionTypeIdProperty.builder()
                                                        .category("Source")
                                                        .owner("AWS")
                                                        .provider("CodeStarSourceConnection")
                                                        .version("1")
                                                        .build())
                                                .configuration(Map.of(
                                                        "ConnectionArn", connectionArn,
                                                        "FullRepositoryId", "Digitalisierung/im-frontend",
                                                        "BranchName", "develop",
                                                        "OutputArtifactFormat", "CODE_ZIP"
                                                ))
                                                .outputArtifacts(List.of(
                                                        CfnPipeline.OutputArtifactProperty.builder()
                                                                .name("SourceOutput")
                                                                .build()
                                                ))
                                                .build()
                                ))
                                .build(),
                        CfnPipeline.StageDeclarationProperty.builder()
                                .name("Build")
                                .actions(List.of(
                                        CfnPipeline.ActionDeclarationProperty.builder()
                                                .name("CodeBuild")
                                                .actionTypeId(CfnPipeline.ActionTypeIdProperty.builder()
                                                        .category("Build")
                                                        .owner("AWS")
                                                        .provider("CodeBuild")
                                                        .version("1")
                                                        .build())
                                                .configuration(Map.of(
                                                        "ProjectName", codeBuildProject.getRef()
                                                ))
                                                .inputArtifacts(List.of(
                                                        CfnPipeline.InputArtifactProperty.builder()
                                                                .name("SourceOutput")
                                                                .build()
                                                ))
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }
}
