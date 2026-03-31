package com.sakai.cloud.infra.stack;

import com.sakai.cloud.infra.config.StageConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Stage;
import software.amazon.awscdk.StageProps;
import software.constructs.Construct;

public class SakaiApplicationStage extends Stage {
    private static final Logger LOGGER = LoggerFactory.getLogger(SakaiApplicationStage.class);

    public SakaiApplicationStage(Construct scope, String id, StageProps stageProps, StageConfigurator stageConfig) {
        super(scope, id, stageProps);

        final Environment env = stageProps.getEnv();

        if (env == null || env.getAccount() == null || env.getRegion() == null) {
            throw new RuntimeException("Missing Environment in stageProps.");
        }

        LOGGER.info("Env::getAccount() {}", env.getAccount());
        LOGGER.info("Env::getRegion() {}", env.getRegion());

        final StackProps serviceStackProps = StackProps.builder()
                .description("Sakai Service Stack.")
                .env(env)
                .build();

        final SakaiServiceStack sakaiServiceStack = new SakaiServiceStack(this, "SakaiServiceStackId", serviceStackProps, stageConfig);
        sakaiServiceStack.initializeStack();
    }
}
