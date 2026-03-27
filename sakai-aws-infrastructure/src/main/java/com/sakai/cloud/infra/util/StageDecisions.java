package com.sakai.cloud.infra.util;

import software.amazon.awscdk.RemovalPolicy;

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
}
