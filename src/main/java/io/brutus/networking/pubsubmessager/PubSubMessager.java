package io.brutus.networking.pubsubmessager;

/**
 * Messager for standard pub/sub model. Handles multiple publishers and subscribers.
 * <p>
 * All messaging is asynchronous and non-blocking, even to local subscribers.
 * <p>
 * For more about the pub/sub messaging paradigm, see <a
 * href="http://en.wikipedia.org/wiki/Publish%E2
 * %80%93subscribe_pattern">http://en.wikipedia.org/wiki/Publish%E2%80%93subscribe_pattern</a>
 */
public interface PubSubMessager {

  /**
   * Publishes a message to all subscribers of a given channel.
   * <p>
   * Publishes to all connected subscribers, including local ones.
   * <p>
   * Publishing may not reach all subscribers on the channel for arbitrary networking reasons and
   * may fail entirely, such as if connection to the messaging instance is totally unavailable. No
   * guarantee is made that the message will send successfully or that the publisher will receive
   * any indication of whether it succeeded or failed.
   * 
   * @param channel The channel to publish the message on.
   * @param message The message to send.
   */
  void publish(byte[] channel, byte[] message);

  /**
   * Subscribes to a messaging channel.
   * <p>
   * When incoming messages arrive, the subscriber is called from an arbitrary new thread.
   * <p>
   * No guarantees are made that all messages sent on this channel will be received. Specific
   * messages or large chunks of messages may be missed for various networking reasons, and no
   * assurances are given that messages reach their destinations.
   * 
   * @param channel The channel to subscribe to.
   * @param sub The subscriber to inform of incoming messages.
   */
  void subscribe(byte[] channel, Subscriber sub);

  /**
   * Unsubscribes from a messaging channel.
   * 
   * @param channel The channel to unsubscribe from.
   * @param sub The subscriber to stop informing of incoming messages.
   */
  void unsubscribe(byte[] channel, Subscriber sub);

}
