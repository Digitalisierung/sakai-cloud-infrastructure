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
import software.amazon.awscdk.services.ssm.ParameterDataType;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.ssm.StringParameterProps;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class LambdaDeployPipelineStack extends Stack {
    private final StageConfigurator stageConfig;
    private Bucket lambdaArtifactBucket;
    private Pipeline backendPipeline;

    private StringParameter bucketNameParameter;
    private StringParameter jarKeyParameter;

    // TODO: eine Lösung überlegen - zentraler Konfigurationsort (oder Datei) für ORG und REPO.
    private static final String ORGANISATION = "Digitalisierung";
    private static final String REPO = "sakai-lambda-slave";

    // TODO: buildspec.yaml muss im Backend-Repo (sakai-lambda-slave) vorhanden sein.
    // Alternativ: BuildSpec.fromObject() für Inline-Definition verwenden.
    public LambdaDeployPipelineStack(Construct scope, String id, StackProps stackProps, StageConfigurator stageConfig) {
        super(scope, id, stackProps);

        this.stageConfig = stageConfig;
    }

    public void initializeStack() {
        lambdaArtifactBucket = createLambdaArtifactBucket();
        initializeStringParams();
        Bucket pipelineArtifactBucket = createPipelineArtifactBucket();
        Role lambdaArtifactBucketRole = createArtifactBucketRole();
        PipelineProject codeBuildProject = createPipelineProject(lambdaArtifactBucketRole);
        Role pipelineRole = createPipelineRole(codeBuildProject, lambdaArtifactBucketRole);
        pipelineArtifactBucket.grantReadWrite(pipelineRole);
        backendPipeline = createBackendPipeline(pipelineArtifactBucket, codeBuildProject, pipelineRole);
    }

    private void initializeStringParams() {
        StringParameterProps stringParamProps = StringParameterProps.builder()
                .parameterName("/sakai/" + stageConfig.stageName() + "/lambda/artifact-bucket-name")
                .stringValue(lambdaArtifactBucket.getBucketName())
                .dataType(ParameterDataType.TEXT)
                .build();

        this.bucketNameParameter = new StringParameter(this, "BucketNameParameterId", stringParamProps);


        StringParameterProps jarKeyParamProps = StringParameterProps.builder()
                .parameterName("/sakai/" + stageConfig.stageName() + "/lambda/artifact-key")
                .stringValue("asset-service-lambda.jar")
                .dataType(ParameterDataType.TEXT)
                .build();

        this.jarKeyParameter = new StringParameter(this, "JarKeyParameterID", jarKeyParamProps);

        /*
        buildspec.yaml im Backend-Repo diese SSM-Parameter nach erfolgreichem Upload setzen muss:
        post_build:
          commands:
            - aws ssm put-parameter --name "/sakai/${STAGE_NAME}/lambda/artifact-bucket-name" --value "${S3_LAMBDA_ART_BUCKET}" --type String --overwrite
            - aws ssm put-parameter --name "/sakai/${STAGE_NAME}/lambda/artifact-key" --value "asset-service-lambda.jar" --type String --overwrite
         */
    }

    public Bucket getLambdaArtifactBucket() {
        return lambdaArtifactBucket;
    }

    private Pipeline createBackendPipeline(Bucket pipelineArtifactBucket, PipelineProject codeBuildProject, Role pipelineRole) {
        Artifact sourceOutput = new Artifact("BackendSourceOutputArtifact");
//        Artifact buildOutput = new Artifact("BuildOutputArtifact");

        StageOptions sourceStage = StageOptions.builder()
                .stageName("Source")
                .actions(List.of(CodeStarConnectionsSourceAction.Builder.create()
                        .actionName("GitHub_Source")
                        .owner(ORGANISATION)
                        .repo(REPO)
                        .branch(stageConfig.branch())
                        .connectionArn(stageConfig.connectionArn())
                        .triggerOnPush(true)
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

    private Role createPipelineRole(PipelineProject codebuildProject, Role lambdaArtifactBucketRole) {
        // Berechtigung für Pipeline, um CodeBuild zu starten.
        PolicyStatement startCodeBuildPermissions = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("codebuild:StartBuild", "codebuild:BatchGetBuilds"))
                .resources(List.of(codebuildProject.getProjectArn()))
                .build();

        // Berechtigung, um zu deployen.
//        PolicyStatement deployPermissions = PolicyStatement.Builder.create()
//                .effect(Effect.ALLOW)
//                .actions(List.of(
//                        "lambda:GetFunction",
//                        "lambda:GetFunctionConfiguration",
//                        "lambda:UpdateFunctionConfiguration",
//                        "lambda:UpdateFunctionCode"
//                ))
//                .resources(List.of("*"))
//                .build();

        // Berechtigung, um eine Berechtigung zuzuweisen.
        PolicyStatement passPermissionToCodeBuild = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("iam:PassRole"))
                .resources(List.of(lambdaArtifactBucketRole.getRoleArn()))
                .build();

        RoleProps pipelineRoleProps = RoleProps.builder()
                .description("IAM-Rolle für die CodePipeline des Lambda-Deployments.")
                .assumedBy(new ServicePrincipal("codepipeline.amazonaws.com"))
                .build();

        Role pipelineRole = new Role(this, "PipelineRoleId", pipelineRoleProps);

        pipelineRole.addToPolicy(startCodeBuildPermissions);
        pipelineRole.addToPolicy(passPermissionToCodeBuild);
//        pipelineRole.addToPolicy(deployPermissions);

        return pipelineRole;
    }

    private PipelineProject createPipelineProject(Role lambdaArtifactBucketRole) {
        BuildEnvironment projectEnvironment = BuildEnvironment.builder()
                .computeType(ComputeType.MEDIUM)
                .buildImage(LinuxBuildImage.STANDARD_7_0)
                .build();

        PipelineProjectProps projectProps = PipelineProjectProps.builder()
                .description("CodeBuild-Projekt für den Bau und Deployment der Lambda-Funktion des Inventory-Services.")
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
                .actions(List.of("s3:GetObject", "s3:PutObject", "ssm:PutParameter"))
                .resources(List.of(
                        lambdaArtifactBucket.getBucketArn(),
                        lambdaArtifactBucket.getBucketArn() + "/*",
                        bucketNameParameter.getParameterArn(),
                        jarKeyParameter.getParameterArn()))
                .build();

        RoleProps roleProps = RoleProps.builder()
                .description("IAM-Rolle für den Zugriff auf den S3-Bucket mit Lambda-Artefakten durch CodeBuild.")
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
