package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.config.ApiGatewayConfigurator;
import com.sakai.cloud.infra.config.StageConfigurator;
import software.amazon.awscdk.services.apigateway.MethodLoggingLevel;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.constructs.Construct;

public class ApiGatewayFactory {
    private StageOptions getStageOptions(StageConfigurator stageConfig) {
        return StageOptions.builder()
                .stageName(stageConfig.stageName())
                .dataTraceEnabled(/*StageDecisions.enableDataTrace(stageName)*/ stageConfig.apiGatewayDataTraceEnabled())
                .loggingLevel(MethodLoggingLevel.ERROR)
                .build();
    }

//    private CorsOptions getCorsOptions(String stage) {
//        return StageDecisions.getCorsOptions(stage);
//    }

    private RestApiProps getRestApiProps(ApiGatewayConfigurator apiGatewayConfigurator) {
        return RestApiProps.builder()
                .restApiName(apiGatewayConfigurator.restApiName())
                .description(apiGatewayConfigurator.description())
                .deployOptions(getStageOptions(apiGatewayConfigurator.stageConfig()))
                .defaultCorsPreflightOptions(apiGatewayConfigurator.stageConfig().stageOptions())
                .cloudWatchRole(true)
                .build();
    }

    public RestApi createApiGateway(Construct scope, String id, ApiGatewayConfigurator apiGatewayConfigurator) {
        return new RestApi(scope, id, getRestApiProps(apiGatewayConfigurator));
    }
}
