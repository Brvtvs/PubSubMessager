package io.brutus.networking.pubsubmessager.distribution;

import io.brutus.networking.pubsubmessager.PubSubLibraryClient;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A pub/sub client-wrapper that iterates over its group of clients when publishing to distribute
 * load.
 */
public class IteratingRedundantClient extends RedundantClient {

  private Set<PubSubLibraryClient> used;

  public IteratingRedundantClient() {
    super();

    used = Collections.newSetFromMap(new ConcurrentHashMap<PubSubLibraryClient, Boolean>());
  }

  @Override
  protected PubSubLibraryClient getNextPublisher(Set<PubSubLibraryClient> currentlyTrusted) {
    PubSubLibraryClient first = null;
    for (PubSubLibraryClient client : currentlyTrusted) {
      if (first == null) {
        first = client;
      }
      if (used.add(client)) {
        return client;
      }
    }

    used.clear();
    used.add(first);
    return first;
  }

  @Override
  protected void onDestroy() {
    used.clear();
  }

}
