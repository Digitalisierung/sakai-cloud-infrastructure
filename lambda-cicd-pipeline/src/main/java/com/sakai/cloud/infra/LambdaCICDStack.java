package com.sakai.cloud.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Best Practice Lambda CI/CD Pipeline
 * - Build: JAR/ZIP erstellen
 * - Deploy: Lambda direkt updaten mit Versioning
 */
public class LambdaCICDStack extends Stack {

    public LambdaCICDStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        String connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/0eb84fa4-1c3f-4b0b-8434-a3f94184c621";

        // S3 Bucket für Lambda Artifacts
        Bucket lambdaArtifactBucket = new Bucket(this, "LambdaArtifactBucket", BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .expiration(Duration.days(30))
                        .build()))
                .build());

        // Pipeline Artifact Bucket
        Bucket pipelineArtifactBucket = new Bucket(this, "PipelineArtifactBucket", BucketProps.builder()
                .encryption(BucketEncryption.S3_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build());

        // Lambda Function (Beispiel)
        Function lambdaFunction = createLambdaFunction(lambdaArtifactBucket);

        // CodeBuild Project
        PipelineProject buildProject = createBuildProject(lambdaArtifactBucket);

        // CodePipeline
        createPipeline(connectionArn, buildProject, lambdaFunction, pipelineArtifactBucket, lambdaArtifactBucket);
    }

    private Function createLambdaFunction(Bucket artifactBucket) {
        Role lambdaRole = new Role(this, "LambdaExecutionRole", RoleProps.builder()
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build());

        return new Function(this, "MyLambdaFunction", FunctionProps.builder()
                .runtime(Runtime.JAVA_17)
                .handler("com.sakai.lambda.Handler::handleRequest")
                .code(Code.fromBucket(artifactBucket, "initial-lambda.jar"))
                .role(lambdaRole)
                .timeout(Duration.seconds(30))
                .memorySize(512)
                .environment(Map.of("STAGE", "dev"))
                .build());
    }

    private PipelineProject createBuildProject(Bucket lambdaArtifactBucket) {
        return PipelineProject.Builder.create(this, "LambdaBuildProject")
                .environment(BuildEnvironment.builder()
                        .buildImage(LinuxBuildImage.STANDARD_7_0)
                        .computeType(ComputeType.SMALL)
                        .build())
                .environmentVariables(Map.of(
                        "S3_BUCKET", BuildEnvironmentVariable.builder()
                                .value(lambdaArtifactBucket.getBucketName())
                                .build()
                ))
                .buildSpec(BuildSpec.fromObject(Map.of(
                        "version", "0.2",
                        "phases", Map.of(
                                "install", Map.of(
                                        "runtime-versions", Map.of("java", "corretto17")
                                ),
                                "build", Map.of(
                                        "commands", List.of(
                                                "echo Building Lambda function...",
                                                "mvn clean package -DskipTests",
                                                "mv target/*.jar lambda-function.jar"
                                        )
                                ),
                                "post_build", Map.of(
                                        "commands", List.of(
                                                "echo Uploading to S3...",
                                                "aws s3 cp lambda-function.jar s3://$S3_BUCKET/lambda-function-${CODEBUILD_RESOLVED_SOURCE_VERSION}.jar",
                                                "echo '{\"s3Bucket\":\"'$S3_BUCKET'\",\"s3Key\":\"lambda-function-'${CODEBUILD_RESOLVED_SOURCE_VERSION}'.jar\"}' > deploy-config.json"
                                        )
                                )
                        ),
                        "artifacts", Map.of(
                                "files", List.of("deploy-config.json")
                        )
                )))
                .build();
    }

    private void createPipeline(String connectionArn, PipelineProject buildProject,
                                Function lambdaFunction, Bucket pipelineArtifactBucket,
                                Bucket lambdaArtifactBucket) {

        Artifact sourceOutput = new Artifact("SourceOutput");
        Artifact buildOutput = new Artifact("BuildOutput");

        // Pipeline Role
        Role pipelineRole = new Role(this, "PipelineRole", RoleProps.builder()
                .assumedBy(new ServicePrincipal("codepipeline.amazonaws.com"))
                .build());

        pipelineRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "lambda:UpdateFunctionCode",
                        "lambda:GetFunction",
                        "lambda:GetFunctionConfiguration",
                        "lambda:UpdateFunctionConfiguration"
                ))
                .resources(List.of(lambdaFunction.getFunctionArn()))
                .build());

        lambdaArtifactBucket.grantRead(pipelineRole);

        Pipeline pipeline = Pipeline.Builder.create(this, "LambdaPipeline")
                .artifactBucket(pipelineArtifactBucket)
                .role(pipelineRole)
                .stages(List.of(
                        // Source Stage
                        StageOptions.builder()
                                .stageName("Source")
                                .actions(List.of(
                                        CodeStarConnectionsSourceAction.Builder.create()
                                                .actionName("GitHub_Source")
                                                .owner("your-github-org")
                                                .repo("your-lambda-repo")
                                                .branch("develop")
                                                .connectionArn(connectionArn)
                                                .output(sourceOutput)
                                                .build()
                                ))
                                .build(),

                        // Build Stage
                        StageOptions.builder()
                                .stageName("Build")
                                .actions(List.of(
                                        CodeBuildAction.Builder.create()
                                                .actionName("Build_Lambda")
                                                .project(buildProject)
                                                .input(sourceOutput)
                                                .outputs(List.of(buildOutput))
                                                .build()
                                ))
                                .build(),

                        // Deploy Stage
                        StageOptions.builder()
                                .stageName("Deploy")
                                .actions(List.of(
                                        LambdaInvokeAction.Builder.create()
                                                .actionName("Deploy_Lambda")
                                                .lambda(createDeployFunction(lambdaFunction, lambdaArtifactBucket))
                                                .inputs(List.of(buildOutput))
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }

    private Function createDeployFunction(Function targetLambda, Bucket artifactBucket) {
        Role deployRole = new Role(this, "DeployFunctionRole", RoleProps.builder()
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build());

        deployRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "lambda:UpdateFunctionCode",
                        "lambda:GetFunctionConfiguration",
                        "lambda:UpdateFunctionConfiguration",
                        "lambda:GetFunction"
                ))
                .resources(List.of(targetLambda.getFunctionArn()))
                .build());

        artifactBucket.grantRead(deployRole);

        return new Function(this, "DeployFunction", FunctionProps.builder()
                .runtime(Runtime.PYTHON_3_11)
                .handler("index.handler")
                .role(deployRole)
                .timeout(Duration.minutes(5))
                .code(Code.fromInline(
                        "import boto3\n" +
                                "import json\n" +
                                "lambda_client = boto3.client('lambda')\n" +
                                "def handler(event, context):\n" +
                                "    config = json.loads(event['CodePipeline.job']['data']['inputArtifacts'][0]['location']['s3Location']['objectKey'])\n" +
                                "    response = lambda_client.update_function_code(\n" +
                                "        FunctionName='" + targetLambda.getFunctionName() + "',\n" +
                                "        S3Bucket=config['s3Bucket'],\n" +
                                "        S3Key=config['s3Key'],\n" +
                                "        Publish=True\n" +
                                "    )\n" +
                                "    return {'statusCode': 200, 'body': json.dumps(response)}\n"
                ))
                .build());
    }
}
