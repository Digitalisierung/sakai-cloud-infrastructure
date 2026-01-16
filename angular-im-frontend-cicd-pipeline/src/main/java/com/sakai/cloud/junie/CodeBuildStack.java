package com.sakai.cloud.junie;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.constructs.Construct;

import java.util.List;

public class CodeBuildStack extends Stack {
    public CodeBuildStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        GitHubSourceProps gitHubSourceProps = GitHubSourceProps.builder()
                .owner("Digitalisierung")
                .repo("im-frontend")
                .branchOrRef("develop")
                .webhook(true)
                .webhookFilters(List.of(
                        FilterGroup.inEventOf(EventAction.PUSH)
                                .andBranchIs("develop")
                ))
                .build();

        IProject codeBuildProject = Project.Builder.create(this, "ImFrontendCodeBuildProject")
                .projectName("ImFrontendBuildProject")
                .description("Automatisches Build Projekt für im-frontend auf GitHub")
                .source(Source.gitHub(gitHubSourceProps))
                .environment(BuildEnvironment.builder()
                        .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5)
                        .computeType(ComputeType.SMALL)
                        .build())
                .timeout(Duration.minutes(20))
                .build();
    }
}
