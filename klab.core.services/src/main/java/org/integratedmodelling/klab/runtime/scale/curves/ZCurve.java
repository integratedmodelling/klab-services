//package org.integratedmodelling.klab.runtime.scale.curves;
//
//import java.util.function.Function;
//
//public class ZCurve {
//
//    /**
//     * Implements the the algorithm defined in: Tropf paper to find:
//     * LITMAX: maximum z-index in query range smaller than current point, xd
//     * BIGMIN: minimum z-index in query range greater than current point, xd
//     *
//     * @param load: function that knows how to load bits into appropraite dimention of a z-index
//     * @param xd: z-index that is outside of the query range
//     * @param rmin: minimum z-index of the query range, inclusive
//     * @param rmax: maximum z-index of the query range, inclusive
//     * @return (LITMAX, BIGMIN)
//     */
//    public static Pair<Long, Long> zdiv(Function<Tuple4<Long, Long, Integer, Integer>, Long> load, int dims, long xd, long rmin, long rmax) {
//        if (rmin >= rmax) {
//            throw new IllegalArgumentException("min (" + rmin + ") must be less than max " + rmax);
//        }
//        long zmin = rmin;
//        long zmax = rmax;
//        long bigmin = 0L;
//        long litmax = 0L;
//
//        for (int i = 63; i >= 0; i--) {
//            int bits = i / dims + 1;
//            int dim = i % dims;
//
//            int xdBit = bit(xd, i);
//            int zminBit = bit(zmin, i);
//            int zmaxBit = bit(zmax, i);
//
//            if (xdBit == 0 && zminBit == 0 && zmaxBit == 0) {
//                // continue
//            } else if (xdBit == 0 && zminBit == 0 && zmaxBit == 1) {
//                zmax = load.apply(new Tuple4<>(zmax, under(bits), bits, dim));
//                bigmin = load.apply(new Tuple4<>(zmin, over(bits), bits, dim));
//            } else if (xdBit == 0 && zminBit == 1 && zmaxBit == 1) {
//                bigmin = zmin;
//                return new Pair<>(litmax, bigmin);
//            } else if (xdBit == 1 && zminBit == 0 && zmaxBit == 0) {
//                litmax = zmax;
//                return new Pair<>(litmax, bigmin);
//            } else if (xdBit == 1 && zminBit == 0 && zmaxBit == 1) {
//                litmax = load.apply(new Tuple4<>(zmax, under(bits), bits, dim));
//                zmin = load.apply(new Tuple4<>(zmin, over(bits), bits, dim));
//            } else if (xdBit == 1 && zminBit == 1 && zmaxBit == 1) {
//                // continue
//            } else {
//                throw new IllegalStateException("Unexpected case: (" + xdBit + ", " + zminBit + ", " + zmaxBit + ") at index " + i);
//            }
//        }
//        return new Pair<>(litmax, bigmin);
//    }
//
//    private static int bit(long x, int idx) {
//        return (int) ((x & (1L << idx)) >> idx);
//    }
//
//    private static long over(int bits) {
//        return 1L << (bits - 1);
//    }
//
//    private static long under(int bits) {
//        return (1L << (bits - 1)) - 1;
//    }
//
//    public static class Tuple4<T1, T2, T3, T4> {
//        private final T1 t1;
//        private final T2 t2;
//        private final T3 t3;
//        private final T4 t4;
//
//        public Tuple4(T1 t1, T2 t2, T3 t3, T4 t4) {
//            this.t1 = t1;
//            this.t2 = t2;
//            this.t3 = t3;
//            this.t4 = t4;
//        }
//    }
//
//    private static class Pair<T1, T2> {
//        private final T1 t1;
//        private final T2 t2;
//
//        public Pair(T1 t1, T2 t2) {
//            this.t1 = t1;
//            this.t2 = t2;
//        }
//    }
//}