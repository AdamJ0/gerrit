package com.google.gerrit.server.replication;

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.util.Providers;

import java.util.HashMap;
import java.util.Map;

/**
 * Can set a Context in Thread local storage for requests that require ReviewDb.
 * A ThreadLocalRequestContext is guice injected by the class which manages the current
 * RequestContext using a ThreadLocal
 *
 * It is important to remember to unset or remove a set context from the thread local
 * storage as the context can go stale. Be careful when using this class in conjunction
 * with a thread pool as you must unset the context. Threads in a thread pool retain
 * context unless unset. This can be done in a finally clause.
 * @author ronan.conway
 */
public class ReplicatedEventRequestScope {

    private static final Key<RequestScopedReviewDbProvider> DB_KEY =
            Key.get(RequestScopedReviewDbProvider.class);

    public static class Context implements RequestContext {
        private final RequestCleanup cleanup = new RequestCleanup();
        private final Map<Key<?>, Object> map = new HashMap<>();
        private final SchemaFactory<ReviewDb> schemaFactory;

        private Context(SchemaFactory<ReviewDb> sf) {
            schemaFactory = sf;
            map.put(DB_KEY, new RequestScopedReviewDbProvider(schemaFactory, Providers.of(cleanup)));
        }

        @Override
        public CurrentUser getUser() {
            throw new NotImplementedException("No user for this request scope");
        }

        @Override
        public Provider<ReviewDb> getReviewDbProvider() {
            return (RequestScopedReviewDbProvider) map.get(DB_KEY);
        }

        synchronized <T> T get(Key<T> key, Provider<T> creator) {
            @SuppressWarnings("unchecked")
            T t = (T) map.get(key);
            if (t == null) {
                t = creator.get();
                map.put(key, t);
            }
            return t;
        }
    }

    static class ContextProvider implements Provider<Context> {
        @Override
        public Context get() {
            return requireContext();
        }
    }

    private static final ThreadLocal<Context> current = new ThreadLocal<>();

    private static Context requireContext() {
        final Context ctx = current.get();
        if (ctx == null) {
            throw new OutOfScopeException("Not in command/request");
        }
        return ctx;
    }

    private final ThreadLocalRequestContext local;

    @Inject
    ReplicatedEventRequestScope(ThreadLocalRequestContext local) {
        this.local = local;
    }


    public Context newContext(SchemaFactory<ReviewDb> sf) {
        return new Context(sf);
    }

    public Context set(Context ctx) {
        Context old = current.get();
        current.set(ctx);
        local.setContext(ctx);
        return old;
    }

    public Context get() {
        return current.get();
    }


    public Context reopenDb() {
        // Setting a new context with the same fields is enough to get the ReviewDb
        // provider to reopen the database.
        Context old = current.get();
        return set(new Context(old.schemaFactory));
    }

    /** Returns exactly one instance per command executed. */
    static final Scope REQUEST =
            new Scope() {
                @Override
                public <T> Provider<T> scope(Key<T> key, Provider<T> creator) {
                    return new Provider<T>() {
                        @Override
                        public T get() {
                            return requireContext().get(key, creator);
                        }

                        @Override
                        public String toString() {
                            return String.format("%s[%s]", creator, REQUEST);
                        }
                    };
                }

                @Override
                public String toString() {
                    return "Replicated Event Request Scope.REQUEST";
                }
            };
}