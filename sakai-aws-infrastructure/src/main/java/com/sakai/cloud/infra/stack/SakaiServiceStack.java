package com.sakai.cloud.infra.stack;

import com.sakai.cloud.infra.config.ApiGatewayConfigurator;
import com.sakai.cloud.infra.config.StageConfigurator;
import com.sakai.cloud.infra.factory.ApiGatewayFactory;
import com.sakai.cloud.infra.factory.DynamoDbTableFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.apigateway.IResource;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.LambdaIntegrationOptions;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Der Stack für die eigentlichen Backend-Services des Sakai Inventory Management Systems.
 * Er erstellt und konfiguriert die primären Ressourcen wie die DynamoDB-Tabelle,
 * das API Gateway und die Lambda-Funktionen für die Geschäftslogik.
 */
public class SakaiServiceStack extends Stack {
    private static final Logger LOGGER = LoggerFactory.getLogger(SakaiServiceStack.class);

    // sakai-lambda-artifacts
    private final String artifactBucketName;
    // asset-service-1.0-SNAPSHOT.jar
    //private String artifactObjectKey;
    private Table inventoryTable;
    // DEV, TEST, PROD, etc.
    private StageConfigurator stageConfig;

    public SakaiServiceStack(Construct app, String id, StackProps props, StageConfigurator stageConfig) {
        super(app, id, props);

        this.stageConfig = stageConfig;
        this.artifactBucketName = StringParameter.valueForStringParameter(this, "/sakai/" + stageConfig.stageName() + "/lambda/artifact-bucket-name");

        Tags.of(this).add("Project", "Sakai");
        Tags.of(this).add("Stage", stageConfig.stageName());
        Tags.of(this).add("ManagedBy", "CDK");
        Tags.of(this).add("Owner", "Digitalisierung");
        Tags.of(this).add("Service", "InventoryManagement");
    }

    /**
     * Initialisiert den Stack, indem er die DynamoDB-Tabelle, die IAM-Rollen,
     * die Lambda-Funktionen und das API Gateway erstellt und miteinander verknüpft.
     */
    public void initializeStack() {

        DynamoDbTableFactory dbTableFactory = new DynamoDbTableFactory();
        ApiGatewayFactory apiGatewayFactory = new ApiGatewayFactory();


        // === DynamoDB TABLE ===
        //inventoryTable = createDynamoDbTable(stage);
        inventoryTable = dbTableFactory.createDynamoDbTable(this, "InventoryTableId", stageConfig);

        // === S3 Artifact BUCKET ===
        final IBucket artifactBucket = Bucket.fromBucketName(this, "ArtifactBucketId", artifactBucketName);

        // === IAM ROLE | for Lambda Function ===
        final Role lambdaExecRole = createLambdaExecRole();
        inventoryTable.grantReadData(lambdaExecRole);


        // === LAMBDA FUNCTION for ListArticles Handler ===

        // Versuche den Key aus SSM zu lesen...
        String jarKeyParameter = StringParameter.valueForStringParameter(this, "/sakai/" + stageConfig.stageName() + "/lambda/artifact-key");

        FunctionProps lambdaFunctionProps = FunctionProps.builder()
                .architecture(Architecture.X86_64)
                .code(Code.fromBucket(artifactBucket, jarKeyParameter))
                .description("Lambda-Funktion, die eine Liste aller Artikel aus der DynamoDB zurückgibt.")
                .environment(Map.of(
                        "TABLE_NAME", inventoryTable.getTableName()
                ))
                .handler("com.sakai.inventory.api.handler.ListArticlesHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_21)
                .role(lambdaExecRole)
                .timeout(Duration.seconds(30))
                .build();

        final Function listArticlesFunction = new Function(this, "ListArticlesHandlerFunctionId", lambdaFunctionProps);


        // === API GATEWAY ===
        // final RestApi lambdaRestApi = createApiGateway(stage, listArticlesHandler);
        ApiGatewayConfigurator apiGatewayConfigurator = new ApiGatewayConfigurator(
                "InventoryServiceRestApiGateway",
                "API für/von Inventory Management System.",
                stageConfig
        );
        final RestApi lambdaRestApi = apiGatewayFactory.createApiGateway(this, "ApiGatewayId", apiGatewayConfigurator);

        // define `/articles` resource
        final IResource listArticlesResource = lambdaRestApi.getRoot().addResource("articles");
        listArticlesResource.addMethod("GET", new LambdaIntegration(listArticlesFunction, LambdaIntegrationOptions.builder()
                .proxy(true)
                .build()));

        // define `/articles/{id}` resource
        // define `/catalogs` resource
        // define `/catalogs/{id}/articles` resource
    }

    private Role createLambdaExecRole() {
        final RoleProps lambdaRoleProps = RoleProps.builder()
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .description("IAM-Rolle für die Ausführung der Lambda-Funktion des Inventory-Services.")
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        return new Role(this, "LambdaExecutionRoleId", lambdaRoleProps);
    }

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
}
