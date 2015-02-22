package io.brutus.networking.pubsubmessager.distribution;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.jodah.expiringmap.ExpiringMap;
import net.jodah.expiringmap.ExpiringMap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap.ExpirationPolicy;
import io.brutus.networking.pubsubmessager.ByteArrayWrapper;
import io.brutus.networking.pubsubmessager.PubSubLibraryClient;
import io.brutus.networking.pubsubmessager.Subscriber;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * A wrapper that spreads messaging across multiple client instances and makes a best-effort attempt
 * to avoid publishing messages on unresponsive clients.
 * <p>
 * The clients wrapped by this should not be edited independently either before they are added or
 * after. This should be the sole editor after its wrapped clients have been constructed.
 * <p>
 * Can be used to wrap a single client, but this provides no added benefit in such a case.
 * <p>
 * Accepts a single subscriber.
 * <p>
 * Messages will be published to any of the grouped clients, meaning that a subscriber will need to
 * be subscribed to <i>all</i> of the client instances if they want to make sure to get all of the
 * published messages. Practically speaking, in most instances all publishers/subscribers should be
 * using the same set of messaging instances if they want to mimic normal, non-distributed behavior
 * as closely as possible.
 * <p>
 * For the sake of internal efficiency, this does not protect the sanity or unchangeability of
 * arguments passed into its methods. Clients should not generally interact with this directly.
 */
public abstract class RedundantClient implements PubSubLibraryClient, Subscriber,
    ExpirationListener<PubSubLibraryClient, Long> {

  private static ExecutorService threadPool;

  // number of instances to allow to try and fail for an individual message before giving up. The
  // same instance is not tried twice.
  private static final int MAX_TRIES = 5;
  // length of time to distrust a pub/sub instance if it fails a publish attempt
  private static final long DISTRUST_MILLIS = 60000;

  private Subscriber subscriber;
  private Set<ByteArrayWrapper> subscribedTo;

  private Set<PubSubLibraryClient> pubSubClients; // all clients
  private Set<PubSubLibraryClient> trusted; // responsive
  private ExpiringMap<PubSubLibraryClient, Long> distrusted; // unresponsive

  private boolean destroyed;

  protected RedundantClient() {
    if (threadPool == null) {
      threadPool = Executors.newCachedThreadPool();
    }

    subscribedTo = Collections.newSetFromMap(new ConcurrentHashMap<ByteArrayWrapper, Boolean>());
    pubSubClients =
        Collections.newSetFromMap(new ConcurrentHashMap<PubSubLibraryClient, Boolean>());
    trusted = Collections.newSetFromMap(new ConcurrentHashMap<PubSubLibraryClient, Boolean>());

    distrusted =
        ExpiringMap.builder().expiration(DISTRUST_MILLIS, TimeUnit.MILLISECONDS)
            .expirationPolicy(ExpirationPolicy.CREATED).expirationListener(this).build();
  }

  @Override
  public final ListenableFuture<Boolean> publish(byte[] channel, byte[] message) {
    if (destroyed || pubSubClients.isEmpty()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(false);
      return ret;
    }

    return new PublishAttempt(channel, message).callback;
  }

  @Override
  public final void onMessage(byte[] channel, byte[] message) {
    if (!destroyed && subscriber != null) {
      subscriber.onMessage(channel, message);
    }
  }

  @Override
  public final void addChannel(byte[] channel) {
    if (destroyed) {
      return;
    }
    for (PubSubLibraryClient client : pubSubClients) {
      client.addChannel(channel);
    }
    subscribedTo.remove(ByteArrayWrapper.wrap(channel));
  }

  @Override
  public final void removeChannel(byte[] channel) {
    if (destroyed) {
      return;
    }
    for (PubSubLibraryClient client : pubSubClients) {
      client.removeChannel(channel);
    }
    subscribedTo.add(ByteArrayWrapper.wrap(channel));
  }

  @Override
  public final void destroy() {
    if (destroyed) {
      return;
    }
    destroyed = true;

    for (PubSubLibraryClient client : pubSubClients) {
      client.destroy();
    }
    pubSubClients.clear();
    trusted.clear();
    distrusted.clear();

    onDestroy();
  }

  @Override
  public final void setSubscriber(Subscriber sub) {
    this.subscriber = sub;
  }

  /**
   * Adds a pub/sub client that this may use to publish and subscribe to.
   * <p>
   * Grouping multiple clients allows for this to distribute load and potentially tolerate failure.
   * 
   * @param client The pub/sub messaging client to add and use to subscribe and publish to.
   */
  public final void addPubSubClient(PubSubLibraryClient client) {
    if (destroyed || client == null || pubSubClients.contains(client)) {
      return;
    }
    pubSubClients.add(client);
    trusted.add(client);

    for (ByteArrayWrapper channel : subscribedTo) {
      client.addChannel(channel.getData());
    }
  }

  /**
   * Removes a pub/sub client from being used to fulfill publish and subscribe requests by this
   * wrapper.
   * 
   * @param client The pub/sub messaging client to stop using.
   */
  public final void removePubSubClient(PubSubLibraryClient client) {
    if (destroyed) {
      return;
    }
    if (pubSubClients.remove(client)) {
      for (ByteArrayWrapper channel : subscribedTo) {
        client.removeChannel(channel.getData());
      }
      trusted.remove(client);
      distrusted.remove(client);
    }
  }

  @Override
  public final void expired(PubSubLibraryClient client, Long distrustStarted) {
    trusted.add(client);
  }

  private final void distrustClient(PubSubLibraryClient client) {
    // if there only one client, there is nothing to be done about a failure
    if (pubSubClients.size() <= 1 || !trusted.remove(client)) {
      return;

      // removing this would leave no trusted clients, retrusts previously untrusted clients early
    } else if (trusted.size() < 1) {
      trusted.addAll(distrusted.keySet());
      distrusted.clear();
    }

    distrusted.put(client, System.currentTimeMillis());
  }

  /**
   * Gets the next publisher that should be used out of the set of currently responsive messaging
   * instances.
   * 
   * @param currentlyTrusted A set of this wrapper's clients that it has no reason to distrust yet.
   *        Should not be edited in any way.
   * @return The client that this should attempt to publish its message via.
   */
  protected abstract PubSubLibraryClient getNextPublisher(Set<PubSubLibraryClient> currentlyTrusted);

  /**
   * Called when this client wrapper is destroyed.
   */
  protected abstract void onDestroy();

  /**
   * Makes repeated attempts to publish a message.
   */
  private class PublishAttempt {

    private byte[] channel;
    private byte[] message;
    private int attempts;
    private SettableFuture<Boolean> callback;

    private PublishAttempt(byte[] channel, byte[] message) {
      this.channel = channel;
      this.message = message;
      callback = SettableFuture.create();

      makeAttempt();
    }

    private void makeAttempt() {
      if (destroyed) {
        callback.set(false);
        return;
      }

      attempts++;
      // gets the next publisher based on the subclass' selection algorithm.
      final PubSubLibraryClient publisher = getNextPublisher(trusted);
      final ListenableFuture<Boolean> fut = publisher.publish(channel, message);

      fut.addListener(new Runnable() {
        @Override
        public void run() {
          boolean successful = false;

          try {
            successful = fut.get();
          } catch (InterruptedException e) {
          } catch (ExecutionException e) {
          }

          // if successful or too many tries, calls back with the result
          if (successful || attempts >= MAX_TRIES || attempts >= pubSubClients.size()) {
            callback.set(successful);

          } else { // else try another messager
            distrustClient(publisher);
            makeAttempt();
          }
        }
      }, threadPool);
    }
  }

}
