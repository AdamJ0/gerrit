package com.google.gerrit.server.replication.processors;

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.replication.customevents.CacheKeyWrapper;
import com.google.gerrit.server.replication.customevents.CacheObjectCallWrapper;
import com.google.gerrit.server.replication.ReplicatedCacheWrapper;
import com.google.gerrit.server.replication.ReplicatorMetrics;
import com.google.gerrit.server.replication.SingletonEnforcement;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.gerrit.server.replication.exceptions.ReplicatedEventsUnknownTypeException;
import com.google.gerrit.server.project.ProjectCache;
import com.wandisco.gerrit.gitms.shared.events.ReplicatedEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.wandisco.gerrit.gitms.shared.events.EventWrapper.Originator.CACHE_EVENT;

public class ReplicatedIncomingCacheEventProcessor extends AbstractReplicatedEventProcessor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<String, ReplicatedCacheWrapper> caches = new ConcurrentHashMap<>();
  private final Map<String, Object> cacheObjects = new ConcurrentHashMap<>();
  public static String projectCache = "ProjectCacheImpl";


  /**
   * We only create this class from the replicatedEventsCoordinator.
   * This is a singleton and its enforced by our SingletonEnforcement below that if anyone else tries to create
   * this class it will fail.
   * Sorry by adding a getInstance, make this class look much more public than it is,
   * and people expect they can just call getInstance - when in fact they should always request it via the
   * ReplicatedEventsCoordinator.getReplicatedXWorker() methods.
   *
   * @param replicatedEventsCoordinator
   */
  public ReplicatedIncomingCacheEventProcessor(ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    super(CACHE_EVENT, replicatedEventsCoordinator);
    logger.atInfo().log("Creating main processor for event type: %s", eventType);
    subscribeEvent(this);
    SingletonEnforcement.registerClass(ReplicatedIncomingCacheEventProcessor.class);
  }

  @Override
  public void stop() {
    unsubscribeEvent(this);
  }


  @Override
  public void processIncomingReplicatedEvent(final ReplicatedEvent replicatedEvent) {
    applyCacheMethodOrEviction((CacheKeyWrapper) replicatedEvent);
  }

  private void applyCacheMethodOrEviction(CacheKeyWrapper cacheKeyWrapper) {
    try {
      cacheKeyWrapper.rebuildOriginal();
    } catch (ClassNotFoundException e) {
      logger.atSevere().withCause(e).log("Event has been lost. Could not find class %s to rebuild original event",
          cacheKeyWrapper.getClass().getName());
    }
    cacheKeyWrapper.replicated = true;
    cacheKeyWrapper.setNodeIdentity(Objects.requireNonNull(replicatedEventsCoordinator.getThisNodeIdentity()));

    if (cacheKeyWrapper instanceof CacheObjectCallWrapper) {
      CacheObjectCallWrapper originalObj = (CacheObjectCallWrapper) cacheKeyWrapper;
      // Invokes a particular method on a cache. The CacheObjectCallWrapper carries the method
      // to be invoked on the cache. At present, we make only two replicated cache method calls from ProjectCacheImpl.
      applyMethodCallOnCache(originalObj.cacheName, originalObj.key, originalObj.otherMethodArgs, originalObj.methodName);
      return;
    }

    // Perform an eviction for a specified key on the specified local cache
    applyReplicatedEvictionFromCache(cacheKeyWrapper.cacheName, cacheKeyWrapper.key);
  }


  private void applyReplicatedEvictionFromCache(String cacheName, Object key) {
    boolean evicted = false;
    boolean reloaded = false;
    ReplicatedCacheWrapper wrapper = caches.get(cacheName);
    if (wrapper == null) {
      logger.atSevere().log("CACHE call could not be made, as cache does not exist. %s", cacheName);
      throw new ReplicatedEventsUnknownTypeException(
          String.format("CACHE call on replicated eviction could not be made, as cache does not exist. %s", cacheName));
    }

    if (replicatedEventsCoordinator.isCacheToBeEvicted(cacheName)) {
      logger.atFine().log("CACHE %s to evict %s...", cacheName, key);
      evicted = wrapper.evict(key);
      if (replicatedEventsCoordinator.getReplicatedConfiguration().isCacheToBeReloaded(cacheName)) {
        logger.atFine().log("CACHE %s to reload key %s...", cacheName, key);
        reloaded = wrapper.reload(key);
      } else {
        logger.atFine().log("CACHE %s *not* to reload key %s...", cacheName, key);
      }
    } else {
      logger.atFine().log("CACHE %s to *not* to evict %s...", cacheName, key);
    }

    if (evicted) {
      ReplicatorMetrics.addEvictionsPerformed(cacheName);
    }
    if (reloaded) {
      ReplicatorMetrics.addReloadsPerformed(cacheName);
    }
  }

  private void applyMethodCallOnCache(String cacheName, Object methodArg, List<Object> otherArgs, String methodName) {
    Object obj = cacheObjects.get(cacheName);
    if (obj == null) {
      // Failed to get a cache by the given name - return indicate failure - this wont change.
      logger.atSevere().log("CACHE method call could not be made, as cache does not exist. %s", cacheName);
      throw new ReplicatedEventsUnknownTypeException(
          String.format("CACHE call could not be made, as cache does not exist. %s", cacheName));
    }

    // Determine if we have a method that has more than one argument for the method signature. The main argument
    // for the method signature is usually Project.NameKey however methods can now support having other arguments
    // supplied.
    List<Object> remainingArgs = new ArrayList<>();
    if(otherArgs != null){
      remainingArgs.addAll(otherArgs);
    }

    // Find out what the class types of the other arguments are supplied to the method. This is required
    // in order to find the method with the name and matching signature.
    // Setting a size of 10 although the number of arguments will in reality be much smaller than this.
    List<Class<?>> remainingArgClassTypes = null;

    if(remainingArgs.size() > 0) {
      Class<?>[] classTypes = new Class[10];
      for (int n = 0; n < remainingArgs.size(); n++) {
        classTypes[n] = remainingArgs.get(n).getClass();
      }
      // Filter and remove any nulls as we cannot have nulls when invoking method due to signature mismatch.
      remainingArgClassTypes = Arrays.stream(classTypes).filter(Objects::nonNull).collect(Collectors.toList());
    }

    try {
      // The initial argument will be of the Project.NameKey type in most cases. If there are no other
      // remaining arguments then we can look for a matching method name that has a single argument in its signature
      if(remainingArgs.size() == 0) {

        logger.atFine().log("Looking for method %s...", methodName);
        Method method = obj.getClass().getMethod(methodName, methodArg.getClass());
        method.invoke(obj, methodArg);
        logger.atFine().log("Success for %s!", methodName);

      } else {
        // We have remaining arguments so lets look for a method signature that matches.
        logger.atFine().log("Looking for method %s with the following signature %s",
                methodName, remainingArgClassTypes);

        // The Project.NameKey must be the first argument in the method signature, so placing it at index 0.
        remainingArgClassTypes.add(0, methodArg.getClass());

        // The remainingArgClassTypes array is a filtered array (no nulls) of class types. If a method is
        // found with a matching name and matching signature of class types then we will be able to invoke
        // against that method.
        Class<?>[] remainingTypesArray = remainingArgClassTypes.toArray(new Class<?>[0]);
        Method method = obj.getClass().getMethod(methodName, remainingTypesArray);

        // Add the first argument at index 0 so they call all be passed together in a single array for invocation.
        remainingArgs.add(0, methodArg);
        method.invoke(obj, remainingArgs.toArray());

        logger.atFine().log("Success for %s!", methodName);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
      final String err = String.format("CACHE method call has been lost, could not call %s. %s", cacheName, methodName);
      logger.atSevere().withCause(ex).log(err);
      throw new ReplicatedEventsUnknownTypeException(err);
    }
  }

  public void watchCache(String cacheName, Cache cache) {
    caches.put(cacheName, new ReplicatedCacheWrapper(cache));
    logger.atInfo().log("CACHE New cache named %s inserted", cacheName);
  }

  public void watchObject(String cacheName, ProjectCache projectCache) {
    cacheObjects.put(cacheName, projectCache);
  }


}
