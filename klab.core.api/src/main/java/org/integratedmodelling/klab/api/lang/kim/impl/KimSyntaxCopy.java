package org.integratedmodelling.klab.api.lang.kim.impl;

import java.io.*;

/** Detached snapshots of in-process syntax payloads, including nested mutable parameter values. */
final class KimSyntaxCopy {
  private KimSyntaxCopy() {}

  /**
   * Uses the payload's serialization contract rather than guessing which Object-valued fields are
   * immutable. Never falls back to sharing an unsupported payload. Reads only bytes just written
   * from the supplied in-process object, not external serialized input.
   */
  @SuppressWarnings("unchecked")
  static <T> T copy(T value) {
    if (value == null) return null;
    try {
      var bytes = new ByteArrayOutputStream();
      try (var output = new ObjectOutputStream(bytes)) { output.writeObject(value); }
      try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        return (T) input.readObject();
      }
    } catch (IOException | ClassNotFoundException failure) {
      throw new IllegalArgumentException(
          "Cannot detach syntax payload of type " + value.getClass().getName(), failure);
    }
  }
}
