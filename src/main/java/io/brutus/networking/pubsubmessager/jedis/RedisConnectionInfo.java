package io.brutus.networking.pubsubmessager.jedis;

/**
 * Connection information for a redis instance.
 * <p>
 * Two connections are considered the same if their connection info is identical, regardless of
 * their name.
 **/
public class RedisConnectionInfo {

  private final String name; // arbitrary name of the connection

  private final String host;
  private final int port;
  private final String password;

  public RedisConnectionInfo(String name, String host, int port, String password) {
    if (name == null || name.equals("") || host == null || host.equals("")) {
      throw new IllegalArgumentException("name and host cannot be null or empty");
    }
    this.name = name;
    this.host = host;
    this.port = port;
    this.password = password;
  }


  public String getName() {
    return name;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getPassword() {
    return password;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((host == null) ? 0 : host.hashCode());
    result = prime * result + ((password == null) ? 0 : password.hashCode());
    result = prime * result + port;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    RedisConnectionInfo other = (RedisConnectionInfo) obj;
    if (host == null) {
      if (other.host != null)
        return false;
    } else if (!host.equals(other.host))
      return false;
    if (password == null) {
      if (other.password != null)
        return false;
    } else if (!password.equals(other.password))
      return false;
    if (port != other.port)
      return false;
    return true;
  }

}
