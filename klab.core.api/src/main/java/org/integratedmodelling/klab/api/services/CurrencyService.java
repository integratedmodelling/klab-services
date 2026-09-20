package org.integratedmodelling.klab.api.services;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Rate sourcing is explicit; storage never consults a live provider while scanning. */
public interface CurrencyService extends Service {
  enum Operation { EXCHANGE, INFLATION }
  enum Rounding { IEEE_754_BINARY64 }
  record Rate(String source, String target, Instant valuation, String provider, String version,
              Operation operation, Rounding rounding, double factor) implements Serializable {
    public Rate {
      if (source == null || source.isBlank() || target == null || target.isBlank()
          || provider == null || provider.isBlank() || version == null || version.isBlank()
          || !Double.isFinite(factor) || factor <= 0) throw new IllegalArgumentException("Invalid pinned currency rate");
      Objects.requireNonNull(valuation); Objects.requireNonNull(operation); Objects.requireNonNull(rounding);
      if (!source.matches("[A-Z]{3}(@[0-9]{4})?") || !target.matches("[A-Z]{3}(@[0-9]{4})?"))
        throw new IllegalArgumentException("Currency definitions must be CODE or CODE@baseYear");
      boolean sameYear = source.substring(3).equals(target.substring(3));
      boolean sameCode = source.substring(0, 3).equals(target.substring(0, 3));
      if (operation == Operation.EXCHANGE && !sameYear || operation == Operation.INFLATION && (!sameCode || sameYear))
        throw new IllegalArgumentException("Exchange and base-year adjustment must be separate operations");
    }
  }
  @FunctionalInterface interface RateProvider {
    /** Throw when unavailable. Inflation/base-year adjustments must be requested separately. */
    Rate quote(String source, String target, Instant valuation, Operation operation);
  }
}
