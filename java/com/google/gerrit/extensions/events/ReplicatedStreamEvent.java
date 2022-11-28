package com.google.gerrit.extensions.events;

public interface ReplicatedStreamEvent {
    String nodeIdentity();
    String className();
    String projectName();
    void setStreamEventReplicated(boolean replicated);
    boolean replicationSuccessful();
}
