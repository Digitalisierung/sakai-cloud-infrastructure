package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.config.ApiGatewayConfigurator;
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
        return StageDecisions.getCorsOptions(stage);
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

    public RestApi createApiGateway(Construct scope, String id, ApiGatewayConfigurator apiGatewayConfigurator) {
        return new RestApi(scope, id, getRestApiProps(apiGatewayConfigurator.restApiName(), apiGatewayConfigurator.description(), apiGatewayConfigurator.stage()));
    }
}
