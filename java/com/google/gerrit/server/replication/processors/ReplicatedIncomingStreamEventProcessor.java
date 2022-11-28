package com.google.gerrit.server.replication.processors;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.server.replication.ReplicatedEventRequestScope;
import com.google.gerrit.server.replication.SingletonEnforcement;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.wandisco.gerrit.gitms.shared.events.ReplicatedEvent;

import static com.wandisco.gerrit.gitms.shared.events.EventWrapper.Originator.REPLICATED_STREAM_EVENT;

public class ReplicatedIncomingStreamEventProcessor extends AbstractReplicatedEventProcessor{

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private ReplicatedEventRequestScope replicatedEventRequestScope;

    public ReplicatedIncomingStreamEventProcessor(ReplicatedEventsCoordinator replicatedEventsCoordinator,
                                                  ReplicatedEventRequestScope replicatedEventRequestScope) {
        super(REPLICATED_STREAM_EVENT, replicatedEventsCoordinator);
        this.replicatedEventRequestScope = replicatedEventRequestScope;
        logger.atInfo().log("Creating main processor for event type: %s", eventType);
        subscribeEvent(this);
        SingletonEnforcement.registerClass(ReplicatedIncomingStreamEventProcessor.class);
    }


    /**
     * Post the stream event to the event broker. The event broker will then pass the event
     * @param replicatedEvent
     */
    @Override
    public void processIncomingReplicatedEvent(ReplicatedEvent replicatedEvent) {
        ReplicatedStreamEvent replicatedStreamEvent = (ReplicatedStreamEvent) replicatedEvent;
        //Set replicated to true, so we do not attempt to replicate this event again.
        replicatedStreamEvent.setStreamEventReplicated(true);

        // Set the request scope in order to get the ReviewDb provider. The request scope is set for the Thread Local
        // storage request context.
        ReplicatedEventRequestScope.Context old =
                replicatedEventsCoordinator.getReplicatedEventRequestScope().set(replicatedEventRequestScope
                        .newContext(replicatedEventsCoordinator.getSchemaFactory()));

        try {
            // Post the replicatedStreamEvent to the EventBroker where it will be subsequently fired off for
            // any listeners of onReplicatedStreamEvent.
            replicatedEventsCoordinator.getEventBroker().postEvent(replicatedStreamEvent);
        } finally {
            replicatedEventRequestScope.set(old);
        }
    }

    @Override
    public void stop() {
        unsubscribeEvent(this);
    }
}
