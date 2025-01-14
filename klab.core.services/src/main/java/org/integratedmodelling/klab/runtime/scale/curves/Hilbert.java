//package org.integratedmodelling.klab.runtime.scale.curves;
//
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.function.Function;
//
//public class HilbertSpaceTimeKeyIndex implements KeyIndex<SpaceTimeKey> {
//    private final KeyBounds<SpaceTimeKey> keyBounds;
//    private final int xResolution;
//    private final int yResolution;
//    private final int temporalResolution;
//    private final long startMillis;
//    private final long timeWidth;
//    private final long temporalBinCount;
//    private final SpaceTimeKey minKey;
//    private final CompactHilbertCurve chc;
//
//    private HilbertSpaceTimeKeyIndex(KeyBounds<SpaceTimeKey> keyBounds, int xResolution, int yResolution, int temporalResolution) {
//        this.keyBounds = keyBounds;
//        this.xResolution = xResolution;
//        this.yResolution = yResolution;
//        this.temporalResolution = temporalResolution;
//        this.startMillis = keyBounds.getMinKey().getTemporalKey().getTime().toInstant().toEpochMilli();
//        this.timeWidth = keyBounds.getMaxKey().getTemporalKey().getTime().toInstant().toEpochMilli() - this.startMillis;
//        this.temporalBinCount = Math.pow(2, temporalResolution);
//        this.minKey = keyBounds.getMinKey().getSpatialKey();
//        this.chc = new CompactHilbertCurve(new MultiDimensionalSpec(List.of(xResolution, yResolution, temporalResolution).stream().map(Integer::valueOf).toList()));
//    }
//
//    public static HilbertSpaceTimeKeyIndex apply(KeyBounds<SpaceTimeKey> keyBounds, int spatialResolution, int temporalResolution) {
//        return apply(keyBounds, spatialResolution, spatialResolution, temporalResolution);
//    }
//
//    public static HilbertSpaceTimeKeyIndex apply(KeyBounds<SpaceTimeKey> keyBounds, int xResolution, int yResolution, int temporalResolution) {
//        return new HilbertSpaceTimeKeyIndex(keyBounds, xResolution, yResolution, temporalResolution);
//    }
//
//    public static HilbertSpaceTimeKeyIndex apply(SpaceTimeKey minKey, SpaceTimeKey maxKey, int spatialResolution, int temporalResolution) {
//        return apply(KeyBounds.create(minKey, maxKey), spatialResolution, temporalResolution);
//    }
//
//    private long binTime(SpaceTimeKey key) {
//        long bin = ((key.getTemporalKey().getTime().toInstant().toEpochMilli() - startMillis) * (long) temporalBinCount) / timeWidth;
//        return bin == temporalBinCount ? bin - 1 : bin;
//    }
//
//    @Override
//    public BigInteger toIndex(SpaceTimeKey key) {
//        BitVector[] bitVectors = new BitVector[]{
//                BitVectorFactories.OPTIMAL.apply(xResolution),
//                BitVectorFactories.OPTIMAL.apply(yResolution),
//                BitVectorFactories.OPTIMAL.apply(temporalResolution)
//        };
//
//        int col = key.getSpatialKey().getCol() - minKey.getCol();
//        int row = key.getSpatialKey().getRow() - minKey.getRow();
//        bitVectors[0].copyFrom(col);
//        bitVectors[1].copyFrom(row);
//        bitVectors[2].copyFrom(binTime(key));
//
//        BitVector hilbertBitVector = BitVectorFactories.OPTIMAL.apply(chc.getSpec().getSumBitsPerDimension());
//        chc.index(bitVectors, 0, hilbertBitVector);
//        return BigInteger.valueOf(hilbertBitVector.toExactLong());
//    }
//
//    @Override
//    public List<Pair<BigInteger, BigInteger>> indexRanges(Pair<SpaceTimeKey, SpaceTimeKey> keyRange) {
//        List<LongRange> ranges = List.of(
//                LongRange.of(keyRange.getFirst().getSpatialKey().getCol() - minKey.getCol(), keyRange.getSecond().getSpatialKey().getCol() - minKey.getCol() + 1),
//                LongRange.of(keyRange.getFirst().getSpatialKey().getRow() - minKey.getRow(), keyRange.getSecond().getSpatialKey().getRow() - minKey.getRow() + 1),
//                LongRange.of(binTime(keyRange.getFirst()), binTime(keyRange.getSecond()) + 1)
//        );
//
//        RegionInspector<LongRange, LongContent> regionInspector = SimpleRegionInspector.create(
//                ranges,
//                new LongContent(1),
//                Function.identity(),
//                LongRangeHome.INSTANCE,
//                new LongContent(0L)
//        );
//
//        PlainFilterCombiner<LongRange, Long, LongContent, LongRange> combiner =
//                new PlainFilterCombiner<>(LongRange.of(0, 1));
//
//        BacktrackingQueryBuilder<LongRange, Long, LongContent, LongRange> queryBuilder = BacktrackingQueryBuilder.create(
//                regionInspector,
//                combiner,
//                Integer.MAX_VALUE,
//                true,
//                LongRangeHome.INSTANCE,
//                new LongContent(0L)
//        );
//
//        chc.accept(new ZoomingSpaceVisitorAdapter(chc, queryBuilder));
//        List<FilteredIndexRange<LongRange>> filteredIndexRanges = queryBuilder.get().getFilteredIndexRanges();
//
//        List<Pair<BigInteger, BigInteger>> result = new ArrayList<>(filteredIndexRanges.size());
//        for (FilteredIndexRange<LongRange> range : filteredIndexRanges) {
//            result.add(new Pair<>(BigInteger.valueOf(range.getIndexRange().getStart()), BigInteger.valueOf(range.getIndexRange().getEnd() - 1)));
//        }
//        return result;
//    }
//}