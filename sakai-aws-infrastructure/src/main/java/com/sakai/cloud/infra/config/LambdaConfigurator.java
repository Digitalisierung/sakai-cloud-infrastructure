package com.sakai.cloud.infra.config;

import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.lambda.Code;

import java.util.Map;

public record LambdaConfigurator(
        String handlerName,
        Role lambdaExecRole,
        Code code,
        Map<String, String> environmentVars
) {
}
