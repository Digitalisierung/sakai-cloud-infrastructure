package com.sakai.cloud.infra.util;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;

import java.util.List;

public final class StageDecisions {
    private StageDecisions() {
        super();
    }

    public static RemovalPolicy getRemovalPolicy(String stage) {
        return "prod".equalsIgnoreCase(stage) ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;
    }

    public static boolean enablePitr(String stage) {
        return "prod".equalsIgnoreCase(stage);
    }

    public static boolean enableDataTrace(String stage) {
        return !"dev".equalsIgnoreCase(stage);
    }

    public static CorsOptions getCorsOptions(String stage) {
        return switch (stage) {
            case "dev", "Dev", "DEV" -> CorsOptions.builder()
                    .allowOrigins(Cors.ALL_ORIGINS) // Später mit List.of()
                    .allowMethods(Cors.ALL_METHODS)
                    .allowHeaders(Cors.DEFAULT_HEADERS)
                    .build();
            default -> CorsOptions.builder()
                    .allowOrigins(List.of("*")) // TODO: Warum? Ist das richtig? Ist es notwendig??
                    .allowMethods(Cors.ALL_METHODS)
                    .allowHeaders(Cors.DEFAULT_HEADERS) // alternativ List.of("Content-Type", "Authorization")
                    .build();
        };
    }
}
