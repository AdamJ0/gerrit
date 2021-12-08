package com.google.gerrit.server.replication.processors;

import com.wandisco.gerrit.gitms.shared.events.ReplicatedEvent;


public interface ReplicatedEventProcessor {
//  void publishIncomingReplicatedEvents(EventWrapper newEvent);
  void processIncomingReplicatedEvent(final ReplicatedEvent replicatedEvent);


  /**
   * Stop is used to call unsubscribe at appropriate time.. But as it passes in the this pointer I needed
   * to keep it abstract...
   */
  void stop();
  void subscribeEvent(ReplicatedEventProcessor toCall);
  void unsubscribeEvent(ReplicatedEventProcessor toCall);
}
