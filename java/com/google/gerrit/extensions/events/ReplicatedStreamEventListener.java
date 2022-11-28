package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

import java.io.IOException;


@ExtensionPoint
public interface ReplicatedStreamEventListener {

    interface Event extends ReplicatedStreamEvent { }

    void onReplicatedStreamEvent(ReplicatedStreamEvent event) throws IOException;
}
