//package org.integratedmodelling.klab.runtime.storage;
//
//import org.integratedmodelling.klab.api.data.Data;
//import org.integratedmodelling.klab.api.knowledge.observation.Observation;
//import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
//
///**
// * Base storage providing the general methods. Children enable either boxed I/O or faster native
// * operation (recommended). The runtime makes the choice.
// *
// * @author Ferd
// */
//public class IntStorage extends AbstractStorage<IntBuffer> {
//
//    public IntStorage(Observation observation, StateStorageImpl scope, ServiceContextScope contextScope) {
//    super(Type.INTEGER, observation, scope, contextScope);
//  }
//
//  @Override
//  public IntBuffer buffer(long size, Data.FillCurve fillCurve, long[] offsets) {
//    var ret = new IntBuffer(this, size, fillCurve, offsets);
//    registerBuffer(ret);
//    return ret;
//  }
//}
