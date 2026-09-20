package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.StorageScan;
import org.integratedmodelling.klab.api.services.CurrencyService;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;

/** Compiles once; the scanner hot path uses primitive arithmetic and immutable bounds only. */
public final class ValueMediation {
  private ValueMediation() {}
  private static class Units { static final UnitServiceImpl SERVICE = new UnitServiceImpl(); }

  /** Old semantic snapshots have no separate meaning field. Preserve exact-URN compatibility. */
  static StorageScan.Semantics target(StorageScan.Semantics source, StorageScan.Semantics requested) {
    if (requested == null) return source;
    if (requested.meaning().equals(requested.observable()) && requested.observable().equals(source.observable()))
      return new StorageScan.Semantics(requested.observable(), requested.unit(), requested.range(),
          requested.currency(), requested.contextualDimensions(), source.meaning());
    return requested;
  }

  public static StorageScan.Conversion compile(StorageScan.Semantics source, StorageScan.Semantics target,
      CurrencyService.Rate rate) {
    target = target(source, target);
    if (source.equals(target)) {
      if (rate != null) throw new IllegalArgumentException("Unused currency quote");
      return null;
    }
    if (!source.meaning().equals(target.meaning())) throw new UnsupportedOperationException("Different observable meaning");
    if (!source.contextualDimensions().equals(target.contextualDimensions()))
      throw new UnsupportedOperationException("Contextual mediation requires S5");
    boolean unit = !source.unit().equals(target.unit());
    boolean range = !source.range().equals(target.range());
    boolean currency = !source.currency().equals(target.currency());
    if ((unit ? 1 : 0) + (range ? 1 : 0) + (currency ? 1 : 0) != 1)
      throw new UnsupportedOperationException("Exactly one ordinary value mediator may change");
    if (!currency && rate != null) throw new IllegalArgumentException("Unused currency quote");
    if (unit) {
      if (source.unit().isEmpty() || target.unit().isEmpty()) throw new UnsupportedOperationException("Missing unit definition");
      if (!source.contextualDimensions().isEmpty() && !source.contextualDimensions().equals("{}"))
        throw new UnsupportedOperationException("Contextual mediation requires S5");
      var conversion = Units.SERVICE.conversion(Units.SERVICE.getUnit(target.unit()), Units.SERVICE.getUnit(source.unit()));
      return new StorageScan.Conversion("UNIT", conversion.factor(), conversion.offset(), "", "", null);
    }
    if (range) {
      var from = Bounds.parse(source.range()); var to = Bounds.parse(target.range());
      if (from.lowerOpen != to.lowerOpen || from.upperOpen != to.upperOpen)
        throw new IllegalArgumentException("Range endpoint inclusion must agree");
      double factor = (to.upper - to.lower) / (from.upper - from.lower);
      return new StorageScan.Conversion("RANGE", factor, to.lower - from.lower * factor, source.range(), target.range(), null);
    }
    if (rate == null) throw new IllegalArgumentException("A pinned currency quote is required");
    if (!rate.source().equals(source.currency()) || !rate.target().equals(target.currency()))
      throw new IllegalArgumentException("Currency quote direction/definitions differ");
    return new StorageScan.Conversion("CURRENCY", rate.factor(), 0, "", "", rate);
  }

  static Kernel kernel(StorageScan.Conversion conversion) { return new Kernel(conversion); }
  static void validateType(StorageScan.Conversion conversion, Storage.Type type) {
    if (conversion != null && type != Storage.Type.DOUBLE && type != Storage.Type.FLOAT)
      throw new UnsupportedOperationException("Value mediation requires floating-point storage and scanner types");
  }
  static final class Kernel {
    final StorageScan.Conversion conversion;
    final Bounds source, target;
    Kernel(StorageScan.Conversion conversion) {
      this.conversion = conversion;
      source = conversion != null && conversion.kind().equals("RANGE") ? Bounds.parse(conversion.sourceRange()) : null;
      target = source == null ? null : Bounds.parse(conversion.targetRange());
    }
    float applyFloat(double value) {
      float result = (float) apply(value);
      if (target != null && !Float.isNaN(result) && !target.contains(result))
        throw new IllegalArgumentException("Float rounding leaves the target range");
      return result;
    }
    double apply(double value) {
      if (conversion == null || Double.isNaN(value)) return value;
      if (source != null && !source.contains(value)) throw new IllegalArgumentException("Value outside source range");
      double result = conversion.offset() == 0 ? value * conversion.factor() : value * conversion.factor() + conversion.offset();
      // Map included endpoints exactly despite floating-point cancellation; never clip interior values.
      if (source != null) {
        if (value == source.lower) result = target.lower;
        else if (value == source.upper) result = target.upper;
        if (!target.contains(result)) throw new IllegalArgumentException("Value outside target range");
      }
      return result;
    }
  }
  private record Bounds(double lower, boolean lowerOpen, double upper, boolean upperOpen) {
    static Bounds parse(String text) {
      var parts = text.split(":", -1);
      if (parts.length != 4 || !java.util.Set.of("true", "false").contains(parts[1])
          || !java.util.Set.of("true", "false").contains(parts[3])) throw new IllegalArgumentException("Invalid range definition");
      var bounds = new Bounds(Double.parseDouble(parts[0]), Boolean.parseBoolean(parts[1]),
          Double.parseDouble(parts[2]), Boolean.parseBoolean(parts[3]));
      if (!Double.isFinite(bounds.lower) || !Double.isFinite(bounds.upper)
          || !Double.isFinite(bounds.upper - bounds.lower) || bounds.upper <= bounds.lower)
        throw new IllegalArgumentException("Range must have finite, strictly increasing bounds");
      return bounds;
    }
    boolean contains(double value) {
      return (lowerOpen ? value > lower : value >= lower) && (upperOpen ? value < upper : value <= upper);
    }
  }
}
