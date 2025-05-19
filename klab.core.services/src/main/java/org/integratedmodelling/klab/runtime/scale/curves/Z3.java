//package org.integratedmodelling.klab.runtime.scale.curves;
//
//import java.util.function.BiFunction;
//
//public class Z3 {
//    private final long z;
//
//    private Z3(long z) {
//        this.z = z;
//    }
//
//    public boolean lessThan(Z3 other) {
//        return z < other.z;
//    }
//
//    public boolean greaterThan(Z3 other) {
//        return z > other.z;
//    }
//
//    public boolean greaterThanOrEqual(Z3 other) {
//        return z >= other.z;
//    }
//
//    public boolean lessThanOrEqual(Z3 other) {
//        return z <= other.z;
//    }
//
//    public Z3 plus(long offset) {
//        return new Z3(z + offset);
//    }
//
//    public Z3 minus(long offset) {
//        return new Z3(z - offset);
//    }
//
//    public boolean equals(Z3 other) {
//        return other.z == z;
//    }
//
//    public int[] decode() {
//        return new int[] { combine((int) z), combine((int) (z >> 1)), combine((int) (z >> 2)) };
//    }
//
//    public int dim(int i) {
//        return combine((int) (z >> i));
//    }
//
//    public boolean inRange(Z3 rmin, Z3 rmax) {
//        int[] xyz = decode();
//        return xyz[0] >= rmin.dim(0) &&
//                xyz[0] <= rmax.dim(0) &&
//                xyz[1] >= rmin.dim(1) &&
//                xyz[1] <= rmax.dim(1) &&
//                xyz[2] >= rmin.dim(2) &&
//                xyz[2] <= rmax.dim(2);
//    }
//
//    public Z3 mid(Z3 p) {
//        if (p.z < z)
//            return new Z3(p.z + (z - p.z) / 2);
//        else
//            return new Z3(z + (p.z - z) / 2);
//    }
//
//    public String bitsToString() {
//        return String.format("(%" + 16 + "s)(%" + 8 + "s,%" + 8 + "s,%" + 8 + "s)", Long.toBinaryString(z), Integer.toBinaryString(dim(0)), Integer.toBinaryString(dim(1)), Integer.toBinaryString(dim(2)));
//    }
//
//    @Override
//    public String toString() {
//        int[] xyz = decode();
//        return String.format("%d (%d, %d, %d)", z, xyz[0], xyz[1], xyz[2]);
//    }
//
//    public static final int MAX_BITS = 21;
//    public static final long MAX_MASK = 0x1fffffL;
//    public static final int MAX_DIM = 3;
//
//    public static Z3 apply(long zvalue) {
//        return new Z3(zvalue);
//    }
//
//    private static long split(long value) {
//        long x = value & MAX_MASK;
//        x = (x | x << 32) & 0x1f00000000ffffL;
//        x = (x | x << 16) & 0x1f0000ff0000ffL;
//        x = (x | x << 8) & 0x100f00f00f00f00fL;
//        x = (x | x << 4) & 0x10c30c30c30c30c3L;
//        return (x | x << 2) & 0x1249249249249249L;
//    }
//
//    private static int combine(long z) {
//        long x = z & 0x1249249249249249L;
//        x = (x ^ (x >> 2)) & 0x10c30c30c30c30c3L;
//        x = (x ^ (x >> 4)) & 0x100f00f00f00f00fL;
//        x = (x ^ (x >> 8)) & 0x1f0000ff0000ffL;
//        x = (x ^ (x >> 16)) & 0x1f00000000ffffL;
//        x = (x ^ (x >> 32)) & MAX_MASK;
//        return (int) x;
//    }
//
//    public static Z3 apply(int x, int y, int z) {
//        return new Z3(split(x) | split(y) << 1 | split(z) << 2);
//    }
//
//    public static Z3Range unapply(Z3 z) {
//        int[] xyz = z.decode();
//        return new Z3Range(z, new Z3(xyz[0], xyz[1], xyz[2]));
//    }
//
//    public static Z3Range[] zdivide(Z3 p, Z3 rmin, Z3 rmax) {
//        long[] result = zdiv(load, MAX_DIM).apply(p.z, rmin.z, rmax.z);
//        return new Z3Range[] { new Z3Range(new Z3(result[0]), new Z3(result[1])) };
//    }
//
//    private static long load(long target, long p, int bits, int dim) {
//        long mask = ~(split(MAX_MASK >> (MAX_BITS - bits)) << dim);
//        long wiped = target & mask;
//        return wiped | (split(p) << dim);
//    }
//
//    private static BiFunction<Long, Long, Long[]> zdiv(TriFunction<Long, Long, Integer, Long> load, int maxDim) {
//        return (p, rmin, rmax) -> {
//            long litmax = 0;
//            long bigmin = Long.MAX_VALUE;
//
//            for (int i = 0; i < maxDim; i++) {
//                long min = load.apply(0, rmin, 1 << i, i);
//                long max = load.apply(0, rmax, 1 << i, i);
//                if (p >= min && p <= max) {
//                    litmax = Math.max(litmax, min);
//                    bigmin = Math.min(bigmin, max);
//                }
//            }
//
//            return new Long[] { litmax, bigmin };
//        };
//    }
//
//    public static Iterable<Z3Range> zranges(Z3 min, Z3 max) {
//        MergeQueue mq = new MergeQueue();
//        Z3Range sr = new Z3Range(min, max);
//
//        int recCounter = 0;
//        int reportCounter = 0;
//
//        zranges(0L, MAX_BITS * MAX_DIM, 0, sr, mq, recCounter, reportCounter);
//
//        return mq.toList();
//    }
//
//    private static void zranges(long prefix, int offset, int quad, Z3Range sr, MergeQueue mq, int recCounter, int reportCounter) {
//        recCounter++;
//
//        long min = prefix | (quad << offset);
//        long max = min | ((1L << offset) - 1);
//        Z3Range qr = new Z3Range(new Z3(min), new Z3(max));
//        if (sr.contains(qr)) {
//            mq.add(qr.min.z, qr.max.z);
//            reportCounter++;
//        } else if (offset > 0 && sr.overlaps(qr)) {
//            zranges(min, offset - MAX_DIM, 0, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 1, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 2, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 3, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 4, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 5, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 6, sr, mq, recCounter, reportCounter);
//            zranges(min, offset - MAX_DIM, 7, sr, mq, recCounter, reportCounter);
//        }
//    }
//
//    private interface TriFunction<T, U, V, R> {
//        R apply(T t, U u, V v);
//    }
//
//    private static class Z3Range {
//        private final Z3 min;
//        private final Z3 max;
//
//        private Z3Range(Z3 min, Z3 max) {
//            this.min = min;
//            this.max = max;
//        }
//
//        private boolean contains(Z3Range other) {
//            return other.min.z >= min.z && other.max.z <= max.z;
//        }
//
//        private boolean overlaps(Z3Range other) {
//            return other.min.z <= max.z && other.max.z >= min.z;
//        }
//    }
//
//    private static class MergeQueue {
//        private final java.util.List<Z3Range> ranges = new java.util.ArrayList<>();
//
//        private void add(long min, long max) {
//            ranges.add(new Z3Range(new Z3(min), new Z3(max)));
//        }
//
//        private Iterable<Z3Range> toList() {
//            return ranges;
//        }
//    }
//}