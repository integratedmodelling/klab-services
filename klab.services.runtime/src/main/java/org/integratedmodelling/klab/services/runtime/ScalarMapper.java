package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.runtime.storage.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Scalar executor providing handling and caching for LUTs, classifications and expression and
 * implementing the mapping strategy configured in the runtime.
 */
public class ScalarMapper {

  private final DigitalTwin digitalTwin;
  private final ServiceContextScope scope;
  private final Observation targetObservation;
  private final Class<? extends Storage> storageClass;

  public ScalarMapper(Observation target, DigitalTwin digitalTwin, ServiceContextScope scope) {

    this.targetObservation = target;
    this.digitalTwin = digitalTwin;
    this.scope = scope;

    // observation should admit scalar values
    this.storageClass =
        switch (target.getObservable().getArtifactType()) {
          case BOOLEAN -> BooleanStorage.class;
          case NUMBER -> /* TODO use config to choose between double and float */
              DoubleStorage.class;
          case TEXT, CONCEPT -> KeyedStorage.class;
          default ->
              throw new KlabIllegalStateException(
                  "scalar mapping to type "
                      + target.getObservable().getArtifactType()
                      + " not supported");
        };
  }

  public void add(ServiceCall serviceCall, Extensions.FunctionDescriptor descriptor) {

    // check out the expected data value vs. the observation

    // if needed, adjust the storage class

    System.out.println("ADD CALL " + serviceCall);
  }

  public boolean run() {

    // determine storage
    var storage = digitalTwin.getStateStorage().getOrCreateStorage(targetObservation, storageClass);

    // call storage.map() with the correct executor and configuration
    // TODO masking
    switch (storage) {
      case DoubleStorage doubleStorage -> {
//        doubleStorage.map(getDoubleMapper());
      }
      case FloatStorage doubleStorage -> {}
      case BooleanStorage booleanStorage -> {}
      case KeyedStorage keyedStorage -> {}
      default ->
          throw new KlabInternalErrorException("unexpected storage type in ScalarMapper run()");
    }

    return true;
  }

//  private LongStorage.OffsetToDoubleFunction getDoubleMapper() {
//    // TODO
//    return null;
//  }
}
