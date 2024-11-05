//package org.integratedmodelling.klab.api.services.runtime;
//
//import org.integratedmodelling.klab.api.scope.ContextScope;
//
//import java.util.concurrent.Future;
//
///**
// * A task is a future for an object that exists in a
// * {@link org.integratedmodelling.klab.api.scope.ContextScope}, exposes a tracking URN for the object of
// * interest and admits k.LAB-aware listeners for any messages contextualized to it or any sub-tasks.
// *
// * @param <T>
// */
//public interface Task<T> extends Future<T> {
//
//    ContextScope getScope();
//
//    String getUrn();
//
//}
