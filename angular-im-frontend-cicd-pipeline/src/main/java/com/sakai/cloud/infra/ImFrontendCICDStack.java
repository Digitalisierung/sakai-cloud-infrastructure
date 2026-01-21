package com.sakai.cloud.infra;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.CfnProject;
import software.amazon.awscdk.services.codebuild.CfnProjectProps;
import software.amazon.awscdk.services.codepipeline.CfnPipeline;
import software.amazon.awscdk.services.codepipeline.CfnPipelineProps;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.CfnBucket;
import software.amazon.awscdk.services.s3.CfnBucketPolicy;
import software.amazon.awscdk.services.s3.CfnBucketPolicyProps;
import software.amazon.awscdk.services.s3.CfnBucketProps;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class ImFrontendCICDStack extends Stack {
    private String connectionArn;
    private String connectionId;

    public ImFrontendCICDStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        connectionId = "0eb84fa4-1c3f-4b0b-8434-a3f94184c621";
        connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/" + connectionId;

        CfnBucket imFrontendBucket = createImFrontendBucket();
        CfnBucket artifactBucket = createPipelineArtifactBucket();
        CfnProject codeBuildProject = createCodeBuildProject(imFrontendBucket);
        createL1Pipeline(codeBuildProject, imFrontendBucket, artifactBucket);
    }

    private CfnBucket createPipelineArtifactBucket() {
        CfnBucket.PublicAccessBlockConfigurationProperty publicAccessConfiguration = CfnBucket.PublicAccessBlockConfigurationProperty.builder()
                .blockPublicAcls(true)
                .blockPublicPolicy(true)
                .ignorePublicAcls(true)
                .restrictPublicBuckets(true)
                .build();

        CfnBucket.LifecycleConfigurationProperty lifecycleConfigurationProperty = CfnBucket.LifecycleConfigurationProperty.builder()
                .rules(List.of(CfnBucket.RuleProperty.builder()
                        .id("DeleteOldArtifactsId")
                        .status("Enabled")
                        .expirationInDays(1)
                        .build()))
                .build();

        CfnBucket.VersioningConfigurationProperty versioningConfiguration = CfnBucket.VersioningConfigurationProperty.builder()
                .status("Enabled")
                .build();

        CfnBucketProps bucketProps = CfnBucketProps.builder()
                .publicAccessBlockConfiguration(publicAccessConfiguration)
                .lifecycleConfiguration(lifecycleConfigurationProperty)
                .versioningConfiguration(versioningConfiguration)
                .build();

        CfnBucket artifactBucket = new CfnBucket(this, "ArtifactBucketId", bucketProps);

        return artifactBucket;
    }

    private CfnBucket createImFrontendBucket() {
        CfnBucket.WebsiteConfigurationProperty websiteConfiguration = CfnBucket.WebsiteConfigurationProperty.builder()
                .indexDocument("index.html")
                .errorDocument("index.html")
                .build();

        CfnBucket.PublicAccessBlockConfigurationProperty publicAccessConfiguration = CfnBucket.PublicAccessBlockConfigurationProperty.builder()
                .blockPublicAcls(false)
                .blockPublicPolicy(false)
                .ignorePublicAcls(false)
                .restrictPublicBuckets(false)
                .build();

        CfnBucketProps bucketProps = CfnBucketProps.builder()
                .websiteConfiguration(websiteConfiguration)
                .publicAccessBlockConfiguration(publicAccessConfiguration)
                .versioningConfiguration(CfnBucket.VersioningConfigurationProperty.builder()
                        .status("Disabled")
                        .build())
                .build();

        CfnBucket imFrontendBucket = new CfnBucket(this, "ImFrontendWebHostingBucket", bucketProps);

        // Bucket Policy
        CfnBucketPolicyProps bucketPolicyProps = CfnBucketPolicyProps.builder()
                .bucket(imFrontendBucket.getRef())
                .policyDocument(PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(List.of("s3:GetObject"))
                        .resources(List.of(imFrontendBucket.getAttrArn() + "/*"))
                        .principals(List.of(new AnyPrincipal()))
                        .sid("PublicReadAccess")
                        .build())
                .build();

        new CfnBucketPolicy(this, "ImFrontendWebHostingBucketPolicy", bucketPolicyProps);

        return imFrontendBucket;
    }

    private CfnProject createCodeBuildProject(CfnBucket imFrontendBucket) {
        PolicyDocumentProps props = PolicyDocumentProps.builder()
                .statements(List.of(
                        PolicyStatement.Builder.create()
                                .actions(List.of("s3:GetObject", "s3:PutObject", "s3:DeleteObject"))
                                .effect(Effect.ALLOW)
                                .resources(List.of(imFrontendBucket.getAttrArn() + "/*"))
                                .build(),
                        PolicyStatement.Builder.create()
                                .actions(List.of("s3:ListBucket"))
                                .effect(Effect.ALLOW)
                                .resources(List.of(imFrontendBucket.getAttrArn()))
                                .build(),
                        PolicyStatement.Builder.create()
                                .actions(List.of("codeconnections:UseConnection"))
                                .effect(Effect.ALLOW)
                                .resources(List.of(connectionArn))
                                .build(),
                        PolicyStatement.Builder.create()
                                .actions(List.of("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                                .effect(Effect.ALLOW)
                                .build()
                        )
                )
                .build();

        PolicyDocument policyDocument = new PolicyDocument(props);

        CfnRoleProps roleProps = CfnRoleProps.builder()
                .assumeRolePolicyDocument(List.of(policyDocument))
                .build();

        CfnRole codeBuildRole = new CfnRole(this, "CodeBuildRoleId", roleProps);

        CfnProject.EnvironmentProperty environmentProperty = CfnProject.EnvironmentProperty.builder()
                .computeType("BUILD_GENERAL1_SMALL")
                .image("aws/codebuild/amazonlinux2-x86_64-standard:4.0")
                .imagePullCredentialsType("CODEBUILD")
                .privilegedMode(false)
                .type("LINUX_CONTAINER")
                .environmentVariables(List.of(
                        CfnProject.EnvironmentVariableProperty.builder()
                                .name("S3_BUCKET")
                                .value(imFrontendBucket.getBucketName())
                                .type("PLAINTEXT")
                                .build()
                ))
                .build();

        CfnProjectProps projectProps = CfnProjectProps.builder()
                .artifacts(CfnProject.ArtifactsProperty.builder()
                        .type("CODEPIPELINE")
                        .build())
                .source(CfnProject.SourceProperty.builder()
                        .type("CODEPIPELINE")
                        .buildSpec("buildspec.yaml")
                        .build())
                .badgeEnabled(false)
                .cache(CfnProject.ProjectCacheProperty.builder()
                        .type("NO_CACHE")
                        .build())
                .encryptionKey("alias/aws/s3")
                .environment(environmentProperty)
                .visibility("PRIVATE")
                .queuedTimeoutInMinutes(480)
                .timeoutInMinutes(20)
                .autoRetryLimit(0)
                .serviceRole(codeBuildRole.getAttrArn())
                .logsConfig(CfnProject.LogsConfigProperty.builder()
                        .cloudWatchLogs(CfnProject.CloudWatchLogsConfigProperty.builder()
                                .status("ENABLED")
                                .groupName("/aws/codebuild/ImFrontendCICDBuildProjectL1")
                                .build())
                        .s3Logs(CfnProject.S3LogsConfigProperty.builder()
                                .status("DISABLED")
                                .build())
                        .build())
                .build();

        CfnProject project = new CfnProject(this, "ImFrontendCodeBuildProjectId", projectProps);

        return project;
    }

    private void createL1Pipeline(CfnProject codeBuildProject, CfnBucket imFrontendBucket, CfnBucket pipelineArtifactBucket) {
        // CodeConnection Policy
        PolicyStatementProps policyStatementProps = PolicyStatementProps.builder()
                .resources(List.of(connectionArn))
                .actions(List.of("codeconnections:UseConnection", "codestar-connections:UseConnection"))
                .effect(Effect.ALLOW)
                .build();

        PolicyStatement codeconnectionPolicyStatement = new PolicyStatement(policyStatementProps);

        // CodeBuild Policy
        PolicyStatementProps codebuildPolicyStatementProps = PolicyStatementProps.builder()
                .resources(List.of(codeBuildProject.getAttrArn()))
                .actions(List.of("codebuild:BatchGetBuilds", "codebuild:StartBuild"))
                .effect(Effect.ALLOW)
                .build();

        PolicyStatement codebuildPolicyStatement = new PolicyStatement(codebuildPolicyStatementProps);

        // artifact Bucket read-write policy
        PolicyStatementProps rwArtBucketPolicyProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:GetObject", "s3:PutObject", "s3:DeleteObject"))
                .resources(List.of(pipelineArtifactBucket.getAttrArn() + "/*"))
                .build();

        PolicyStatement rwArtBucketPolicyStatement = new PolicyStatement(rwArtBucketPolicyProps);

        // im-frontend Bucket read-write policy
        PolicyStatementProps imFrontendRWPolicyProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket", "s3:GetBucketLocation"))
                .resources(List.of(imFrontendBucket.getAttrArn(), imFrontendBucket.getAttrArn() + "/*"))
                .build();

        PolicyStatement imFrontendRWPolicyStatement = new PolicyStatement(imFrontendRWPolicyProps);


        // Pipeline Role
        RoleProps roleProps = RoleProps.builder()
                .assumedBy(new software.amazon.awscdk.services.iam.ServicePrincipal("codepipeline.amazonaws.com"))
                .description("Diese Role Wird verwendet für Im-Fronend zum Bereitstellen von Frontend (Angular) im AWS Pipeline.")
                .build();

        Role pipelineRole = new Role(this, "ImFrontendPipelineRole", roleProps);
        pipelineRole.addToPolicy(codeconnectionPolicyStatement);
        pipelineRole.addToPolicy(codebuildPolicyStatement);
        pipelineRole.addToPolicy(rwArtBucketPolicyStatement);
        pipelineRole.addToPolicy(imFrontendRWPolicyStatement);


        // GitHub Stages
        CfnPipeline.ActionDeclarationProperty sourceAction = CfnPipeline.ActionDeclarationProperty.builder()
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
                        "branchName", "develop",
                        "OutputArtifactFormat", "CODE_ZIP"
                ))
                .outputArtifacts(List.of(
                        CfnPipeline.OutputArtifactProperty.builder()
                                .name("SourceOutput")
                                .build()
                ))
                .build();

        CfnPipeline.StageDeclarationProperty gitHubStage = CfnPipeline.StageDeclarationProperty.builder()
                .name("Source")
                .actions(List.of(sourceAction))
                .build();

        // CodeBuild Stage
        CfnPipeline.ActionDeclarationProperty buildAction = CfnPipeline.ActionDeclarationProperty.builder()
                .name("CodeBuild")
                .actionTypeId(CfnPipeline.ActionTypeIdProperty.builder()
                        .category("Build")
                        .owner("AWS")
                        .provider("CodeBuild")
                        .version("1")
                        .build())
                .configuration(Map.of("ProjectName", codeBuildProject.getRef()))
                .inputArtifacts(List.of(
                        CfnPipeline.InputArtifactProperty.builder()
                                .name("SourceOutput")
                                .build()
                ))
                .build();

        CfnPipeline.StageDeclarationProperty codeBuildStage = CfnPipeline.StageDeclarationProperty.builder()
                .name("Build")
                .actions(List.of(buildAction))
                .build();

        // Pipeline
        CfnPipelineProps pipelineProps = CfnPipelineProps.builder()
                .roleArn(pipelineRole.getRoleArn())
                .artifactStore(CfnPipeline.ArtifactStoreProperty.builder()
                        .type("S3")
                        .location(pipelineArtifactBucket.getBucketName())
                        .build())
                .stages(List.of(gitHubStage, codeBuildStage))
                .build();

        software.amazon.awscdk.services.codepipeline.CfnPipeline cfnPipeline = new CfnPipeline(this, "ImFrontendPipeline", pipelineProps);
    }
}
