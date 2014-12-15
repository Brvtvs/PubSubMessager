package io.brutus.networking.pubsubmessager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A pub/sub messager that simply routes messages to some underlying pub/sub implementation, which
 * is in turn represented by a multi-channel subscription and a publishing mechanism.
 * <p>
 * This class handles:
 * <ol>
 * <li>Providing a modular messaging interface that is thread-safe.
 * <li>Protecting pub/sub implementations from some bad client behavior/data.
 * <li>Routing messages for multiple subscribers to the same channel(s).
 * </ol>
 */
public class PubSubRouter implements PubSubMessager, Subscriber {

  private final Publisher publisher;
  private final PubSubLibrarySubscription subscription;
  private final Map<ByteArrayWrapper, Set<Subscriber>> subscribers;

  private final ExecutorService threadPool;

  // TODO consider adding a few more comments to clarify what is going on here
  // TODO Is this fully, entirely thread-safe?

  public PubSubRouter(PubSubLibrarySubscription subscription, Publisher publisher) {
    if (subscription == null || publisher == null) {
      throw new IllegalArgumentException("jedis pool cannot be null");
    }

    this.publisher = publisher;
    this.subscription = subscription;
    subscription.setSubscriber(this);
    this.subscribers = new ConcurrentHashMap<ByteArrayWrapper, Set<Subscriber>>();

    this.threadPool = Executors.newCachedThreadPool();
  }


  @Override
  public final void publish(byte[] channel, byte[] message) {
    if (channel == null) {
      // TODO test this
      // messages of 0 length are allowed. Null messages are treated as messages of 0 length.
      throw new NullPointerException();
    } else if (channel.length < 1) {
      throw new IllegalArgumentException("the channel name cannot be empty");
    }

    if (message == null) {
      message = new byte[0];
    } else {
      message = message.clone(); // defensive copying
    }

    publisher.publish(channel, message);
  }

  @Override
  public final void subscribe(byte[] channel, Subscriber sub) {
    if (channel == null || sub == null) {
      throw new NullPointerException();
    }
    if (channel.length < 1) {
      throw new IllegalArgumentException("the channel name cannot be empty");
    }

    final byte[] safeChannel = channel.clone();
    ByteArrayWrapper hashableChannel = ByteArrayWrapper.wrap(safeChannel);

    Set<Subscriber> channelSubs = subscribers.get(hashableChannel);
    if (channelSubs == null) {
      // uses CopyOnWriteArraySet for fast and consistent iteration (forwarding messages to
      // subscribers) but slow writes (adding/removing subscribers).
      // See a discussion of the issue here:
      // http://stackoverflow.com/questions/6720396/different-types-of-thread-safe-sets-in-java
      channelSubs = new CopyOnWriteArraySet<Subscriber>();
      subscribers.put(hashableChannel, channelSubs);

      // starts a jedis subscription to the channel if there were no subscribers before
      subscription.addChannel(safeChannel);
    }

    channelSubs.add(sub);
  }

  @Override
  public final void unsubscribe(byte[] channel, Subscriber sub) {
    if (channel == null || sub == null) {
      throw new NullPointerException();
    }
    if (channel.length < 1) {
      throw new IllegalArgumentException("the channel name cannot be empty");
    }

    final byte[] safeChannel = channel.clone();
    ByteArrayWrapper hashableChannel = ByteArrayWrapper.wrap(safeChannel);

    Set<Subscriber> channelSubs = subscribers.get(hashableChannel);
    if (channelSubs == null) { // no subscribers for the channel to begin with.
      return;
    }

    channelSubs.remove(sub);

    // stops the subscription to this channel if the unsubscribed was the last subscriber
    if (channelSubs.isEmpty()) {
      subscribers.remove(hashableChannel);
      subscription.removeChannel(safeChannel);
    }
  }

  @Override
  public final void onMessage(byte[] channel, byte[] message) {
    if (channel == null || message == null || channel.length < 1) {
      return;
    }

    ByteArrayWrapper hashableChannel = ByteArrayWrapper.wrap(channel);

    Set<Subscriber> channelSubs = subscribers.get(hashableChannel);

    if (channelSubs == null) { // We should not still be listening
      subscription.removeChannel(channel);
      return;
    } else if (channelSubs.isEmpty()) {
      subscribers.remove(hashableChannel);
      subscription.removeChannel(channel);
      return;
    }

    for (final Subscriber sub : channelSubs) {
      // defensive copying to avoid byte-array edits while iterating over the subscriber list.
      final byte[] safeChannel = channel.clone();
      final byte[] safeMessage = message.clone();

      // Gives subscribers their own thread from the thread pool in which to react to the message.
      // Avoids interruptions and other problems while iterating over the subscriber set.
      threadPool.execute(new Runnable() {
        @Override
        public void run() {
          sub.onMessage(safeChannel, safeMessage);
        }
      });
    }
  }

}
