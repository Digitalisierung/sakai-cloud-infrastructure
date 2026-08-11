package com.sakai.cloud.infra.stack;

import com.sakai.cloud.infra.config.StageConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;

@Deprecated
public class LambdaArtifactStage extends Stage {
    private static final Logger LOGGER = LoggerFactory.getLogger(SakaiApplicationStage.class);

    public LambdaArtifactStage(@NotNull Construct scope, @NotNull String id, @Nullable StageProps props, StageConfigurator stageConfig) {
        super(scope, id, props);

        Environment env = props.getEnv();

        if (env == null || env.getAccount() == null || env.getRegion() == null) {
            throw new RuntimeException("Missing Environment in stageProps.");
        }

        LOGGER.info("Env::getAccount() {}", env.getAccount());
        LOGGER.info("Env::getRegion() {}", env.getRegion());

        // JAR Deploy
        final StackProps backendServiceStackProps = StackProps.builder()
                .description("SAKAI Service. Pipeline fuer Lambda Deploy (JAR Build) — Inventory Management System.")
                .env(env)
                .build();

        final LambdaBuildPipelineStack_DELETE lambdaBuildPipelineStack = new LambdaBuildPipelineStack_DELETE(this, "SakaiLambdaBuildPipelineStackId", backendServiceStackProps, stageConfig);
        lambdaBuildPipelineStack.initializeStack();
    }
}
