package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.config.StageConfigurator;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

public class DynamoDbTableFactory {
    public Table createDynamoDbTable(Construct scope, String id, StageConfigurator stageConfig) {
        return new Table(scope, id, createTableProps(stageConfig));
    }

    private TableProps createTableProps(StageConfigurator stageConfig) {
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
                .removalPolicy(stageConfig.removalPolicy())
                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder()
                        .pointInTimeRecoveryEnabled(stageConfig.dynamoDbPitrEnabled())
                        .build())
                .tableName("InventoryTable-" + stageConfig.stageName())
                // Hinweis: TableProps hat keine direkte .description() Methode im CDK für Java.
                // Beschreibungen werden oft über Tags oder in der Dokumentation gelöst.
                .build();
    }
}
