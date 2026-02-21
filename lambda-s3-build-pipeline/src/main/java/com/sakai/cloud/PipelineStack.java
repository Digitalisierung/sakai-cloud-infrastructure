package com.sakai.cloud;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.constructs.Construct;

public class PipelineStack extends Stack {
    public PipelineStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        // 1. S3 Bucket für Artefakte (JAR)
        Bucket bucket = Bucket.Builder.create(this, "s3-artifact-bucket-id")
                .encryption(BucketEncryption.S3_MANAGED)
                .autoDeleteObjects(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .versioned(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .build();


    }
}
