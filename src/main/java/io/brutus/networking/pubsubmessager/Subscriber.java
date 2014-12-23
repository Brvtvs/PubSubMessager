package io.brutus.networking.pubsubmessager;

/**
 * A subscriber to a pub/sub channel.
 */
public interface Subscriber {

  /**
   * Called when a message is sent on a channel that this is subscribed to.
   * <p>
   * No guarantees are made about what thread this will be called from.
   * 
   * @param channel A defensive copy of the channel that the message was sent on.
   * @param message A defensive copy of the message that was received.
   */
  void onMessage(byte[] channel, byte[] message);

}
