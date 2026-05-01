package com.sakai.cloud.infra.stack;

import com.sakai.cloud.infra.config.StageConfigurator;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.PipelineProps;
import software.amazon.awscdk.services.codepipeline.StageOptions;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeStarConnectionsSourceAction;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class BackendPipelineStack extends Stack {
    private final StageConfigurator stageConfig;
    private Bucket lambdaArtifactBucket;
    private Pipeline backendPipeline;

    public BackendPipelineStack(Construct scope, String id, StackProps stackProps, StageConfigurator stageConfig) {
        super(scope, id, stackProps);

        this.stageConfig = stageConfig;
    }

    public void initializeStack() {
        lambdaArtifactBucket = createLambdaArtifactBucket();
        Bucket pipelineArtifactBucket = createPipelineArtifactBucket();
        Role lambdaArtifactBucketRole = createArtifactBucketRole();
        PipelineProject codeBuildProject = createPipelineProject(lambdaArtifactBucketRole);
        Role pipelineRole = createPipelineRole(codeBuildProject);
        backendPipeline = createBackendPipeline(pipelineArtifactBucket, codeBuildProject, pipelineRole);
        pipelineArtifactBucket.grantReadWrite(pipelineRole);
    }

    private Pipeline createBackendPipeline(Bucket pipelineArtifactBucket, PipelineProject codeBuildProject, Role pipelineRole) {
        Artifact sourceOutput = new Artifact("SourceOutputArtifact");
//        Artifact buildOutput = new Artifact("BuildOutputArtifact");

        StageOptions sourceStage = StageOptions.builder()
                .stageName("Source")
                .actions(List.of(CodeStarConnectionsSourceAction.Builder.create()
                        .actionName("GitHub_Source")
                        .owner("Digitalisierung")
                        .repo("sakai-lambda-slave")
                        .branch(stageConfig.branch())
                        .connectionArn(stageConfig.connectionArn())
                        .output(sourceOutput)
                        .build()))
                .build();

        StageOptions buildStage = StageOptions.builder()
                .stageName("Build")
                .actions(List.of(CodeBuildAction.Builder.create()
                        .actionName("Build_Lamba")
                        .input(sourceOutput)
                        .project(codeBuildProject)
                        .build()))
                .build();

//        StageOptions deployStage = StageOptions.builder()
//                .stageName("Deploy")
//                .actions(List.of())
//                .build();

        PipelineProps pipelineProps = PipelineProps.builder()
                .artifactBucket(pipelineArtifactBucket)
                .role(pipelineRole)
                .stages(List.of(sourceStage, buildStage))
                .build();

        return new Pipeline(this, "BackendPipelineId", pipelineProps);
    }

    private Role createPipelineRole(PipelineProject codebuildProject) {
        // Berechtigung für Pipeline, um CodeBuild zu starten.
        PolicyStatement startCodeBuildPermissions = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("codebuild:StartBuild", "codebuild:BatchGetBuilds"))
                .resources(List.of(codebuildProject.getProjectArn()))
                .build();

        // Berechtigung, um zu deployen.
        PolicyStatement deployPermissions = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "lambda:GetFunction",
                        "lambda:GetFunctionConfiguration",
                        "lambda:UpdateFunctionConfiguration",
                        "lambda:UpdateFunctionCode"
                ))
                .resources(List.of("*"))
                .build();

        RoleProps pipelineRoleProps = RoleProps.builder()
                .description("SAKAI Project. Pipeline role for (lambda) backend pipeline.")
                .assumedBy(new ServicePrincipal("codepipeline.amazonaws.com"))
                .build();

        Role pipelineRole = new Role(this, "PipelineRoleId", pipelineRoleProps);

        pipelineRole.addToPolicy(startCodeBuildPermissions);
        pipelineRole.addToPolicy(deployPermissions);

        return pipelineRole;
    }

    private PipelineProject createPipelineProject(Role lambdaArtifactBucketRole) {
        BuildEnvironment projectEnvironment = BuildEnvironment.builder()
                .computeType(ComputeType.SMALL)
                .buildImage(LinuxBuildImage.STANDARD_7_0)
                .build();

        PipelineProjectProps projectProps = PipelineProjectProps.builder()
                .description("Backend Pipeline Project" + this.getStackName())
                .environment(projectEnvironment)
                .environmentVariables(Map.of(
                        "S3_LAMBDA_ART_BUCKET", BuildEnvironmentVariable.builder()
                                .value(lambdaArtifactBucket.getBucketName())
                                .build()
                ))
                .buildSpec(BuildSpec.fromAsset("buildspec.yaml"))
                .role(lambdaArtifactBucketRole)
                .build();

        return new PipelineProject(this, "PipelineProjectId", projectProps);
    }

    private Role createArtifactBucketRole() {
        PolicyStatement policyStatement = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:GetObject", "s3:PutObject"))
                .resources(List.of(lambdaArtifactBucket.getBucketArn() + "/*"))
                .build();

        RoleProps roleProps = RoleProps.builder()
                .description("SAKAI Project. Role to handle the artifact bucket access for CodeBuild.")
                .assumedBy(new ServicePrincipal("codebuild.amazonaws.com"))
                .build();

        Role role = new Role(this, "AccessArtifactBucketRoleId", roleProps);

        role.addToPolicy(policyStatement);

        return role;
    }

    private Bucket createPipelineArtifactBucket() {
        BucketProps bucketProps = BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .build();

        return new Bucket(this, "PipelineArtifactBucketId", bucketProps);
    }

    private Bucket createLambdaArtifactBucket() {
        BucketProps bucketProps = BucketProps.builder()
                .autoDeleteObjects(stageConfig.autoDeleteObjects())
                .removalPolicy(stageConfig.removalPolicy())
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .expiration(Duration.days(10))
                        .build()))
                .versioned(true)
                .build();

        return new Bucket(this, "LambdaArtifactBucketId", bucketProps);
    }
}
