package com.sakai.cloud.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static software.amazon.awscdk.services.codebuild.LinuxBuildImage.AMAZON_LINUX_2_5;
import static software.amazon.awscdk.services.codebuild.ComputeType.SMALL;
import static software.amazon.awscdk.services.codebuild.BuildEnvironmentVariableType.PLAINTEXT;

public class ImFrontendCICDStackL2 extends Stack {
    public ImFrontendCICDStackL2(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        Bucket artifactBucket = createArtifactBucket();
        Bucket imFrontendBucket = createImFrontendBucket();
        Project codeBuildProject = createCodeBuiltProject(imFrontendBucket, artifactBucket);
    }

    private Bucket createArtifactBucket(){
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

        return bucket;
    }

    private Project createCodeBuiltProject(Bucket imFrontendBucket, Bucket arttifactBucket) {
        ProjectProps props = ProjectProps.builder()
                .logging(LoggingOptions.builder().build())
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
}
