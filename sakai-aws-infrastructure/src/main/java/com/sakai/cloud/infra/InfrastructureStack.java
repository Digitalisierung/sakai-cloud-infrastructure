package com.sakai.cloud.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfrastructureStack extends Stack {
    // sakai-lambda-artifacts
    private String artifactBucketName;
    // asset-service-1.0-SNAPSHOT.jar
    private String artifactObjectKey;
    private Table inventoryTable;
    // DEV, TEST, PROD, etc.
    private String stage;

    public InfrastructureStack(Construct app, String id, StackProps props) {
        super(app, id, props);
    }

    public void initializeStack() {
        stage = (String) this.getNode().tryGetContext("stage");
        if (stage == null) throw new RuntimeException("Stage is not defined");

        artifactBucketName = (String) this.getNode().tryGetContext("artifactBucketName");
        if (artifactBucketName == null) throw new RuntimeException("Artifact Bucket Name is not defined");
        artifactObjectKey = (String) this.getNode().tryGetContext("artifactObjectKey");
        if (artifactObjectKey == null) throw new RuntimeException("Artifact Object Key is not defined");

        // === DynamoDB Table ===
        inventoryTable = createDynamoDbTable(stage);

        // S3 Artifact Bucket
        IBucket artifactBucket = Bucket.fromBucketName(this, "ArtifactBucketId", artifactBucketName);

        // === Lambda Function Role ===
        Role lambdaExecRole = createLambdaExecRole();
        inventoryTable.grantReadData(lambdaExecRole);

        // === Function for ListArticles Handler ===
        Function listArticlesHandler = createLambdaFunction(lambdaExecRole, artifactBucket);

        RestApi lambdaRestApi = createApiGateway(stage, listArticlesHandler);
    }

    private Table createDynamoDbTable(String stage) {

        TableProps inventoryTableProps = TableProps.builder()
                .partitionKey(Attribute.builder()
                        .name("partitionKey")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("sortKey")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(determinateRemovalPolicy(stage))
                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder()
                        .pointInTimeRecoveryEnabled(determinatePitr(stage))
                        .build())
                .build();

        return new Table(this, "InventoryTableId", inventoryTableProps);
    }

    private Role createLambdaExecRole() {
        RoleProps lambdaRoleProps = RoleProps.builder()
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        return new Role(this, "LambdaExecutionRoleId", lambdaRoleProps);
    }

    private Function createLambdaFunction(Role lambdaExecRole, IBucket artifactBucket) {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("TABLE_NAME", inventoryTable.getTableName());

        FunctionProps laFuncProps = FunctionProps.builder()
                .runtime(Runtime.JAVA_21)
                .memorySize(1024)
                .handler("com.sakai.inventory.api.handler.ListArticlesHandler::handleRequest")
                .architecture(Architecture.X86_64)
                .timeout(Duration.seconds(30))
                .environment(envVars)
                .role(lambdaExecRole)
                .code(Code.fromBucket(artifactBucket, artifactObjectKey))
                .build();

        return new Function(this, "ListArticlesHandlerFunction", laFuncProps);
    }

    private RestApi createApiGateway(String stage, Function listArticlesFunction) {
        StageOptions deployOpt = StageOptions.builder()
                .stageName("prod")
                .dataTraceEnabled(!stage.equalsIgnoreCase("prod")) // in prod disabled
                .loggingLevel(MethodLoggingLevel.ERROR)
                .build();

        CorsOptions corsOpt = CorsOptions.builder()
                .allowOrigins(Cors.ALL_ORIGINS) // for dev allow all origins. Must be changed in prod.
                .allowMethods(Cors.ALL_METHODS)
                .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                .build();

        RestApiProps props = RestApiProps.builder()
                .restApiName("InventoryRestApiGateway")
                .description("API for Inventory Management System")
                .deployOptions(deployOpt)
                .defaultCorsPreflightOptions(corsOpt)
                .cloudWatchRole(true)
                .build();

        RestApi restApi = new RestApi(this, "RestApiGateway", props);

        // define `/articles` resource
        IResource listArticlesResource = restApi.getRoot().addResource("articles");
        listArticlesResource.addMethod("GET", new LambdaIntegration(listArticlesFunction, LambdaIntegrationOptions.builder()
                .proxy(true)
                .build()));

        // define `/articles/{id}` resource
        // define `/catalogs` resource
        // define `/catalogs/{id}/articles` resource

        return restApi;
    }

    private RemovalPolicy determinateRemovalPolicy(String stage) {
        if (stage != null && stage.equalsIgnoreCase("prod")) {
            return RemovalPolicy.RETAIN;
        }

        return RemovalPolicy.DESTROY;
    }

    private Boolean determinatePitr(String stage) {
        return stage != null && stage.equalsIgnoreCase("prod");
    }

    public Table getInventoryTable() {
        return inventoryTable;
    }

    public void setArtifactBucketName(String artifactBucketName) {
        this.artifactBucketName = artifactBucketName;
    }

    public void setArtifactObjectKey(String artifactObjectKey) {
        this.artifactObjectKey = artifactObjectKey;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }
}
