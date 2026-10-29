package com.sakai.cloud.infra.factory;

import com.sakai.cloud.infra.config.StageConfigurator;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

/**
 * Factory-Klasse zur Erstellung von Amazon DynamoDB Tabellen.
 * Definiert das Tabellenschema (Partition Key, Sort Key) und wendet
 * stage-spezifische Einstellungen wie Removal Policy und Point-in-Time Recovery an.
 */
public class DynamoDbTableFactory {
    /**
     * Erstellt eine DynamoDB Tabelle mit vordefiniertem Schema für das Inventory.
     *
     * @param scope       Der CDK-Scope.
     * @param id          Die logische ID der Tabelle.
     * @param stageConfig Die Stage-Konfiguration.
     * @return Eine neue DynamoDB Table-Instanz.
     */
    public Table createDynamoDbTable(Construct scope, String id, StageConfigurator stageConfig) {
        return new Table(scope, id, createTableProps(stageConfig));
    }

    public void addGlobalSecondaryIndex(Table table, String indexName, String partitionKey, String sortKey) {
        GlobalSecondaryIndexProps gsi = GlobalSecondaryIndexProps.builder()
                .indexName(indexName)
                .partitionKey(Attribute.builder()
                        .name(partitionKey)
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name(sortKey)
                        .type(AttributeType.STRING)
                        .build())
                .projectionType(ProjectionType.ALL)
                .build();

        table.addGlobalSecondaryIndex(gsi);
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
