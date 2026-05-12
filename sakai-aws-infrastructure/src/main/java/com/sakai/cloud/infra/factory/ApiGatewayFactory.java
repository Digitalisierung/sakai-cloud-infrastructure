package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.config.ApiGatewayConfigurator;
import com.sakai.cloud.infra.config.StageConfigurator;
import software.amazon.awscdk.services.apigateway.*;
import software.constructs.Construct;

import java.util.List;

public class ApiGatewayFactory {
    private StageOptions getStageOptions(StageConfigurator stageConfig) {
        return StageOptions.builder()
                .stageName(stageConfig.stageName())
                .dataTraceEnabled(stageConfig.apiGatewayDataTraceEnabled())
                .loggingLevel(MethodLoggingLevel.ERROR)
                .build();
    }

    private CorsOptions getCorsOptions(StageConfigurator stageConfig) {
        return switch (stageConfig.stageName()) {
            case "prod", "Prod", "PROD" -> CorsOptions.builder()
                    .allowOrigins(List.of("*")) // TODO: Warum? Ist das richtig? Ist es notwendig?? (auf bekannte Domains einschränken)
                    .allowMethods(Cors.ALL_METHODS)
                    .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                    .build();
            default -> CorsOptions.builder()
                    .allowOrigins(Cors.ALL_ORIGINS) // Später nur für dev (Test wie Prod)
                    .allowMethods(Cors.ALL_METHODS)
                    .allowHeaders(Cors.DEFAULT_HEADERS)
                    .build();
        };
    }

    private RestApiProps getRestApiProps(ApiGatewayConfigurator apiGatewayConfigurator) {
        return RestApiProps.builder()
                .restApiName(apiGatewayConfigurator.restApiName())
                .description(apiGatewayConfigurator.description())
                .deployOptions(getStageOptions(apiGatewayConfigurator.stageConfig()))
                .defaultCorsPreflightOptions(getCorsOptions(apiGatewayConfigurator.stageConfig()))
                .cloudWatchRole(true)
                .build();
    }

    public RestApi createApiGateway(Construct scope, String id, ApiGatewayConfigurator apiGatewayConfigurator) {
        return new RestApi(scope, id, getRestApiProps(apiGatewayConfigurator));
    }
}
