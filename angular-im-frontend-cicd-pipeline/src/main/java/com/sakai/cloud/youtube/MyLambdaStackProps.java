package com.sakai.cloud.youtube;

import software.amazon.awscdk.StackProps;

public class MyLambdaStackProps implements StackProps {
    private String stageName;

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }
}
