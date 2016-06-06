/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.artifact_cache.thrift;


import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

public enum BuckCacheRequestType implements org.apache.thrift.TEnum {
  UNKNOWN(0),
  FETCH(100),
  STORE(101);

  private final int value;

  private BuckCacheRequestType(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static BuckCacheRequestType findByValue(int value) { 
    switch (value) {
      case 0:
        return UNKNOWN;
      case 100:
        return FETCH;
      case 101:
        return STORE;
      default:
        return null;
    }
  }
}
