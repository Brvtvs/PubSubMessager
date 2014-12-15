package io.brutus.networking.pubsubmessager.jedis;

import io.brutus.networking.pubsubmessager.PubSubLibrarySubscription;
import io.brutus.networking.pubsubmessager.Publisher;
import io.brutus.networking.pubsubmessager.Subscriber;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * A subscription to a Redis pub/sub system through a Jedis client. Includes a publishing mechanism
 * as well.
 * <p>
 * Subscribes to Jedis and offers a variety of methods to edit the channels that this listens to
 * over time. Does not support pattern-matching, even though Jedis can. Takes a single subscriber to
 * inform of incoming messages (on all channels this is subscribed to).
 * <p>
 * For the sake of internal efficiency, this does not protect the sanity or unchangeability of
 * arguments passed into its methods. Clients should not generally interact with this directly.
 * <p>
 * The Jedis pub/sub interface is a little confusing, especially when it comes to multithreading. At
 * any given time, if this class is subscribed to any channels at all, it will have a single thread
 * that is listening for incoming messages from redis with a blocking method. After that listening
 * thread is created, we can add and remove subscriptions as desired, but the initial subscription
 * and actual listening must be done on its own thread. When all channels are unsubscribed from, the
 * listening thread returns. Note that this is stated with about 70% certainty, as the internals of
 * the pub/sub mechanism are not entirely clear to me.
 * <p>
 * This class maintains a constant connection to its redis server by subscribing to a base channel.
 * This makes it much easier to protect its operation from potentially insane client commands.
 * <p>
 * When {@link #unsubscribe()} or {@link #destroy()} is called, this class ceases operation.
 */
public class PubSubJedisClient extends BinaryJedisPubSub implements PubSubLibrarySubscription,
    Publisher {

  private final byte[] BASE_CHANNEL = "pG8n5jp#".getBytes(Charset.forName("UTF-8"));

  private final JedisPool jedisPool;
  private final ExecutorService threadPool;
  private Subscriber sub;

  private volatile boolean subscribed; // Is there a base subscription yet?


  public PubSubJedisClient(JedisPool jedisPool) {
    if (jedisPool == null) {
      throw new IllegalArgumentException("jedis pool cannot be null");
    }
    this.jedisPool = jedisPool;
    this.threadPool = Executors.newCachedThreadPool();

    createSubscription(BASE_CHANNEL);
  }


  @Override
  public final synchronized void setSubscriber(Subscriber sub) {
    this.sub = sub;
  }

  @Override
  public final void addChannel(final byte[] channel) {
    if (subscribed) { // Already has a subscription thread and can just add a new channel to it.
      subscribe(channel);

    } else { // Waits for the initial subscription to prepare itself.
      threadPool.execute(new Runnable() {
        @Override
        public void run() {
          while (!subscribed) {
            try {
              Thread.sleep(5);
            } catch (InterruptedException e) {
            }
          }
          subscribe(channel);
        }
      });
    }
  }

  @Override
  public final void removeChannel(final byte[] channel) {
    if (Arrays.equals(channel, BASE_CHANNEL)) { // Protects the base subscription
      return;
    }

    if (subscribed) {
      unsubscribe(channel);

    } else { // Waits a for the initial subscription to prepare itself.
      threadPool.execute(new Runnable() {
        @Override
        public void run() {
          while (!subscribed) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
            }
          }
          unsubscribe(channel);
        }
      });
    }
  }

  @Override
  public final void destroy() {
    unsubscribe();
    jedisPool.destroy();
  }

  @Override
  public final void onMessage(final byte[] channel, final byte[] message) {
    if (sub == null || Arrays.equals(channel, BASE_CHANNEL)) {
      return;
    }

    try {
      sub.onMessage(channel, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public final void publish(final byte[] channel, final byte[] message) {
    threadPool.execute(new Runnable() {
      @Override
      public void run() {
        BinaryJedis bJedis = jedisPool.getResource();
        try {
          bJedis.publish(channel, message);
        } catch (Exception e) {
          jedisPool.returnBrokenResource((Jedis) bJedis);
          e.printStackTrace();
        }
        jedisPool.returnResource((Jedis) bJedis);
      }
    });
  }


  // Confirms successful subscriptions/unsubscriptions.
  @Override
  public void onSubscribe(byte[] channel, int subscribedChannels) {
    subscribed = true;
    // TODO debug
    System.out.println("[JedisClient] Subscribed to channel: "
        + new String(channel, Charset.forName("UTF-8")));
  }

  @Override
  public void onUnsubscribe(byte[] channel, int subscribedChannels) {
    // TODO debug
    System.out.println("[JedisClient] Unsubscribed from channel: "
        + new String(channel, Charset.forName("UTF-8")));
  }


  // This implementation does not support pattern-matching subscriptions
  @Override
  public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {}

  @Override
  public void onPSubscribe(byte[] pattern, int subscribedChannels) {}

  @Override
  public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {}


  /**
   * Creates the initial listening thread which blocks as it polls redis for new messages.
   * Subsequent subscriptions can simply be added using {@link #subscribe(byte[]...)} after the
   * subscription thread has been created.
   * 
   * @param firstChannel The first channel to initially subscribe to. If you do not have a first
   *        channel, there is no reason to create a subscriber thread yet.
   */
  private void createSubscription(final byte[] firstChannel) {
    // gets a non-thread-safe jedis instance from the thread-safe pool.
    final BinaryJedis jedisInstance = jedisPool.getResource();
    final BinaryJedisPubSub pubsub = this;

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          // TODO debug
          System.out.println("[PubSub] Creating initial jedis subscription to channel "
              + new String(firstChannel, Charset.forName("UTF-8")));
          // this will block as long as there are subscriptions
          jedisInstance.subscribe(pubsub, firstChannel);

        } catch (Exception e) {
          jedisPool.returnBrokenResource((Jedis) jedisInstance);
          e.printStackTrace();
        }
        // TODO debug
        System.out.println("[PubSub] jedisInstance.subscribe() returned, subscription over.");
        // when the subscription ends (jedisInstance.subscribe() returns), returns the instance to
        // the pool
        jedisPool.returnResource((Jedis) jedisInstance);
      }
    }).start();
  }

}
