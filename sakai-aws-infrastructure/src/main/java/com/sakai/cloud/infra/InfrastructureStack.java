package com.sakai.cloud.infra;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

public class InfrastructureStack extends Stack {
    private final Table inventoryTable;

    public InfrastructureStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        String stage = (String) this.getNode().tryGetContext("stage");

        TableProps inventoryTableProps = TableProps.builder()
                .partitionKey(Attribute.builder()
                        .name("partitionKey")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("sortKey")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(determinateRemovalPolicy(stage))
                .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder()
                        .pointInTimeRecoveryEnabled(determinatePitr(stage))
                        .build())
                .build();

        this.inventoryTable = new Table(this, "InventoryTableId", inventoryTableProps);
    }

    private RemovalPolicy determinateRemovalPolicy(String stage) {
        if (stage != null && stage.equals("prod")) {
            return RemovalPolicy.RETAIN;
        }

        return RemovalPolicy.DESTROY;
    }

    private Boolean determinatePitr(String stage) {
        return stage != null && stage.equals("prod");
    }

    public Table getInventoryTable() {
        return inventoryTable;
    }
}
