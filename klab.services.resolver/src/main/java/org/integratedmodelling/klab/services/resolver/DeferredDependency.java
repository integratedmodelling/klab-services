package org.integratedmodelling.klab.services.resolver;

import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

/** Resolver-local chosen-route evidence; only the portable plan is sent to Runtime. */
record DeferredDependency(KimObservable source, int index, String name, String namespace,
    long sourceVersion, String sourceFingerprint, long semanticRevision, String reasonerId,
    Map<List<String>, OperationTarget> operations,
    org.integratedmodelling.klab.api.services.runtime.ClassifierDependencyPlan.ContextWitness context) {}
