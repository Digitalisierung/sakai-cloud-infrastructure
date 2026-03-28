package com.sakai.cloud.infra.stack;

import com.sakai.cloud.infra.config.ApiGatewayConfigurator;
import com.sakai.cloud.infra.config.LambdaConfigurator;
import com.sakai.cloud.infra.config.StageConfigurator;
import com.sakai.cloud.infra.factory.ApiGatewayFactory;
import com.sakai.cloud.infra.factory.DynamoDbTableFactory;
import com.sakai.cloud.infra.factory.LambdaFunctionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.IResource;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.LambdaIntegrationOptions;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class SakaiServiceStack extends Stack {
    private static final Logger LOGGER = LoggerFactory.getLogger(SakaiServiceStack.class);

    // sakai-lambda-artifacts
    private String artifactBucketName;
    // asset-service-1.0-SNAPSHOT.jar
    //private String artifactObjectKey;
    private Table inventoryTable;
    // DEV, TEST, PROD, etc.
    private StageConfigurator stageConfig;

    public SakaiServiceStack(Construct app, String id, StackProps props, StageConfigurator stageConfig) {
        super(app, id, props);

        this.stageConfig = stageConfig;

        artifactBucketName = (String) this.getNode().tryGetContext("artifactBucketName");
        if (artifactBucketName == null) artifactBucketName = System.getenv("ARTIFACT_BUCKET");
        if (artifactBucketName == null) throw new RuntimeException("Artifact Bucket Name is not defined");

        // LOGGER.info("Stage: {}, artifactBucketName: {}, artifactObjectKey: {}", stage, artifactBucketName, artifactObjectKey);
    }

    public void initializeStack() {

        DynamoDbTableFactory dbTableFactory = new DynamoDbTableFactory();
        LambdaFunctionFactory functionFactory = new LambdaFunctionFactory();
        ApiGatewayFactory apiGatewayFactory = new ApiGatewayFactory();


        // === DynamoDB TABLE ===
        //inventoryTable = createDynamoDbTable(stage);
        inventoryTable = dbTableFactory.createDynamoDbTable(this, "InventoryTableId", stageConfig.stageName());

        // === S3 Artifact BUCKET ===
        final IBucket artifactBucket = Bucket.fromBucketName(this, "ArtifactBucketId", artifactBucketName);

        // === Lambda Function IAM Role ===
        final Role lambdaExecRole = createLambdaExecRole();
        inventoryTable.grantReadData(lambdaExecRole);

        // == ApiGateway ===
        // final RestApi lambdaRestApi = createApiGateway(stage, listArticlesHandler);
        ApiGatewayConfigurator apiGatewayConfigurator = new ApiGatewayConfigurator(
                "InventoryRestApiGateway",
                "API for Inventory Management System",
                stageConfig.stageName()
        );
        final RestApi lambdaRestApi = apiGatewayFactory.createApiGateway(this, "ApiGatewayId", apiGatewayConfigurator);

        // === LAMBDA FUNCTION for ListArticles Handler ===
        LambdaConfigurator lambdaFunctionConfig = new LambdaConfigurator(
                "com.sakai.inventory.api.handler.ListArticlesHandler::handleRequest",
                lambdaExecRole,
                Code.fromBucket(artifactBucket, "asset-service-1.0-SNAPSHOT.jar"),
                Map.of()
        );
        // final Function listArticlesFunction = createLambdaFunction(lambdaExecRole, artifactBucket);
        final Function listArticlesFunction = functionFactory.createLambdaFunction(this, "ListArticlesHandlerFunctionId", lambdaFunctionConfig);


        // define `/articles` resource
        final IResource listArticlesResource = lambdaRestApi.getRoot().addResource("articles");
        listArticlesResource.addMethod("GET", new LambdaIntegration(listArticlesFunction, LambdaIntegrationOptions.builder()
                .proxy(true)
                .build()));

        // define `/articles/{id}` resource
        // define `/catalogs` resource
        // define `/catalogs/{id}/articles` resource
    }

//    private Table createDynamoDbTable(String stage) {
//
//        final TableProps inventoryTableProps = TableProps.builder()
//                .partitionKey(Attribute.builder()
//                        .name("partitionKey")
//                        .type(AttributeType.STRING)
//                        .build())
//                .sortKey(Attribute.builder()
//                        .name("sortKey")
//                        .type(AttributeType.STRING)
//                        .build())
//                .billingMode(BillingMode.PAY_PER_REQUEST)
//                .removalPolicy(StageDecisions.getRemovalPolicy(stage))
//                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder()
//                        .pointInTimeRecoveryEnabled(StageDecisions.enablePitr(stage))
//                        .build())
//                .build();
//
//        return new Table(this, "InventoryTableId", inventoryTableProps);
//    }

    private Role createLambdaExecRole() {
        final RoleProps lambdaRoleProps = RoleProps.builder()
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        return new Role(this, "LambdaExecutionRoleId", lambdaRoleProps);
    }

//    private Function createLambdaFunction(Role lambdaExecRole, IBucket artifactBucket) {
//        final Map<String, String> envVars = new HashMap<>();
//        envVars.put("TABLE_NAME", inventoryTable.getTableName());
//
//        final FunctionProps laFuncProps = FunctionProps.builder()
//                .runtime(Runtime.JAVA_21)
//                .memorySize(1024)
//                .architecture(Architecture.X86_64)
//                .timeout(Duration.seconds(30))
//                .handler("com.sakai.inventory.api.handler.ListArticlesHandler::handleRequest")
//                .code(Code.fromBucket(artifactBucket, artifactObjectKey))
//                .environment(envVars)
//                .role(lambdaExecRole)
//                .build();
//
//        return new Function(this, "ListArticlesHandlerFunction", laFuncProps);
//    }

//    private RestApi createApiGateway(String stage, Function listArticlesFunction) {
//        final StageOptions deployOpt = StageOptions.builder()
//                .stageName(stage)
//                .dataTraceEnabled(StageDecisions.enableDataTrace(stage)) // in prod disabled
//                .loggingLevel(MethodLoggingLevel.ERROR)
//                .build();
//
//        final CorsOptions corsOpt = CorsOptions.builder()
//                .allowOrigins(Cors.ALL_ORIGINS) // for dev allow all origins. Must be changed in prod.
//                .allowMethods(Cors.ALL_METHODS)
//                .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
//                .build();
//
//        final RestApiProps props = RestApiProps.builder()
//                .restApiName("InventoryRestApiGateway")
//                .description("API for Inventory Management System")
//                .deployOptions(deployOpt)
//                .defaultCorsPreflightOptions(corsOpt)
//                .cloudWatchRole(true)
//                .build();
//
//        final RestApi restApi = new RestApi(this, "RestApiGateway", props);
//
//        // define `/articles` resource
//        final IResource listArticlesResource = restApi.getRoot().addResource("articles");
//        listArticlesResource.addMethod("GET", new LambdaIntegration(listArticlesFunction, LambdaIntegrationOptions.builder()
//                .proxy(true)
//                .build()));
//
//        // define `/articles/{id}` resource
//        // define `/catalogs` resource
//        // define `/catalogs/{id}/articles` resource
//
//        return restApi;
//    }

//    private RemovalPolicy determinateRemovalPolicy(String stage) {
//        if (stage != null && stage.equalsIgnoreCase("prod")) {
//            return RemovalPolicy.RETAIN;
//        }
//
//        return RemovalPolicy.DESTROY;
//    }
//
//    private Boolean determinatePitr(String stage) {
//        return stage != null && stage.equalsIgnoreCase("prod");
//    }

//    public Table getInventoryTable() {
//        return inventoryTable;
//    }

//    public void setArtifactBucketName(String artifactBucketName) {
//        this.artifactBucketName = artifactBucketName;
//    }

//    public void setArtifactObjectKey(String artifactObjectKey) {
//        this.artifactObjectKey = artifactObjectKey;
//    }

//    public void setStage(String stage) {
//        this.stage = stage;
//    }
}
