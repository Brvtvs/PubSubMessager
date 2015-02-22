package io.brutus.networking.pubsubmessager.distribution;

import io.brutus.networking.pubsubmessager.PubSubLibraryClient;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pub/sub client-wrapper that randomly chooses from its group of clients when publishing to
 * distribute load.
 */
public class RandomizingRedundantClient extends RedundantClient {

  public RandomizingRedundantClient() {
    super();
  }

  @Override
  protected PubSubLibraryClient getNextPublisher(Set<PubSubLibraryClient> currentlyTrusted) {
    PubSubLibraryClient atLeastOne = null;
    int index = ThreadLocalRandom.current().nextInt(currentlyTrusted.size());
    int i = 0;
    for (PubSubLibraryClient client : currentlyTrusted) {
      if (i++ == index) {
        return client;
      }
      if (atLeastOne == null) {
        atLeastOne = client;
      }
    }
    return atLeastOne;
  }

  @Override
  protected void onDestroy() {}

}
