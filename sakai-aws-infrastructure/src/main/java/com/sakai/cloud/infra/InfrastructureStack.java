package com.sakai.cloud.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.events.targets.ApiGatewayProps;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfrastructureStack extends Stack {
    private final String ARTIFACT_BUCKET_NAME;
    private final String ARTIFACT_OBJECT_KEY;
    private final Table INVENTORY_TABLE;

    public InfrastructureStack(Construct app, String id, StackProps props) {
        super(app, id, props);

        String stage = (String) this.getNode().tryGetContext("stage");

        ARTIFACT_BUCKET_NAME = (String) this.getNode().tryGetContext("artifactBucketName");
        if (ARTIFACT_BUCKET_NAME == null) throw new RuntimeException("Artifact Bucket Name is not defined");
        ARTIFACT_OBJECT_KEY = (String) this.getNode().tryGetContext("artifactObjectKey");
        if (ARTIFACT_OBJECT_KEY == null) throw new RuntimeException("Artifact Object Key is not defined");

        // === DynamoDB Table ===
        INVENTORY_TABLE = createDynamoDbTable(stage);

        // S3 Artifact Bucket
        IBucket artifactBucket = Bucket.fromBucketName(this, "ArtifactBucketId", ARTIFACT_BUCKET_NAME);

        // === Lambda Function Role ===
        Role lambdaExecRole = createLambdaExecRole();
        INVENTORY_TABLE.grantReadData(lambdaExecRole);

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
        envVars.put("TABLE_NAME", INVENTORY_TABLE.getTableName());

        FunctionProps laFuncProps = FunctionProps.builder()
                .runtime(Runtime.JAVA_21)
                .memorySize(1024)
                .handler("com.sakai.inventory.api.handler.ListArticlesHandler::handleRequest")
                .architecture(Architecture.X86_64)
                .timeout(Duration.seconds(30))
                .environment(envVars)
                .role(lambdaExecRole)
                .code(Code.fromBucket(artifactBucket, ARTIFACT_OBJECT_KEY))
                .build();

        return new Function(this, "ListArticlesHandlerFunction", laFuncProps);
    }

    private RestApi createApiGateway(String stage, Function listArticlesFunction) {
        StageOptions deployOpt = StageOptions.builder()
                .stageName("prod")
                .dataTraceEnabled(!stage.equalsIgnoreCase("prod")) // in prod disabled
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
        if (stage != null && stage.equals("prod")) {
            return RemovalPolicy.RETAIN;
        }

        return RemovalPolicy.DESTROY;
    }

    private Boolean determinatePitr(String stage) {
        return stage != null && stage.equals("prod");
    }

    public Table getInventoryTable() {
        return INVENTORY_TABLE;
    }
}
