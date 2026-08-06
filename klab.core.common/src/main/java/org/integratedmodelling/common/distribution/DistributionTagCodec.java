package org.integratedmodelling.common.distribution;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Stack;

/** Encodes the physical identity of a distribution tag for persistent engine settings. */
public final class DistributionTagCodec {

  private DistributionTagCodec() {}

  public static String encode(Stack.Tag tag) {
    if (tag == null || tag.version() == null) {
      return "";
    }
    return tag.version() + "|" + encodeSegment(tag.release()) + "|" + encodeSegment(tag.build());
  }

  public static Stack.Tag decode(String specification) {
    if (specification == null || specification.isBlank()) {
      return null;
    }
    var fields = specification.split("\\|", -1);
    if (fields.length != 3) {
      return null;
    }
    var version = "HEAD".equals(fields[0]) ? Version.HEAD : Version.create(fields[0]);
    return Stack.Tag.of(version, decodeSegment(fields[1]), decodeSegment(fields[2]), false, false);
  }

  private static String encodeSegment(String value) {
    return value == null
        ? "~"
        : Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decodeSegment(String value) {
    return "~".equals(value)
        ? null
        : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }
}
