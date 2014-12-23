package io.brutus.networking.pubsubmessager;

import java.util.Arrays;

/**
 * A wrapper for a byte array.
 * <p>
 * Allows for hashing the byte array, judging equality based on the array's contents, and minor
 * performance improvements.
 */
public class ByteArrayWrapper {

  private final byte[] data;
  private final boolean copy;
  private final int hashCode;

  /**
   * Factory method that creates a wrapper by copying a byte array.
   * <p>
   * A copied wrapper is immutable.
   * 
   * @param data The data to copy into a new wrapper.
   * @return The new wrapper object.
   */
  public static ByteArrayWrapper copyOf(byte[] data) {
    return new ByteArrayWrapper(data, true);
  }

  /**
   * Factory method that creates a wrapper without copying the data.
   * <p>
   * Slightly more efficient than copying the array for a new wrapper, but any changes to the
   * wrapped array will change what {@link #equals(Object)} and {@link #hashCode()} return. In most
   * cases, such changes would invalidate the basic point of the wrapper. Generally, this method
   * should only be used to create a wrapper when mirco-efficiency is important and you are assured
   * that the underlying byte array will not be changed.
   * 
   * @param data
   * @return
   */
  public static ByteArrayWrapper wrap(byte[] data) {
    return new ByteArrayWrapper(data, false);
  }

  private ByteArrayWrapper(byte[] data, boolean copy) {
    if (data == null) {
      throw new IllegalArgumentException("byte data cannot be null");
    }
    this.copy = copy;
    if (copy) {
      this.data = data.clone();
      this.hashCode = Arrays.hashCode(data); // precomputes and stores the hashcode
    } else {
      this.data = data;
      this.hashCode = 0; // unused, because the byte array might change
    }
  }

  /**
   * Gets a defensive copy of the byte array that this is wrapping.
   * 
   * @return This wrapper's byte array.
   */
  public byte[] getData() {
    return data.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ByteArrayWrapper)) {
      return false;
    }
    return Arrays.equals(data, ((ByteArrayWrapper) other).data);
  }

  @Override
  public int hashCode() {
    if (copy) {
      return hashCode;
    }
    return Arrays.hashCode(data);
  }

}
