package com.sakai.cloud.junie;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.constructs.Construct;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ImFrontendCICDStackL2 extends Stack {
    private final String connectionArn;

    public ImFrontendCICDStackL2(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        String connectionId = "0eb84fa4-1c3f-4b0b-8434-a3f94184c621";
        this.connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/" + connectionId;

        Bucket bucket = createS3Bucket();
        createCodeBuildProject(bucket);
    }

    private Bucket createS3Bucket() {
        Bucket bucket = new Bucket(this, "BucketForImFrontendCICDStackL2", BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .websiteIndexDocument("index.html")
                .websiteErrorDocument("index.html")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS_ONLY)
                .publicReadAccess(true)
                .build());

        bucket.addToResourcePolicy(PolicyStatement.Builder.create()
                .actions(List.of("s3:GetObject"))
                .resources(List.of(bucket.getBucketArn() + "/*"))
                .effect(Effect.ALLOW)
                .principals(List.of(new AnyPrincipal()))
                .build());

        return bucket;
    }

    private void createCodeBuildProject(Bucket bucket) {
        Role buildRole = Role.Builder.create(this, "CodeBuildRole")
                .assumedBy(new ServicePrincipal("codebuild.amazonaws.com"))
                .build();

        bucket.grantReadWrite(buildRole);

        buildRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("codeconnections:UseConnection"))
                .resources(List.of(connectionArn))
                .build());

        buildRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                .resources(List.of("arn:aws:logs:" + getRegion() + ":" + getAccount() + ":log-group:/aws/codebuild/*"))
                .build());

        CfnProject.Builder.create(this, "ImFrontendCICDBuildProjectL2")
                .source(CfnProject.SourceProperty.builder()
                        .type("GITHUB")
                        .location("https://github.com/Digitalisierung/im-frontend.git")
                        .buildSpec("buildspec.yaml")
                        .sourceIdentifier("main_source")
                        .build())
                .sourceVersion("refs/heads/develop")
                .environment(CfnProject.EnvironmentProperty.builder()
                        .type("LINUX_CONTAINER")
                        .image("aws/codebuild/standard:5.0")
                        .computeType("BUILD_GENERAL1_SMALL")
                        .environmentVariables(List.of(
                                CfnProject.EnvironmentVariableProperty.builder()
                                        .name("S3_BUCKET")
                                        .value(bucket.getBucketName())
                                        .type("PLAINTEXT")
                                        .build()
                        ))
                        .build())
                .serviceRole(buildRole.getRoleArn())
                .artifacts(CfnProject.ArtifactsProperty.builder()
                        .type("NO_ARTIFACTS")
                        .build())
                .triggers(CfnProject.ProjectTriggersProperty.builder()
                        .webhook(true)
                        .filterGroups(List.of(List.of(
                                CfnProject.WebhookFilterProperty.builder()
                                        .type("EVENT")
                                        .pattern("PUSH")
                                        .build(),
                                CfnProject.WebhookFilterProperty.builder()
                                        .type("HEAD_REF")
                                        .pattern("^refs/heads/develop$")
                                        .build()
                        )))
                        .buildType("BUILD")
                        .build())
                .build();
    }
}
