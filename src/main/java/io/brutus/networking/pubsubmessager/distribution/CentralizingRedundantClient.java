package io.brutus.networking.pubsubmessager.distribution;

import io.brutus.networking.pubsubmessager.PubSubLibraryClient;

import java.util.Set;

/**
 * A pub/sub client-wrapper that tries to publish all messages on a single of its wrapped clients,
 * so long as it remains responsive.
 */
public class CentralizingRedundantClient extends RedundantClient {

  private PubSubLibraryClient current;

  public CentralizingRedundantClient() {
    super();
  }

  @Override
  protected PubSubLibraryClient getNextPublisher(Set<PubSubLibraryClient> currentlyTrusted) {
    if (current == null || !currentlyTrusted.contains(current)) {
      for (PubSubLibraryClient client : currentlyTrusted) {
        current = client;
        break;
      }
    }
    return current;
  }

  @Override
  protected void onDestroy() {
    current = null;
  }

}
