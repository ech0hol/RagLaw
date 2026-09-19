package com.raglaw.agentscope.routing;

public interface TaskClassifier {
    TaskClassification classify(RoutingRequest request);
}
