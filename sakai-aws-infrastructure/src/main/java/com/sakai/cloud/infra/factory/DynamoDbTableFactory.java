package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.util.StageDecisions;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

public class DynamoDbTableFactory {
    public Table createDynamoDbTable(Construct scope, String id, String stage) {
        return new Table(scope, id, createTableProps(stage));
    }

    private TableProps createTableProps(String stage) {
        return TableProps.builder()
                .partitionKey(Attribute.builder()
                        .name("partitionKey")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("sortKey")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(StageDecisions.getRemovalPolicy(stage))
                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder()
                        .pointInTimeRecoveryEnabled(StageDecisions.enablePitr(stage))
                        .build())
                .build();
    }
}
