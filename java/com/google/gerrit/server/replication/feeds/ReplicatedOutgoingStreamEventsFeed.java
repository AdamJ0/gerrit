package com.google.gerrit.server.replication.feeds;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.extensions.events.ReplicatedStreamEventListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.events.EventBroker;
import com.google.gerrit.server.events.SkipReplication;
import com.google.gerrit.server.replication.GerritEventFactory;
import com.google.gerrit.server.replication.SingletonEnforcement;
import com.google.gerrit.server.replication.configuration.ReplicatedConfiguration;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.inject.Inject;

public class ReplicatedOutgoingStreamEventsFeed extends ReplicatedOutgoingEventsFeedCommon implements LifecycleListener {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private ReplicatedConfiguration configuration;
    private EventBroker eventBroker;

    public static class Module extends LifecycleModule {
        @Override
        protected void configure() {
            bind(ReplicatedOutgoingStreamEventsFeed.class);
            /* We need to bind the listener to this class as it's required to start the lifecycle*/
            listener().to(ReplicatedOutgoingStreamEventsFeed.class);
        }
    }


    @Inject
    protected ReplicatedOutgoingStreamEventsFeed(ReplicatedConfiguration configuration,
                                                 ReplicatedEventsCoordinator eventsCoordinator,
                                                 EventBroker eventBroker) {
        super(eventsCoordinator);
        this.eventBroker = eventBroker;
        this.configuration = configuration;
        SingletonEnforcement.registerClass(ReplicatedOutgoingStreamEventsFeed.class);
    }


    /**
     * Both listeners are bound here. The OutgoingStreamEventsFeedListener is where stream events will be fed into
     * The ReplicatedStreamEventsApiListener will mainly be responsible for acting on ReplicatedStreamEvents fired from
     * the eventBroker on remote sites. The eventBroker is fired on remote sites by the
     * ReplicatedIncomingStreamEventProcessor. The event file is read in on a remote site and the incoming processor
     * will pass the event to the eventBroker to be fired off for the ReplicatedStreamEventsApiListener. This
     * listener will then determine what the underlying event type is and call the fire method for the appropriate
     * listener for that type.
     */
    @Override
    public void start() {
        logger.atFine().log("Registering OutgoingStreamEventsFeedListener and ReplicatedStreamEventsApiListener");
        this.eventBroker.registerReplicatedStreamEventListener("OutgoingStreamEventsFeedListener", this.listener);
        this.eventBroker.registerReplicatedStreamEventListener("ReplicatedStreamEventsApiListener",
                replicatedEventsCoordinator.getReplicatedStreamEventsApiListener());
    }

    @Override
    public void stop() {
        SingletonEnforcement.unregisterClass(ReplicatedOutgoingStreamEventsFeed.class);
    }

    /**
     * There are two replicated stream event listeners registered.
     * This listener is registered against this thread with the EventBroker and will pick up on
     * ReplicatedStreamEvent types fired from the broker. Within this thread we
     * offer the replicated stream events to a queue to be replicated.
     *
     * The other listener is registered in ReplicatedStreamEventsApiListener
     * and it is responsible for playing out ReplicatedStreamEvents received on remote sites.
     */
    public final ReplicatedStreamEventListener listener = event -> {
        if(!isReplicatedStreamEventToBeSkipped(event)) {
            logger.atFine().log("Received stream event {}", event.className());
            replicatedEventsCoordinator.queueEventForReplication(GerritEventFactory.createReplicatedStreamEvent(event));
        }
    };

    /**
     * isEventToBeSkipped uses 3 things.
     * 1) has the event previously been replicated - if so we don't do it again!!
     * 2) IS the event in a list of events we are not to replicate ( a skip list )
     * 3) Is the event annotated with the @SkipReplication annotation, if it is, skip it.
     *    Using the SkipReplication annotation should be used with caution as there are normally
     *    multiple events associated with a given operation in Gerrit and skipping one could
     *    leave the repository in a bad state.
     *
     * @param streamEvent
     * @return
     */

    public boolean isReplicatedStreamEventToBeSkipped(ReplicatedStreamEvent streamEvent) {
        if (streamEvent.replicationSuccessful()) {
            // don't cause cyclic loop replicating forever./
            return true;
        }
        //Check if we are to skip this extension event type
        if(streamEvent.getClass().isAnnotationPresent(SkipReplication.class)){
            return true;
        }

        return isEventInSkipList(streamEvent);
    }


    /**
     * This checks against the list of event class names to be skipped
     * Skippable events are configured by a parameter in the application.properties
     * as a comma separated list of class names for event types, e.g.
     * ChangeDeleted, ChangeMerged
     *
     * @param event
     * @return
     */
    public boolean isEventInSkipList(ReplicatedStreamEvent event) {
        //Doesn't matter if the list is empty, check if the list contains the class name.
        //All events are stored in the list as lowercase, so we check for our lowercase class name.
        return configuration.getEventSkipList().contains(event.getClass().getSimpleName().toLowerCase()); //short name of the class
    }
}
