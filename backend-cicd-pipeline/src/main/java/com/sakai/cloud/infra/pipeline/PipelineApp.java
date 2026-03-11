package com.sakai.cloud.infra.pipeline;

import software.amazon.awscdk.App;

public class PipelineApp {
    public static void main(String[] args) {
        App app = new App();
        app.synth();
    }
}
