package org.integratedmodelling.klab.runtime.storage;

import java.util.PrimitiveIterator;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.ojalgo.array.BufferArray;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native
 * operation (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class LongStorage extends AbstractStorage<LongStorage.LongBuffer> {

    public class LongBuffer extends AbstractBuffer {

        private final BufferArray data;

        protected LongBuffer(Geometry geometry, Data.FillCurve fillCurve) {
            super(geometry, fillCurve);
            this.data = scope.getLongBuffer(geometry.size());
        }

        @Override
        public <T extends Data.Filler> T filler(Class<T> fillerClass) {

            final PrimitiveIterator.OfLong iterator = fillCurve().iterate(geometry);

            if (fillerClass == Data.DoubleFiller.class) {
                return (T)
                        new Data.DoubleFiller() {

                            @Override
                            public void add(double value) {
                                data.add(iterator.nextLong(), value);
                                if (histogram != null) {
                                    histogram.insert(value);
                                }
                            }
                        };
            } else if (fillerClass == Data.IntFiller.class) {
                return (T)
                        new Data.IntFiller() {

                            @Override
                            public void add(int value) {
                                data.add(iterator.nextLong(), (double) value);
                                if (histogram != null) {
                                    histogram.insert((double) value);
                                }
                            }
                        };
            } else if (fillerClass == Data.LongFiller.class) {
                return (T)
                        new Data.LongFiller() {

                            @Override
                            public void add(long value) {
                                data.add(iterator.nextLong(), (double) value);
                                if (histogram != null) {
                                    histogram.insert((double) value);
                                }
                            }
                        };
            } else if (fillerClass == Data.FloatFiller.class) {
                return (T)
                        new Data.FloatFiller() {

                            @Override
                            public void add(float value) {
                                data.add(iterator.nextLong(), value);
                                if (histogram != null) {
                                    histogram.insert((double) value);
                                }
                            }
                        };
            }

            throw new KlabIllegalStateException("Unexpected filler type requested for buffer");
        }
    }

    public LongStorage(Geometry geometry, StateStorageImpl scope) {
        super(Type.LONG, geometry, scope);
    }

    @Override
    public LongBuffer buffer(Geometry geometry, Data.FillCurve fillCurve) {
        var ret = new LongBuffer(geometry, fillCurve);
        registerBuffer(ret);
        return ret;
    }

}

