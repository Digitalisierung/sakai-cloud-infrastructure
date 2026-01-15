package com.sakai.cloud.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.CfnWebhook;
import software.amazon.awscdk.services.codepipeline.CfnWebhookProps;
import software.amazon.awscdk.services.iam.AnyPrincipal;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImFrontendCICDStack extends Stack {
    public ImFrontendCICDStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        Bucket bucket = createS3Bucket();
        Project codeBuildProject = createCodeBuiltProject(bucket);
    }

    private Project createCodeBuiltProject(Bucket bucket) {
        GitHubSourceProps gitHubSourceProps = GitHubSourceProps.builder()
                .repo("im-frontend")
                .branchOrRef("develop")
                .cloneDepth(1)
                .owner("Digitalisierung")
                .webhook(true)
                .webhookFilters(List.of(
                        FilterGroup.
                                inEventOf(EventAction.PUSH)
                                .andBranchIs("develop")))
                .build();

        // Build environment
        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5) // entspricht: amazonlinux-x86_64-standard:5.0"
                .computeType(ComputeType.SMALL)
                .privileged(false)
                .build();

        // Environment variables
        Map<String, BuildEnvironmentVariable> buildEnvironmentVar = new HashMap<>();
        buildEnvironmentVar.put("S3_BUCKET", BuildEnvironmentVariable.builder()
                        .type(BuildEnvironmentVariableType.PLAINTEXT)
                        .value(bucket.getBucketName())
                .build());

        // Logging options
        LogGroup logGroup = LogGroup.Builder.create(this, "ImFrontendCICDStackLogGroup")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        LoggingOptions loggingOptions = LoggingOptions.builder()
                .cloudWatch(CloudWatchLoggingOptions.builder()
                        .enabled(true)
                        .logGroup(logGroup)
                        .build())
                .build();

        // Define CodeBuild project properties
        ProjectProps codeBuildProps = ProjectProps.builder()
                .description("Build- und Deployment-Projekt für Angular-Frontend: \"SAKAI\".")
                .environment(buildEnvironment)
                .source(Source.gitHub(gitHubSourceProps))
                .buildSpec(BuildSpec.fromSourceFilename("buildspec.yaml"))
                .environmentVariables(buildEnvironmentVar)
                .timeout(Duration.minutes(15))
                .queuedTimeout(Duration.minutes(480))
                .badge(false)
                .autoRetryLimit(0)
                .logging(loggingOptions)
                .build();

        // Create CodeBuild project to deploy frontend application
        Project codeBuildProject = new Project(this, "NgImFrontendCodeBuildProject", codeBuildProps);

        PolicyStatement policyStatement = new PolicyStatement(PolicyStatementProps.builder()
                .actions(Arrays.asList("s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket", "s3:GetBucketLocation"))
                .effect(Effect.ALLOW)
                .resources(List.of("arn:aws:s3:::" + bucket.getBucketArn(), "arn:aws:s3:::" + bucket.getBucketArn() + "/*"))
                .build());
        codeBuildProject.addToRolePolicy(policyStatement);

        Tags.of(codeBuildProject).add("SakaiNGCodeBuildProjekt", "NG Frontend");

        return codeBuildProject;
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
        Bucket bucket = new Bucket(this, "ImFrontendCICDStack", bucketProps);

        PolicyStatement s3PolicyStatement = PolicyStatement.Builder.create()
                .actions(List.of("s3:GetObject"))
                .resources(List.of(bucket.getBucketArn() + "/*"))
                .effect(Effect.ALLOW)
                .principals(List.of(new AnyPrincipal()))
                .build();

        bucket.addToResourcePolicy(s3PolicyStatement);
        return bucket;
    }

    private void proofConcepts() {}
}
