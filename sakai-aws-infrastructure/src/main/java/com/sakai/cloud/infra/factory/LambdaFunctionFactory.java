package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.config.LambdaConfigurator;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

public class LambdaFunctionFactory {
    private FunctionProps createFunctionProps(LambdaConfigurator lambdaConfigurator) {
        return FunctionProps.builder()
                .runtime(Runtime.JAVA_21)
                .memorySize(1024)
                .architecture(Architecture.X86_64)
                .timeout(Duration.seconds(30))
                .handler(lambdaConfigurator.handlerName())
                .code(lambdaConfigurator.code())
                .environment(lambdaConfigurator.environmentVars())
                .role(lambdaConfigurator.lambdaExecRole())
                .build();
    }

    public Function createLambdaFunction(Construct scope, String id, LambdaConfigurator lambdaConfigurator) {
        return new Function(scope, id, createFunctionProps(lambdaConfigurator));
    }
}
