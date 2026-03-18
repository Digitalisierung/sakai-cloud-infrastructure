package com.sakai.cloud.infra.pipeline;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineProps;
import software.amazon.awscdk.pipelines.ShellStep;
import software.amazon.awscdk.pipelines.ShellStepProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.PipelineProps;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeStarConnectionsSourceAction;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class BackendPipelineStack extends Stack {
    private static final String CONNECTION_ID = "arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b";
    private static final String DEFAULT_REPO_OWNER = "Digitalisierung";
    private static final String DEFAULT_REPO_NAME = "sakai-cloud-infrastructure";
    private static final String DEFAULT_BRANCH_NAME = "develop";
    private static final String BUILD_SPEC_PATH = "sakai-aws-infrastructure/buildspec.yaml";
    private static final int TIMEOUT_MINUTES = 30;

    public BackendPipelineStack(Construct app, String id, StackProps props) {
        super(app, id, props);

//        software.amazon.awscdk.pipelines.CodePipeline codePipeline;
        // Outputs
        // Pipeline-URL etc. können optional ausgegeben werden
    }

    public void initialize() {
        // Konfiguration aus Context oder Umgebungsvariablen
        String repoOwner = resolveContext("repoOwner");

        String repoName = resolveContext("repoName");

        String branchName = resolveContext("branchName");

        // === Source-Artifact (für Pipeline) ===
        Artifact sourceOutput = Artifact.artifact("SourceOutput");

        // === CodeBuild-Projekt für Pipeline ===
        Role codeBuildRole = createCodeBuildRole();
        PipelineProject codeBuildProject = createCodeBuildProject(codeBuildRole, Map.of());

        // === Pipeline (Source + CodeBuild) ===
        Role pipelineRole = createPipelineRole();
        StageProps sourceStage = createSourceStage(repoOwner, repoName, branchName, sourceOutput);
        StageProps codeBuildStage = createCodeBuildStage(codeBuildProject, sourceOutput);

        Pipeline pipeline = createPipeline(pipelineRole, sourceStage, codeBuildStage);
        pipeline.getArtifactBucket().grantReadWrite(codeBuildRole);
    }

    // TODO: Umgebungsvariable auslesen?
    private String resolveContext(String contextKey) {
        String contextValue = (String) this.getNode().tryGetContext(contextKey);
        if (contextValue == null) throw new IllegalStateException("Context key '" + contextKey + "' not defined");
        return contextValue;
    }

    private Role createCodeBuildRole() {
        RoleProps pipelineProjectRoleProps = RoleProps.builder()
                .assumedBy(ServicePrincipal.Builder.create("codebuild.amazonaws.com")
                        .build())
                .build();

        Role role = new Role(this, "CodeBuildRole", pipelineProjectRoleProps);

        addCloudFormationPermissions(role);
        addIamPermissions(role);
        addS3Permissions(role);
        addServicePermissions(role);
        addCloudWatchPermissions(role);
        addSsmPermissions(role);
        addCdkBootstrapPermissions(role);
        addCodeStarPermissions(role);

        return role;
    }

    private void addCloudFormationPermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("cloudformation:*"))
                .resources(List.of("arn:aws:cloudformation:*:*:stack/*/*"))
                .build());
    }

    private void addIamPermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "iam:GetRole",
                        "iam:CreateRole",
                        "iam:DeleteRole",
                        "iam:PutRolePolicy",
                        "iam:AttachRolePolicy",
                        "iam:DetachRolePolicy",
                        "iam:DeleteRolePolicy",
                        "iam:PassRole"
                ))
                .resources(List.of("arn:aws:iam::*:role/*"))
                .build());
    }

    private void addS3Permissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:*"))
                .resources(List.of("*"))
                .build());
    }

    private void addServicePermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("lambda:*", "apigateway:*", "dynamodb:*"))
                .effect(Effect.ALLOW)
                .resources(List.of("*"))
                .build());
    }

    private void addCloudWatchPermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogStreams"
                ))
                .resources(List.of("*"))
                .build());
    }

    private void addSsmPermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("ssm:GetParameter"))
                .resources(List.of("arn:aws:ssm:*:*:parameter/cdk-bootstrap/*"))
                .build());
    }

    private void addCdkBootstrapPermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("sts:AssumeRole"))
                .resources(List.of("arn:aws:iam::*:role/cdk-*"))
                .build());
    }

    private void addCodeStarPermissions(Role role) {
        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("codestar-connections:UseConnection"))
                .resources(List.of(CONNECTION_ID))
                .build());
    }

    private PipelineProject createCodeBuildProject(Role codeBuildRole, Map<String, BuildEnvironmentVariable> environmentVariables) {
        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.STANDARD_7_0)
                .computeType(ComputeType.SMALL)
                .build();

        BuildSpec buildSpecFile = BuildSpec.fromSourceFilename(BUILD_SPEC_PATH);

        PipelineProjectProps pipeLineProjectProps = PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(buildSpecFile)
                .timeout(Duration.minutes(TIMEOUT_MINUTES))
                .environmentVariables(environmentVariables)
                .role(codeBuildRole)
                .build();

        return new PipelineProject(this, "BuildAndDeployProjectId", pipeLineProjectProps);
    }

    private Role createPipelineRole() {
        Role role = new Role(this, "PipelineRole", RoleProps.builder()
                .assumedBy(new ServicePrincipal("codepipeline.amazonaws.com"))
                .build());

        role.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("codestar-connections:UseConnection"))
                .resources(List.of(CONNECTION_ID))
                .build());

        return role;
    }

    private StageProps createSourceStage(String repoOwner, String repoName, String branchName, Artifact sourceOutput) {
        return StageProps.builder()
                .stageName("SOURCE")
                .actions(List.of(
                        CodeStarConnectionsSourceAction.Builder.create()
                                .actionName("GitHub_SourceAction")
                                .owner(repoOwner)
                                .repo(repoName)
                                .branch(branchName)
                                .connectionArn(CONNECTION_ID)
                                .output(sourceOutput)
                                .build()
                ))
                .build();
    }

    private StageProps createCodeBuildStage(PipelineProject codeBuildProject, Artifact sourceOutput) {
        return StageProps.builder()
                .stageName("BUILD")
                .actions(List.of(
                        CodeBuildAction.Builder.create()
                                .actionName("BuildAndDeployAction")
                                .project(codeBuildProject)
                                .input(sourceOutput)
                                .build()
                ))
                .build();
    }

    private Pipeline createPipeline(Role pipelineRole, StageProps sourceStage, StageProps codeBuildStage) {
        PipelineProps pipelineProps = PipelineProps.builder()
                .role(pipelineRole)
                .stages(List.of(sourceStage, codeBuildStage))
                .build();

        return new Pipeline(this, "BackedPipelineId", pipelineProps);
    }

    public void createCodePipeline(Bucket artifactBucket, Role role) {
        ShellStepProps shellStepProps = ShellStepProps.builder()
                .build();

        ShellStep synthShellStep = new ShellStep("SynthShellStep", shellStepProps);

        CodePipelineProps codePipelineProps = CodePipelineProps.builder()
                .synth(synthShellStep)
                .artifactBucket(artifactBucket)
                .role(role)
                .build();

        CodePipeline codePipeline = new CodePipeline(this, "CodePipelineId", codePipelineProps);
    }
}
