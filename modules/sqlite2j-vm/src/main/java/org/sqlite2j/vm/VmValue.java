package org.sqlite2j.vm;

public final class VmValue {
  public enum Type { INT, TEXT, NULL }

  private final Type type;
  private final Object value;

  private VmValue(Type type, Object value) {
    this.type = type;
    this.value = value;
  }

  public static VmValue ofInt(long value) { return new VmValue(Type.INT, value); }
  public static VmValue ofText(String value) { return new VmValue(Type.TEXT, value); }
  public static VmValue nullValue() { return new VmValue(Type.NULL, null); }

  public Type getType() { return type; }
  public Object getValue() { return value; }
}
