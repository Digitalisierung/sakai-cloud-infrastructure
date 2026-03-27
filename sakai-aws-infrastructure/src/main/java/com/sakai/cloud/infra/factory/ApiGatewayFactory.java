package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.util.StageDecisions;
import software.amazon.awscdk.services.apigateway.*;
import software.constructs.Construct;

public class ApiGatewayFactory {
    private StageOptions getStageOptions(String stageName) {
        return StageOptions.builder()
                .stageName(stageName)
                .dataTraceEnabled(StageDecisions.enableDataTrace(stageName))
                .loggingLevel(MethodLoggingLevel.ERROR)
                .build();
    }

    private CorsOptions getCorsOptions(String stage) {
        return switch (stage) {
            case "prod", "Prod", "PROD" -> CorsOptions.builder()
                    .allowMethods(Cors.ALL_METHODS)
                    .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                    .build();
            case "dev", "Dev", "DEV" -> CorsOptions.builder()
                    .allowOrigins(Cors.ALL_ORIGINS) // for dev allow all origins. Must be changed in prod.
                    .allowMethods(Cors.ALL_METHODS)
                    .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                    .build();
            default -> throw new IllegalArgumentException("Invalid stage: " + stage);
        };
    }

    private RestApiProps getRestApiProps(String restApiName, String description, String stage) {
        return RestApiProps.builder()
                .restApiName(restApiName)
                .description(description)
                .deployOptions(getStageOptions(stage))
                .defaultCorsPreflightOptions(getCorsOptions(stage))
                .cloudWatchRole(true)
                .build();
    }

    public RestApi createApiGateway(Construct scope, String id, String restApiName, String description, String stage) {
        return new RestApi(scope, id, getRestApiProps(restApiName, description, stage));
    }
}
