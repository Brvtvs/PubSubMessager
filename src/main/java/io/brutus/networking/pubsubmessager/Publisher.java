package io.brutus.networking.pubsubmessager;

/**
 * A simple interface for a class that can publish to an underlying pub/sub implementation.
 * <p>
 * For the sake of internal efficiency, this makes no guarantees for the sanity or unchangeability
 * of arguments passed into its methods. Clients should not generally interact with this directly.
 */
public interface Publisher {

  /**
   * Publishes a message to all subscribers of a given channel.
   * 
   * @param channel The channel to publish the message on.
   * @param message The message to send.
   */
  void publish(byte[] channel, byte[] message);

}
