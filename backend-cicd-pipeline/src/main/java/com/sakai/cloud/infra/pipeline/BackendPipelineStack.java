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
    private final String connectionId = "arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b";

    public BackendPipelineStack(Construct app, String id, StackProps props) {
        super(app, id, props);

        // Konfiguration aus Context oder Umgebungsvariablen
        String repoOwner = (String) this.getNode().tryGetContext("repoOwner");
        if (repoOwner == null) repoOwner = "Digitalisierung";

        String repoName = (String) this.getNode().tryGetContext("repoName");
        if (repoName == null) repoName = "sakai-cloud-infrastructure";

        String branchName = (String) this.getNode().tryGetContext("branchName");
        if (branchName == null) branchName = "develop";

        // === Source-Artifact (für Pipeline) ===
        Artifact sourceOutput = Artifact.artifact("SourceOutput");

        // === CodeBuild-Projekt für Build + Deploy ===
        RoleProps pipelineProjectRoleProps = RoleProps.builder()
                .assumedBy(ServicePrincipal.Builder.create("codebuild.amazonaws.com")
                        .build())
                .build();

        Role pipelineProjectRole = new Role(this, "CodeBuildRole", pipelineProjectRoleProps);

        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.STANDARD_7_0)
                .computeType(ComputeType.SMALL)
                .build();

        BuildSpec buildSpecFile = BuildSpec.fromSourceFilename("sakai-aws-infrastructure/buildspec.yaml");

        PipelineProjectProps pipeLineProjectProps = PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(buildSpecFile)
                .timeout(Duration.minutes(30))
                .environmentVariables(Map.of())
                .role(pipelineProjectRole)
                .build();

        PipelineProject pipelineProject = new PipelineProject(this, "BuildAndDeployProjectId", pipeLineProjectProps);

        // CodeBuild benötigt erweiterte Rechte für CDK-Deployment
        // CloudFormation Berechtigungen
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("cloudformation:*"))
                .resources(List.of("arn:aws:cloudformation:*:*:stack/*/*"))
                .build());

        // IAM Berechtigungen (Eingeschränkt auf Stack-Ressourcen)
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
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

        // S3 Berechtigungen (für CDK Assets)
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:*"))
                .resources(List.of("*"))
                .build());

        // Lambda, API Gateway und DynamoDB Berechtigungen
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("lambda:*", "apigateway:*", "dynamodb:*"))
                .effect(Effect.ALLOW)
                .resources(List.of("*"))
                .build());

        // CloudWatch Logs Berechtigungen (für Lambda und API Gateway)
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogStreams"
                ))
                .resources(List.of("*"))
                .build());

        // SSM Parameter (falls CDK Bootstrap verwendet)
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("ssm:GetParameter"))
                .resources(List.of("arn:aws:ssm:*:*:parameter/cdk-bootstrap/*"))
                .build());

        // CDK Bootstrap Rollen AssumeRole
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("sts:AssumeRole"))
                .resources(List.of("arn:aws:iam::*:role/cdk-*"))
                .build());

        // CodeStar Connection Berechtigung
        pipelineProjectRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("codestar-connections:UseConnection"))
                .resources(List.of(connectionId))
                .build());

        // === Pipeline Role ===
        Role pipelineRole = new Role(this, "PipelineRole", RoleProps.builder()
                .assumedBy(new ServicePrincipal("codepipeline.amazonaws.com"))
                .build());

        pipelineRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("codestar-connections:UseConnection"))
                .resources(List.of(connectionId))
                .build());

        // === Pipeline ===
        StageProps sourceStage = StageProps.builder()
                .stageName("SOURCE")
                .actions(List.of(
                        CodeStarConnectionsSourceAction.Builder.create()
                                .actionName("GitHub_SourceAction")
                                .owner(repoOwner)
                                .repo(repoName)
                                .branch(branchName)
                                .connectionArn(connectionId)
                                .output(sourceOutput)
                                .build()
                ))
                .build();

        StageProps buildStage = StageProps.builder()
                .stageName("BUILD")
                .actions(List.of(
                        CodeBuildAction.Builder.create()
                                .actionName("BuildAndDeployAction")
                                .project(pipelineProject)
                                .input(sourceOutput)
                                .build()
                ))
                .build();

        PipelineProps pipelineProps = PipelineProps.builder()
                .role(pipelineRole)
                .stages(List.of(sourceStage, buildStage))
                .build();

        Pipeline pipeline = new Pipeline(this, "BackedPipelineId", pipelineProps);

        pipeline.getArtifactBucket().grantReadWrite(pipelineProjectRole);

//        software.amazon.awscdk.pipelines.CodePipeline codePipeline;
        // Outputs
        // Pipeline-URL etc. können optional ausgegeben werden
    }

    public void initialize(Bucket artifactBucket, Role role) {
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
