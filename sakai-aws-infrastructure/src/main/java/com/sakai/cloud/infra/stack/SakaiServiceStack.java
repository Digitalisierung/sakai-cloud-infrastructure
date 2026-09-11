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

    private final String artifactBucketName;
    private Table inventoryTable;
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
        dbTableFactory.addGlobalSecondaryIndex(inventoryTable, "GSI_entityType", "entityType", "sortKey");
        dbTableFactory.addGlobalSecondaryIndex(inventoryTable, "GSI_ItemsInCatalogs", "catalogId", "sortKey");

        // === S3 Artifact BUCKET ===
        final IBucket artifactBucket = Bucket.fromBucketName(this, "ArtifactBucketId", artifactBucketName);

        // === IAM ROLE | for Lambda Function ===
        final Role lambdaExecRole = createLambdaExecRole("LambdaExecutionRoleId");
        inventoryTable.grantReadData(lambdaExecRole);

        Role updateInventoryTableRole = createLambdaExecRole("UpdateInventoryTableRoleId");
        inventoryTable.grantReadWriteData(updateInventoryTableRole);


        // === LAMBDA FUNCTION for ListArticles Handler ===

        // Versuche den Key aus SSM zu lesen...
        String jarKeyParameter = StringParameter.valueForStringParameter(this, "/sakai/" + stageConfig.stageName() + "/lambda/asset-service/artifact-key");

        // List Articles
        FunctionProps lambdaFunctionProps = FunctionProps.builder()
                .architecture(Architecture.X86_64)
                .code(Code.fromBucket(artifactBucket, jarKeyParameter))
                .description("KHACHI KAMRI KHAN. Lambda-Funktion, die eine Liste aller Artikel aus der DynamoDB zuruckgibt.")
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

        // Get Article
        FunctionProps getArticleFunctionProps = FunctionProps.builder()
                .architecture(Architecture.X86_64)
                .code(Code.fromBucket(artifactBucket, jarKeyParameter))
                .description("KHACHI KAMRI KHAN. Lambda Funktion, welche ein Artikel nach seinem ID findet.")
                .environment(Map.of(
                        "TABLE_NAME", inventoryTable.getTableName()
                ))
                .handler("com.sakai.inventory.api.handler.GetArticleHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_21)
                .role(lambdaExecRole)
                .timeout(Duration.seconds(30))
                .build();

        Function getArticleFunction = new Function(this, "GetArticleHandlerFunctionId", getArticleFunctionProps);


        // Catalog-Service
        String catServiceJarKeyParam = StringParameter.valueForStringParameter(this, "/sakai/" + stageConfig.stageName() + "/lambda/catalog-service/artifact-key");

        FunctionProps listCatalogsFunctionProps = FunctionProps.builder()
                .architecture(Architecture.X86_64)
                .code(Code.fromBucket(artifactBucket, catServiceJarKeyParam))
                .description("KHACHI KAMRI kHAN. Lambda Funktion, welche eine Liste von allen Katalogs zuruckgibt.")
                .environment(Map.of(
                        "TABLE_NAME", inventoryTable.getTableName()
                ))
                .handler("com.sakai.inventory.api.handler.ListCatalogsHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_21)
                .role(lambdaExecRole)
                .timeout(Duration.seconds(30))
                .build();

        Function listCatalogsFunction = new Function(this, "ListCatalogsFunctionId", listCatalogsFunctionProps);

        FunctionProps getCatalogFunctionProps = FunctionProps.builder()
                .architecture(Architecture.X86_64)
                .code(Code.fromBucket(artifactBucket, catServiceJarKeyParam))
                .description("KHACHI KAMRI kHAN. Lambda Funktion, welche einen Katalog nach seinem ID findet.")
                .environment(Map.of(
                        "TABLE_NAME", inventoryTable.getTableName()
                ))
                .handler("com.sakai.inventory.api.handler.GetCatalogHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_21)
                .role(lambdaExecRole)
                .timeout(Duration.seconds(30))
                .build();

        Function getCatalogFunction = new Function(this, "GetCatalogFunctionId", getCatalogFunctionProps);

        FunctionProps updateCatalogFunctionProps = FunctionProps.builder()
                .description("KHACHI KAMRI kHAN. Lambda Funktion, welche die Katalog-Eigenschaften (Name, Beschreibung etc.) eines Katalogs updatet.")
                .environment(Map.of(
                        "TABLE_NAME", inventoryTable.getTableName()
                ))
                .architecture(Architecture.X86_64)
                .handler("com.sakai.inventory.api.handler.UpdateCatalogHandler::handleRequest")
                .memorySize(1024)
                .runtime(Runtime.JAVA_21)
                .code(Code.fromBucket(artifactBucket, catServiceJarKeyParam))
                .role(updateInventoryTableRole)
                .timeout(Duration.seconds(30))
                .build();

        Function updateCatalogFunction = new Function(this, "UpdateCatelogFunctionId", updateCatalogFunctionProps);


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
        listArticlesResource.addMethod(
                "GET",
                new LambdaIntegration(listArticlesFunction, LambdaIntegrationOptions.builder()
                        .proxy(true)
                        .build()
                )
        );

        // define `/articles/{id}` resource
        IResource getArticleResource = listArticlesResource.addResource("{id}");
        getArticleResource.addMethod(
                "GET",
                new LambdaIntegration(getArticleFunction, LambdaIntegrationOptions.builder()
                        .proxy(true)
                        .build()
                )
        );

        // define `/catalogs` resource
        IResource listCatalogsResource = lambdaRestApi.getRoot().addResource("catalogs");
        listCatalogsResource.addMethod(
                "GET",
                new LambdaIntegration(listCatalogsFunction, LambdaIntegrationOptions.builder()
                        .proxy(true)
                        .build()
                )
        );

        // define `/catalogs/{id}` resource
        IResource getCatalogResource = listCatalogsResource.addResource("{id}");
        getCatalogResource.addMethod(
                "GET",
                new LambdaIntegration(getCatalogFunction, LambdaIntegrationOptions.builder()
                        .proxy(true)
                        .build()
                )
        );

        // define 'PUT /catalogs/{id}'
        getCatalogResource.addMethod(
                "PUT",
                new LambdaIntegration(updateCatalogFunction, LambdaIntegrationOptions.builder()
                        .proxy(true)
                        .build()
                )
        );

        // define `/catalogs/{id}/articles` resource
    }

    private Role createLambdaExecRole(String id) {
        final RoleProps lambdaRoleProps = RoleProps.builder()
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .description("KHACHI KAMRI KHAN. IAM-Rolle für Lambda-Funktionen zum Schreiben in CloudWatch Logs.")
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")
                ))
                .build();

        return new Role(this, id, lambdaRoleProps);
    }
}
