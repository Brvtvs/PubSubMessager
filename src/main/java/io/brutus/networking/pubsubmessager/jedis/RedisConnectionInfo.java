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

  /**
   * Class constructor.
   * 
   * @param name The arbitrary name to associate with this redis connection, if any. Can be
   *        <code>null</code> for no name.
   * @param host The host of the connection.
   * @param port The port of the connection.
   * @param password The password to authenticate with, if any. Can be <code>null</code> if no
   *        password is needed.
   */
  public RedisConnectionInfo(String name, String host, int port, String password) {
    if (host == null || host.equals("")) {
      throw new IllegalArgumentException("name and host cannot be null or empty");
    }
    this.name = name;
    this.host = host;
    this.port = port;
    this.password = password;
  }

  /**
   * Gets the arbitrarily defined name of this connection, if there is one.
   * 
   * @return The name of this connection. <code>null</code> or empty if there is none.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the host for this connection.
   * 
   * @return The connection's host.
   */
  public String getHost() {
    return host;
  }

  /**
   * The port of this connection.
   * 
   * @return This connection's port.
   */
  public int getPort() {
    return port;
  }

  /**
   * The password to use when authenticating this connection, if any.
   * 
   * @return This connection's password. <code>null</code> or empty if none.
   */
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
