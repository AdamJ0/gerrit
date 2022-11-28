// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.events.PluginEventListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.StringJoiner;

@Singleton
public class PluginEvent {
  private final PluginSetContext<PluginEventListener> listeners;
  private final ReplicatedEventsCoordinator replicatedEventsCoordinator;

  @Inject
  PluginEvent(PluginSetContext<PluginEventListener> listeners, ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    this.listeners = listeners;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;
  }

  public void fire(String pluginName, String type, String data) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    Event e = new Event(pluginName, type, data, replicatedEventsCoordinator.getThisNodeIdentity());
    listeners.runEach(l -> l.onPluginEvent(e));
  }

  /**
   * Cast the event from a ReplicatedStreamEvent to its underlying event type and
   * fire it off for its respective listeners to pick up.
   * @param streamEvent of type ReplicatedStreamEvent to be cast to its underlying type.
   */
  public void fire(ReplicatedStreamEvent streamEvent) {
    if (listeners.isEmpty()) {
      return;
    }
    PluginEvent.Event event = (PluginEvent.Event) streamEvent;
    listeners.runEach(l -> l.onPluginEvent(event));
  }

  @isReplicatedStreamEvent
  private static class Event extends AbstractNoNotifyEvent implements PluginEventListener.Event {
     private final String pluginName;
    private final String type;
    private final String data;

    Event(String pluginName, String type, String data, final String nodeIdentity) {
      super(nodeIdentity);
      this.pluginName = pluginName;
      this.type = type;
      this.data = data;
    }

    @Override
    public String pluginName() {
      return pluginName;
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public String getData() {
      return data;
    }

    @Override
    public String nodeIdentity() {
      return super.getNodeIdentity();
    }

    @Override
    public String className() {
      return this.getClass().getName();
    }

    @Override
    public String projectName() {
      return null;
    }

    @Override
    public void setStreamEventReplicated(boolean replicated) {
      hasBeenReplicated = replicated;
    }

    @Override
    public boolean replicationSuccessful() {
      return hasBeenReplicated;
    }


    @Override
    public String toString() {
      return new StringJoiner(", ", Event.class.getSimpleName() + "[", "]")
              .add("pluginName='" + pluginName + "'")
              .add("type='" + type + "'")
              .add("data='" + data + "'")
              .add("hasBeenReplicated=" + super.hasBeenReplicated)
              .add("eventTimestamp=" + getEventTimestamp())
              .add("eventNanoTime=" + getEventNanoTime())
              .add("nodeIdentity='" + super.getNodeIdentity() + "'")
              .toString();
    }
  }
}
