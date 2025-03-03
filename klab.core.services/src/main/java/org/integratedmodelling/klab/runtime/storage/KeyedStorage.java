//package org.integratedmodelling.klab.runtime.storage;
//
//import org.integratedmodelling.klab.api.data.Data;
//import org.integratedmodelling.klab.api.knowledge.observation.Observation;
//import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
//
//public class KeyedStorage extends AbstractStorage<KeyedBuffer> {
//
//  public KeyedStorage(
//      Observation observation, StateStorageImpl scope, ServiceContextScope contextScope) {
//    super(Type.KEYED, observation, scope, contextScope);
//  }
//
//  @Override
//  public KeyedBuffer buffer(long size, Data.FillCurve fillCurve, long[] offsets) {
//    return null;
//  }
//
//}
