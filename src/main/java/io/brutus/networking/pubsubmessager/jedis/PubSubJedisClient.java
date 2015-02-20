package io.brutus.networking.pubsubmessager.jedis;

import io.brutus.networking.pubsubmessager.ByteArrayWrapper;
import io.brutus.networking.pubsubmessager.PubSubLibrarySubscription;
import io.brutus.networking.pubsubmessager.Publisher;
import io.brutus.networking.pubsubmessager.Subscriber;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

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
 * If the connection to the given redis instance fails or is interrupted, will keep attempting to
 * reconnect periodically until destroyed. Publishers and subscribers are not informed of failure in
 * any way.
 * <p>
 * When {@link #unsubscribe()} or {@link #destroy()} is called, this class ceases operation.
 */
public class PubSubJedisClient extends BinaryJedisPubSub implements PubSubLibrarySubscription,
    Publisher {

  private static final long RECONNECT_PERIOD_MILLIS = 800;
  private static final byte[] BASE_CHANNEL = "pG8n5jp#".getBytes(Charset.forName("UTF-8"));

  private final JedisPool jedisPool;
  private final ExecutorService threadPool;
  private volatile Subscriber sub;

  private Set<ByteArrayWrapper> channels;

  private volatile boolean subscribed; // Is there a base subscription yet?
  private volatile boolean destroyed; // has this been deliberately destroyed?

  /**
   * Class constructor.
   * 
   * @param redisInstance The connection info for the redis instance this client should connect to.
   */
  public PubSubJedisClient(RedisConnectionInfo redisInstance) {
    if (redisInstance == null) {
      throw new IllegalArgumentException("redis instance info cannot be null");
    }
    if (redisInstance.getPassword() == null || redisInstance.getPassword().equals("")) {
      jedisPool =
          new JedisPool(new JedisPoolConfig(), redisInstance.getHost(), redisInstance.getPort());
    } else {
      jedisPool =
          new JedisPool(new JedisPoolConfig(), redisInstance.getHost(), redisInstance.getPort(),
              Protocol.DEFAULT_TIMEOUT, redisInstance.getPassword());
    }
    this.threadPool = Executors.newCachedThreadPool();
    this.channels = new HashSet<ByteArrayWrapper>();

    createSubscription(BASE_CHANNEL);
  }

  @Override
  public final synchronized void setSubscriber(Subscriber sub) {
    this.sub = sub;
  }

  @Override
  public final void addChannel(byte[] channel) {
    channels.add(ByteArrayWrapper.wrap(channel));

    if (subscribed) { // Already has a subscription thread and can just add a new channel to it.
      subscribe(channel);
    }
  }

  @Override
  public final void removeChannel(final byte[] channel) {
    if (Arrays.equals(channel, BASE_CHANNEL)) { // Protects the base subscription
      return;
    }

    channels.remove(ByteArrayWrapper.wrap(channel));

    if (subscribed) {
      unsubscribe(channel);
    }
  }

  @Override
  public final void unsubscribe() {
    destroy();
  }

  @Override
  public final void destroy() {
    destroyed = true;
    try {
      super.unsubscribe();
    } catch (NullPointerException e) {
    }
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
        BinaryJedis bJedis = null;
        try {
          bJedis = jedisPool.getResource();
          bJedis.publish(channel, message);
          jedisPool.returnResource((Jedis) bJedis);

        } catch (Exception e) {
          System.out.println("Encountered issue while publishing a message.");
          e.printStackTrace();
          if (bJedis != null) {
            jedisPool.returnBrokenResource((Jedis) bJedis);
          }
        }
      }
    });
  }

  // Confirms successful subscriptions/unsubscriptions.
  @Override
  public void onSubscribe(byte[] channel, int subscribedChannels) {
    if (!subscribed) {
      for (ByteArrayWrapper subscribeTo : channels) {
        subscribe(subscribeTo.getData());
      }
    }
    subscribed = true;

    System.out.println("[JedisClient] Subscribed to channel: "
        + new String(channel, Charset.forName("UTF-8")));
  }

  @Override
  public void onUnsubscribe(byte[] channel, int subscribedChannels) {
    System.out.println("[JedisClient] Unsubscribed from channel: "
        + new String(channel, Charset.forName("UTF-8")));
  }

  /**
   * Creates the initial listening thread which blocks as it polls redis for new messages.
   * Subsequent subscriptions can simply be added using {@link #subscribe(byte[]...)} after the
   * subscription thread has been created.
   * 
   * @param firstChannel The first channel to initially subscribe to. If you do not have a first
   *        channel, there is no reason to create a subscriber thread yet.
   */
  private void createSubscription(final byte[] firstChannel) {

    final BinaryJedisPubSub pubsub = this;

    new Thread(new Runnable() {
      @Override
      public void run() {

        boolean first = true;

        while (!destroyed) {

          if (!first) {
            System.out
                .println("[PubSub] Jedis connection failed or was interrupted, attempting to reconnect");
          }
          first = false;

          BinaryJedis jedisInstance = null;

          try {
            // gets a non-thread-safe jedis instance from the thread-safe pool.
            jedisInstance = jedisPool.getResource();

            System.out.println("[PubSub] Creating initial jedis subscription to channel "
                + new String(firstChannel, Charset.forName("UTF-8")));
            // this will block as long as there are subscriptions
            jedisInstance.subscribe(pubsub, firstChannel);

            System.out.println("[PubSub] jedisInstance.subscribe() returned, subscription over.");

            // when the subscription ends (subscribe() returns), returns the instance to the pool
            jedisPool.returnResource((Jedis) jedisInstance);

          } catch (JedisConnectionException e) {
            System.out.println("[PubSub] Jedis connection encountered an issue.");
            e.printStackTrace();
            if (jedisInstance != null) {
              jedisPool.returnBrokenResource((Jedis) jedisInstance);
            }

          } catch (JedisDataException e) {
            System.out.println("[PubSub] Jedis connection encountered an issue.");
            e.printStackTrace();
            if (jedisInstance != null) {
              jedisPool.returnBrokenResource((Jedis) jedisInstance);
            }
          }
          subscribed = false;

          // sleeps for a short pause, rather than constantly retrying connection
          if (!destroyed) {
            try {
              Thread.sleep(RECONNECT_PERIOD_MILLIS);
            } catch (InterruptedException e) {
              destroyed = true;
              System.out.println("[PubSub] Reconnection pause thread was interrupted.");
              e.printStackTrace();
            }
          }
        }
      }
    }).start();
  }

  // This implementation does not support pattern-matching subscriptions
  @Override
  public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {}

  @Override
  public void onPSubscribe(byte[] pattern, int subscribedChannels) {}

  @Override
  public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {}

}
