// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Tracks group objects in memory for efficient access.
 */
@Singleton
public class GroupCacheImpl implements GroupCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BYID_NAME = "groups";
  private static final String BYNAME_NAME = "groups_byname";
  private static final String BYUUID_NAME = "groups_byuuid";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, AccountGroup.Id.class, new TypeLiteral<Optional<InternalGroup>>() {
        })
            .maximumWeight(Long.MAX_VALUE)
            .loader(ByIdLoader.class);

        cache(BYNAME_NAME, String.class, new TypeLiteral<Optional<InternalGroup>>() {
        })
            .maximumWeight(Long.MAX_VALUE)
            .loader(ByNameLoader.class);

        cache(BYUUID_NAME, String.class, new TypeLiteral<Optional<InternalGroup>>() {
        })
            .maximumWeight(Long.MAX_VALUE)
            .loader(ByUUIDLoader.class);

        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.Id, Optional<InternalGroup>> byId;
  private final LoadingCache<String, Optional<InternalGroup>> byName;
  private final LoadingCache<String, Optional<InternalGroup>> byUUID;
  private final ReplicatedEventsCoordinator replicatedEventsCoordinator;


  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) LoadingCache<AccountGroup.Id, Optional<InternalGroup>> byId,
      @Named(BYNAME_NAME) LoadingCache<String, Optional<InternalGroup>> byName,
      @Named(BYUUID_NAME) LoadingCache<String, Optional<InternalGroup>> byUUID,
      ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;

    attachToReplication();
  }

  /**
   * Attach to replication the caches that this object uses.
   * N.B. we do not need to hook in the cache listeners if replication is disabled.
   */
  final void attachToReplication() {
    if( !replicatedEventsCoordinator.isReplicationEnabled() ){
      logger.atInfo().log("Replication is disabled - not hooking in GroupCache listeners.");
      return;
    }
    replicatedEventsCoordinator.getReplicatedIncomingCacheEventProcessor().watchCache(BYID_NAME, this.byId);
    replicatedEventsCoordinator.getReplicatedIncomingCacheEventProcessor().watchCache(BYNAME_NAME, this.byName);
    replicatedEventsCoordinator.getReplicatedIncomingCacheEventProcessor().watchCache(BYUUID_NAME, this.byUUID);
  }

  /**
   *  Asks the replicated coordinator for the instance of the ReplicatedOutgoingCacheEventsFeed and calls
   *  replicateEvictionFromCache on it.
   * @param name : Name of the cache to evict from.
   * @param value : Value to evict from the cache.
   */
  private void replicateEvictionFromCache(String name, Object value) {
    if(replicatedEventsCoordinator.isReplicationEnabled()) {
      replicatedEventsCoordinator.getReplicatedOutgoingCacheEventsFeed().replicateEvictionFromCache(name, value);
    }
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.Id groupId) {
    try {
      return byId.get(groupId);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load group %s", groupId);
      return Optional.empty();
    }
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.NameKey name) {
    if (name == null) {
      return Optional.empty();
    }
    try {
      return byName.get(name.get());
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up group %s by name", name.get());
      return Optional.empty();
    }
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.UUID groupUuid) {
    if (groupUuid == null) {
      return Optional.empty();
    }

    try {
      return byUUID.get(groupUuid.get());
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up group %s by uuid", groupUuid.get());
      return Optional.empty();
    }
  }

  @Override
  public void evict(AccountGroup.Id groupId) {
    if (groupId != null) {
      logger.atFine().log("Evict group %s by ID", groupId.get());
      byId.invalidate(groupId);
      replicateEvictionFromCache(BYID_NAME, groupId);
    }
  }

  @Override
  public void evict(AccountGroup.NameKey groupName) {
    if (groupName != null) {
      logger.atFine().log("Evict group '%s' by name", groupName.get());
      byName.invalidate(groupName.get());
      replicateEvictionFromCache(BYNAME_NAME, groupName);
    }
  }

  @Override
  public void evict(AccountGroup.UUID groupUuid) {
    if (groupUuid != null) {
      logger.atFine().log("Evict group %s by UUID", groupUuid.get());
      byUUID.invalidate(groupUuid.get());
      replicateEvictionFromCache(BYUUID_NAME, groupUuid);
    }
  }

  @Override
  public void evict(AccountGroup.UUID groupUuid, boolean shouldReplicate) {
    if (groupUuid != null) {
      logger.atFine().log("Evict group %s by UUID", groupUuid.get());
      byUUID.invalidate(groupUuid.get());
      if (shouldReplicate) {
        replicateEvictionFromCache(BYUUID_NAME, groupUuid);
      }
    }
  }

  static class ByIdLoader extends CacheLoader<AccountGroup.Id, Optional<InternalGroup>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ByIdLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public Optional<InternalGroup> load(AccountGroup.Id key) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading group %s by ID", key)) {
        return groupQueryProvider.get().byId(key);
      }
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<InternalGroup>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ByNameLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public Optional<InternalGroup> load(String name) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading group '%s' by name", name)) {
        return groupQueryProvider.get().byName(new AccountGroup.NameKey(name));
      }
    }
  }

  static class ByUUIDLoader extends CacheLoader<String, Optional<InternalGroup>> {
    private final Groups groups;

    @Inject
    ByUUIDLoader(Groups groups) {
      this.groups = groups;
    }

    @Override
    public Optional<InternalGroup> load(String uuid) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading group %s by UUID", uuid)) {
        return groups.getGroup(new AccountGroup.UUID(uuid));
      }
    }
  }
}
