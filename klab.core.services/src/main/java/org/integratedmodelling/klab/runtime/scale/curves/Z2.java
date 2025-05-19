//package org.integratedmodelling.klab.runtime.scale.curves;
//
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.function.BiFunction;
//
//public class Z2 {
//    private final long z;
//
//    public Z2(long z) {
//        this.z = z;
//    }
//
//    public boolean lessThan(Z2 other) {
//        return z < other.z;
//    }
//
//    public boolean greaterThan(Z2 other) {
//        return z > other.z;
//    }
//
//    public Z2 add(long offset) {
//        return new Z2(z + offset);
//    }
//
//    public Z2 subtract(long offset) {
//        return new Z2(z - offset);
//    }
//
//    public boolean equals(Z2 other) {
//        return other.z == z;
//    }
//
//    public int[] decode() {
//        return new int[] { combine((int) z), combine((int) (z >> 1)) };
//    }
//
//    public int dim(int i) {
//        return combine((int) (z >> i));
//    }
//
//    public Z2 mid(Z2 p) {
//        Z2 ans;
//        if (p.z < z) {
//            ans = new Z2(p.z + (z - p.z) / 2);
//        } else {
//            ans = new Z2(z + (p.z - z) / 2);
//        }
//        return ans;
//    }
//
//    public String bitsToString() {
//        return String.format("(%" + 16 + "s)(%" + 8 + "s,%" + 8 + "s)", Long.toBinaryString(z), Integer.toBinaryString(dim(0)), Integer.toBinaryString(dim(1)));
//    }
//
//    @Override
//    public String toString() {
//        int[] decode = decode();
//        return String.format("%d (%d,%d)", z, decode[0], decode[1]);
//    }
//
//    private static final int MAX_BITS = 31;
//    private static final long MAX_MASK = 0x7fffffffL; // ignore the sign bit, using it breaks < relationship
//    private static final int MAX_DIM = 2;
//
//    public static long split(long value) {
//        long x = value & MAX_MASK;
//        x = (x ^ (x << 32)) & 0x00000000ffffffffL;
//        x = (x ^ (x << 16)) & 0x0000ffff0000ffffL;
//        x = (x ^ (x << 8)) & 0x00ff00ff00ff00ffL; // 11111111000000001111111100000000..
//        x = (x ^ (x << 4)) & 0x0f0f0f0f0f0f0f0fL; // 1111000011110000
//        x = (x ^ (x << 2)) & 0x3333333333333333L; // 11001100..
//        x = (x ^ (x << 1)) & 0x5555555555555555L; // 1010...
//        return x;
//    }
//
//    public static int combine(long z) {
//        long x = z & 0x5555555555555555L;
//        x = (x ^ (x >> 1)) & 0x3333333333333333L;
//        x = (x ^ (x >> 2)) & 0x0f0f0f0f0f0f0f0fL;
//        x = (x ^ (x >> 4)) & 0x00ff00ff00ff00ffL;
//        x = (x ^ (x >> 8)) & 0x0000ffff0000ffffL;
//        x = (x ^ (x >> 16)) & 0x00000000ffffffffL;
//        return (int) x;
//    }
//
//    public static Z2 apply(int x, int y) {
//        return new Z2(split(x) | (split(y) << 1));
//    }
//
//    public static Z2Range unapply(Z2 z) {
//        return new Z2Range(z.decode());
//    }
//
//    public static Z2Range zdivide(Z2 p, Z2 rmin, Z2 rmax) {
//        long[] zdiv = ZCurve.zdiv(Z2::load, MAX_DIM).apply(p.z, rmin.z, rmax.z);
//        return new Z2Range(new Z2(zdiv[0]), new Z2(zdiv[1]));
//    }
//
//    private static long load(long target, long p, int bits, int dim) {
//        long mask = ~(split(MAX_MASK >> (MAX_BITS - bits)) << dim);
//        long wiped = target & mask;
//        return wiped | (split(p) << dim);
//    }
//
//    private static BiFunction<Long, Long, long[]> zdiv = (p, r) -> {
//        long litmax = p & ~(1L << (MAX_BITS - 1 - MAX_DIM));
//        long bigmin = p | (1L << (MAX_BITS - 1 - MAX_DIM));
//        return new long[] { litmax, bigmin };
//    };
//
//    public static Iterable<Z2Range> zranges(Z2 min, Z2 max) {
//        MergeQueue mq = new MergeQueue();
//        Z2Range sr = new Z2Range(min, max);
//
//        AtomicInteger recCounter = new AtomicInteger();
//        AtomicInteger reportCounter = new AtomicInteger();
//
//        zranges(0L, MAX_BITS * MAX_DIM, 0, mq, sr, (prefix, offset, quad) -> {
//            recCounter.getAndIncrement();
//
//            long lmin = prefix | (quad << offset); // QR + 000..
//            long lmax = lmin | ((1L << offset) - 1); // QR + 111..
//
//            Z2Range qr = new Z2Range(new Z2(lmin), new Z2(lmax));
//            if (sr.contains(qr)) { // whole range matches, happy day
//                mq.add(qr.min.z, qr.max.z);
//                reportCounter.getAndIncrement();
//            } else if (offset > 0 && sr.overlaps(qr)) { // some portion of this range are excluded
//                zranges(lmin, offset - MAX_DIM, 0, mq, sr, (p, o, q) -> {});
//                zranges(lmin, offset - MAX_DIM, 1, mq, sr, (p, o, q) -> {});
//                zranges(lmin, offset - MAX_DIM, 2, mq, sr, (p, o, q) -> {});
//                zranges(lmin, offset - MAX_DIM, 3, mq, sr, (p, o, q) -> {});
//            }
//        });
//
//        return mq.toList();
//    }
//
//    private static void zranges(long prefix, int offset, long quad, MergeQueue mq, Z2Range sr, TriConsumer<Long, Integer, Long> consumer) {
//        consumer.accept(prefix, offset, quad);
//    }
//
//    @FunctionalInterface
//    private interface TriConsumer<T, U, V> {
//        void accept(T t, U u, V v);
//    }
//
//    private static class MergeQueue {
//        private final java.util.List<Z2Range> ranges = new java.util.ArrayList<>();
//
//        public void add(long min, long max) {
//            ranges.add(new Z2Range(new Z2(min), new Z2(max)));
//        }
//
//        public Iterable<Z2Range> toList() {
//            return ranges;
//        }
//    }
//
//    private static class Z2Range {
//        private final Z2 min;
//        private final Z2 max;
//
//        public Z2Range(Z2 min, Z2 max) {
//            this.min = min;
//            this.max = max;
//        }
//
//        public Z2Range(int[] decode) {
//            this.min = new Z2(split(decode[0]) | (split(decode[1]) << 1));
//            this.max = new Z2(min.z | ((1L << MAX_DIM) - 1));
//        }
//
//        public boolean contains(Z2Range other) {
//            return other.min.z >= min.z && other.max.z <= max.z;
//        }
//
//        public boolean overlaps(Z2Range other) {
//            return other.min.z <= max.z && other.max.z >= min.z;
//        }
//    }
//}