package com.sakai.cloud.infra.pipeline;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.PipelineProps;
import software.amazon.awscdk.services.iam.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class PipelineStack extends Stack {
    private final String connectionId = "arn:aws:codeconnections:eu-central-1:315735600242:connection/5b463871-e022-42cc-831b-be409b55e94b";

    public PipelineStack(Construct app, String id, StackProps props) {
        super(app, "PipelineStackId", props);

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

        BuildSpec buildSpecFile = BuildSpec.fromSourceFilename("buildspec.yaml");

        PipelineProjectProps pipeLineProjectProps = PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(buildSpecFile)
                .timeout(Duration.minutes(30))
                .environmentVariables(Map.of())
                .role(pipelineProjectRole)
                .build();

        PipelineProject pipelineProject = new PipelineProject(this, "BuildAndDeployProjectId", pipeLineProjectProps);

        // CodeBuild benötigt erweiterte Rechte für CDK-Deployment
        PolicyStatement policyStatement = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("cloudformation:*", "s3:*", "iam:*", "codestar-connections:*", "lambda:*", "apigateway:*", "dynamodb:*", "logs:*"))
                .resources(List.of("*"))
                .build();

        pipelineProject.getRole().addToPrincipalPolicy(policyStatement);

        // === Pipeline ===
        PipelineProps pipelineProps = PipelineProps.builder().build();

        Pipeline pipeline = new Pipeline(this, "BackedPipelineId", pipelineProps);


//        software.amazon.awscdk.pipelines.CodePipeline codePipeline;
    }
}
