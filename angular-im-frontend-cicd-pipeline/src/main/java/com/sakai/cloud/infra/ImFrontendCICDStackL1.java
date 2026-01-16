package com.sakai.cloud.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.codebuild.CfnProject;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.constructs.Construct;

import java.util.List;

public class ImFrontendCICDStackL1 extends Stack {
    private static String connectionId;
    private static String connectionArn;

    public ImFrontendCICDStackL1(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        connectionId = "3baf9c01-87e6-408b-82af-6f37b63edecc";
        connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/" + connectionId;

        Bucket bucket = createS3Bucket();
        CfnProject cfnProject = proofConcepts(bucket);
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

    private CfnProject proofConcepts(Bucket bucket) {
        List<CfnProject.WebhookFilterProperty> groups = List.of(
                CfnProject.WebhookFilterProperty.builder()
                        .type("EVENT")
                        .pattern("PUSH")
                        .build(),
                CfnProject.WebhookFilterProperty.builder()
                        .type("HEAD_REF")
                        .pattern("refs/heads/develop")
                        .build());

        CfnProject.SourceProperty codeBuildSourceProp = CfnProject.SourceProperty.builder()
                .buildSpec("buildspec.yaml")
                .gitCloneDepth(1)
                .gitSubmodulesConfig(CfnProject.GitSubmodulesConfigProperty.builder()
                        .fetchSubmodules(false)
                        .build())
                .type("GITHUB")
                .location("https://github.com/Digitalisierung/im-frontend")
                .reportBuildStatus(true)
                .auth(CfnProject.SourceAuthProperty.builder()
                        .type("CODECONNECTIONS")
                        .resource(connectionArn)
                        .build())
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

        // Berechtigung für CodeConnection (WICHTIG für GitHub Zugriff)
        codeBuildRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("codestar-connections:GetConnectionToken", "codestar-connections:GetConnection", "codestar-connections:UseConnection", "codeconnections:GetConnectionToken", "codeconnections:GetConnection", "codeconnections:UseConnection"))
                .resources(List.of(connectionArn))
                .effect(Effect.ALLOW)
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
        CfnProject cfnCodeBuildProject = CfnProject.Builder.create(this, "ImFrontendCICDBuuldProjectL1")
                .artifacts(CfnProject.ArtifactsProperty.builder()
                        .type("NO_ARTIFACTS")
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
                .sourceVersion("develop")
                .triggers(CfnProject.ProjectTriggersProperty.builder()
                        .webhook(true)
                        .filterGroups(List.of(groups))
                        .build())
                .build();

        return cfnCodeBuildProject;

    }
}
