//package org.integratedmodelling.klab.runtime.storage;
//
//import org.integratedmodelling.klab.api.data.Data;
//import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
//import org.ojalgo.array.BufferArray;
//
//import java.util.PrimitiveIterator;
//
//public class IntBuffer extends AbstractStorage.AbstractBuffer {
//
//  private final IntStorage intStorage;
//  private final BufferArray data;
//
//  protected IntBuffer(IntStorage intStorage, long size, Data.FillCurve fillCurve, long[] offsets) {
//    super(intStorage, size, fillCurve, offsets);
//    this.intStorage = intStorage;
//    this.data = intStorage.stateStorage.getIntBuffer(intStorage.geometry.size());
//  }
//
//  @Override
//  public <T extends Data.Filler> T filler(Class<T> fillerClass) {
//
//    final PrimitiveIterator.OfLong iterator = fillCurve().cursor(intStorage.geometry);
//
//    if (fillerClass == Data.DoubleFiller.class) {
//      return (T)
//          new Data.DoubleFiller() {
//
//            @Override
//            public void add(double value) {
//              data.add(iterator.nextLong(), value);
//              if (getHistogram() != null) {
//                getHistogram().insert(value);
//              }
//              //                if (!iterator.hasNext()) {
//              //                  finalizeStorage();
//              //                }
//            }
//          };
//    } else if (fillerClass == Data.IntFiller.class) {
//      return (T)
//          new Data.IntFiller() {
//
//            @Override
//            public void add(int value) {
//              data.add(iterator.nextLong(), (double) value);
//              if (getHistogram() != null) {
//                getHistogram().insert((double) value);
//              }
//              //                if (!iterator.hasNext()) {
//              //                  finalizeStorage();
//              //                }
//            }
//          };
//    } else if (fillerClass == Data.LongFiller.class) {
//      return (T)
//          new Data.LongFiller() {
//
//            @Override
//            public void add(long value) {
//              data.add(iterator.nextLong(), (double) value);
//              if (getHistogram() != null) {
//                getHistogram().insert((double) value);
//              }
//              //                if (!iterator.hasNext()) {
//              //                  finalizeStorage();
//              //                }
//            }
//          };
//    } else if (fillerClass == Data.FloatFiller.class) {
//      return (T)
//          new Data.FloatFiller() {
//
//            @Override
//            public void add(float value) {
//              data.add(iterator.nextLong(), value);
//              if (getHistogram() != null) {
//                getHistogram().insert((double) value);
//              }
//              //                if (!iterator.hasNext()) {
//              //                  finalizeStorage();
//              //                }
//            }
//          };
//    }
//
//    throw new KlabIllegalStateException("Unexpected filler type requested for buffer");
//  }
//}
