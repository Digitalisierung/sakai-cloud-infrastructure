package com.sakai.cloud.awsq;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class LambdaCICDStack extends Stack {
    
    public LambdaCICDStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        String connectionId = "5b463871-e022-42cc-831b-be409b55e94b";
        String connectionArn = "arn:aws:codeconnections:" + getRegion() + ":" + getAccount() + ":connection/" + connectionId;
        
        Bucket artifactBucket = createArtifactBucket();
        Bucket lambdaBucket = createLambdaBucket();
        
        Project buildProject = createBuildProject(lambdaBucket, artifactBucket);
        Project deployProject = createDeployProject(lambdaBucket);
        
        createPipeline(connectionArn, artifactBucket, buildProject, deployProject);
    }

    private Bucket createArtifactBucket() {
        return Bucket.Builder.create(this, "LambdaArtifactBucket")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .expiration(Duration.days(7))
                        .build()))
                .build();
    }

    private Bucket createLambdaBucket() {
        return Bucket.Builder.create(this, "LambdaCodeBucket")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(true)
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .noncurrentVersionExpiration(Duration.days(30))
                        .build()))
                .build();
    }

    private Project createBuildProject(Bucket lambdaBucket, Bucket artifactBucket) {
        Role role = Role.Builder.create(this, "LambdaBuildRole")
                .assumedBy(new ServicePrincipal("codebuild.amazonaws.com"))
                .build();

        role.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                .resources(List.of("arn:aws:logs:" + getRegion() + ":" + getAccount() + ":*"))
                .build());

        lambdaBucket.grantReadWrite(role);
        artifactBucket.grantRead(role);

        return Project.Builder.create(this, "LambdaBuildProject")
                .role(role)
                .environment(BuildEnvironment.builder()
                        .computeType(ComputeType.SMALL)
                        .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5)
                        .build())
                .environmentVariables(Map.of(
                        "LAMBDA_BUCKET", BuildEnvironmentVariable.builder()
                                .value(lambdaBucket.getBucketName())
                                .build()))
                .buildSpec(BuildSpec.fromObject(Map.of(
                        "version", "0.2",
                        "phases", Map.of(
                                "install", Map.of(
                                        "runtime-versions", Map.of("java", "corretto17")
                                ),
                                "build", Map.of(
                                        "commands", List.of(
                                                "echo 'Building Lambda functions...'",
                                                "echo '{\"functions\":[]}' > lambda-functions.json",
                                                "for dir in */; do",
                                                "  if [ -f \"${dir}pom.xml\" ]; then",
                                                "    echo \"Building Maven project in $dir\"",
                                                "    cd $dir",
                                                "    mvn clean package -DskipTests",
                                                "    JAR_FILE=$(find target -name '*.jar' -not -name '*-sources.jar' | head -1)",
                                                "    if [ -n \"$JAR_FILE\" ]; then",
                                                "      FUNCTION_NAME=$(basename $dir)",
                                                "      S3_KEY=\"${FUNCTION_NAME}-${CODEBUILD_RESOLVED_SOURCE_VERSION:0:7}.jar\"",
                                                "      aws s3 cp $JAR_FILE s3://$LAMBDA_BUCKET/$S3_KEY",
                                                "      cd ..",
                                                "      jq \".functions += [{\\\"name\\\":\\\"$FUNCTION_NAME\\\",\\\"s3Key\\\":\\\"$S3_KEY\\\",\\\"handler\\\":\\\"com.example.Handler\\\",\\\"runtime\\\":\\\"java17\\\",\\\"roleArn\\\":\\\"arn:aws:iam::${AWS_ACCOUNT_ID}:role/lambda-execution-role\\\"}]\" lambda-functions.json > tmp.json && mv tmp.json lambda-functions.json",
                                                "    fi",
                                                "    cd ..",
                                                "  fi",
                                                "done",
                                                "aws s3 cp lambda-functions.json s3://$LAMBDA_BUCKET/lambda-functions.json",
                                                "echo 'Build completed'"
                                        )
                                )
                        ),
                        "artifacts", Map.of(
                                "files", List.of("lambda-functions.json")
                        )
                )))
                .logging(LoggingOptions.builder()
                        .cloudWatch(CloudWatchLoggingOptions.builder()
                                .logGroup(LogGroup.Builder.create(this, "LambdaBuildLogGroup")
                                        .retention(RetentionDays.ONE_WEEK)
                                        .build())
                                .build())
                        .build())
                .timeout(Duration.minutes(15))
                .build();
    }

    private Project createDeployProject(Bucket lambdaBucket) {
        Role role = Role.Builder.create(this, "LambdaDeployRole")
                .assumedBy(new ServicePrincipal("codebuild.amazonaws.com"))
                .build();

        role.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"))
                .resources(List.of("arn:aws:logs:" + getRegion() + ":" + getAccount() + ":*"))
                .build());

        role.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "lambda:CreateFunction",
                        "lambda:UpdateFunctionCode",
                        "lambda:UpdateFunctionConfiguration",
                        "lambda:PublishVersion",
                        "lambda:GetFunction",
                        "lambda:ListFunctions",
                        "iam:PassRole"
                ))
                .resources(List.of("*"))
                .build());

        lambdaBucket.grantRead(role);

        return Project.Builder.create(this, "LambdaDeployProject")
                .role(role)
                .environment(BuildEnvironment.builder()
                        .computeType(ComputeType.SMALL)
                        .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5)
                        .build())
                .environmentVariables(Map.of(
                        "LAMBDA_BUCKET", BuildEnvironmentVariable.builder()
                                .value(lambdaBucket.getBucketName())
                                .build(),
                        "AWS_REGION", BuildEnvironmentVariable.builder()
                                .value(getRegion())
                                .build()))
                .buildSpec(BuildSpec.fromObject(Map.of(
                        "version", "0.2",
                        "phases", Map.of(
                                "build", Map.of(
                                        "commands", List.of(
                                                "echo 'Deploying Lambda functions...'",
                                                "aws s3 cp s3://$LAMBDA_BUCKET/lambda-functions.json .",
                                                "cat lambda-functions.json",
                                                "for func in $(jq -r '.functions[].name' lambda-functions.json); do",
                                                "  S3_KEY=$(jq -r \".functions[] | select(.name==\\\"$func\\\") | .s3Key\" lambda-functions.json)",
                                                "  HANDLER=$(jq -r \".functions[] | select(.name==\\\"$func\\\") | .handler\" lambda-functions.json)",
                                                "  RUNTIME=$(jq -r \".functions[] | select(.name==\\\"$func\\\") | .runtime\" lambda-functions.json)",
                                                "  ROLE_ARN=$(jq -r \".functions[] | select(.name==\\\"$func\\\") | .roleArn\" lambda-functions.json)",
                                                "  if aws lambda get-function --function-name $func 2>/dev/null; then",
                                                "    echo \"Updating existing function: $func\"",
                                                "    aws lambda update-function-code --function-name $func --s3-bucket $LAMBDA_BUCKET --s3-key $S3_KEY --publish",
                                                "  else",
                                                "    echo \"Creating new function: $func\"",
                                                "    aws lambda create-function --function-name $func --runtime $RUNTIME --role $ROLE_ARN --handler $HANDLER --code S3Bucket=$LAMBDA_BUCKET,S3Key=$S3_KEY --publish",
                                                "  fi",
                                                "done"
                                        )
                                )
                        )
                )))
                .logging(LoggingOptions.builder()
                        .cloudWatch(CloudWatchLoggingOptions.builder()
                                .logGroup(LogGroup.Builder.create(this, "LambdaDeployLogGroup")
                                        .retention(RetentionDays.ONE_WEEK)
                                        .build())
                                .build())
                        .build())
                .timeout(Duration.minutes(10))
                .build();
    }

    private void createPipeline(String connectionArn, Bucket artifactBucket, Project buildProject, Project deployProject) {
        Artifact sourceOutput = new Artifact("SourceOutput");

        Pipeline pipeline = Pipeline.Builder.create(this, "LambdaPipeline")
                .artifactBucket(artifactBucket)
                .stages(List.of(
                        software.amazon.awscdk.services.codepipeline.StageProps.builder()
                                .stageName("Source")
                                .actions(List.of(CodeStarConnectionsSourceAction.Builder.create()
                                        .actionName("GitHub")
                                        .connectionArn(connectionArn)
                                        .owner("Digitalisierung")
                                        .repo("lambda-functions")
                                        .branch("develop")
                                        .output(sourceOutput)
                                        .build()))
                                .build(),
                        software.amazon.awscdk.services.codepipeline.StageProps.builder()
                                .stageName("Build")
                                .actions(List.of(CodeBuildAction.Builder.create()
                                        .actionName("BuildLambdas")
                                        .project(buildProject)
                                        .input(sourceOutput)
                                        .build()))
                                .build(),
                        software.amazon.awscdk.services.codepipeline.StageProps.builder()
                                .stageName("Deploy")
                                .actions(List.of(CodeBuildAction.Builder.create()
                                        .actionName("DeployLambdas")
                                        .project(deployProject)
                                        .input(sourceOutput)
                                        .build()))
                                .build()
                ))
                .build();

        pipeline.addToRolePolicy(PolicyStatement.Builder.create()
                .actions(List.of("codestar-connections:UseConnection"))
                .resources(List.of(connectionArn))
                .build());
    }
}
