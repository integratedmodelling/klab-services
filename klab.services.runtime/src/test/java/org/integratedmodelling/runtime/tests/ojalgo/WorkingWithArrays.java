package org.integratedmodelling.runtime.tests.ojalgo;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import org.ojalgo.OjAlgoUtils;
import org.ojalgo.array.*;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.random.Cauchy;
import org.ojalgo.random.Normal;
import org.ojalgo.random.Uniform;
import org.ojalgo.scalar.ComplexNumber;
import org.ojalgo.scalar.Quaternion;
import org.ojalgo.scalar.RationalNumber;
import org.ojalgo.structure.AccessAnyD.MatrixView;
import org.ojalgo.structure.AccessAnyD.VectorView;
import org.ojalgo.structure.ElementView1D;
import org.ojalgo.type.math.MathType;

/**
 * Demonstrate some basic array functionality.
 *
 * @see https://www.ojalgo.org/2021/08/working-with-arrays/
 * @see https://github.com/optimatika/ojAlgo/wiki/Working-with-arrays
 */
public class WorkingWithArrays {

	public static void main(final String[] args) {

		BasicLogger.debug();
		BasicLogger.debug(WorkingWithArrays.class);
		BasicLogger.debug(OjAlgoUtils.getTitle());
		BasicLogger.debug(OjAlgoUtils.getDate());
		BasicLogger.debug();

		/*
		 * The top level classes in org.ojalgo.array are:
		 */
		Array1D<BigDecimal> array1D = Array1D.R256.make(12);
		Array2D<ComplexNumber> array2D = Array2D.C128.makeDense(12, 8);
		ArrayAnyD<Double> arrayAnyD = ArrayAnyD.R064.makeSparse(12, 8, 4);

		/*
		 * They represent 1-, 2- and N-dimensional arrays. They are generic, so they
		 * could hold many different element/component types, but the generic type
		 * argument doesn't always describe exactly what the element type is. For
		 * instance "Double" does NOT only represent 'double' but any/all primitive
		 * types (double, float, long, int short and byte). There is an enum outlining
		 * which number types ojAlgo supports.
		 */

		BasicLogger.debug("Element types supported ny ojAlgo");
		BasicLogger.debug("=================================");
		for (MathType mathType : MathType.values()) {
			BasicLogger.debug("{} := Math number set {} implemented as {} x {}", mathType, mathType.getNumberSet(),
					mathType.getComponents(), mathType.getJavaType());
		}
		BasicLogger.debug();

		/*
		 * There's a couple of things to take note of regarding those 3 top level
		 * classes: (1) Every single method implemented is declared in some interface
		 * elsewhere – typically in the org.ojalgo.structure package – and those same
		 * interfaces are widely used throughout ojAlgo. (2) If you look at the source
		 * code implementations you'll find that essentially nothing actually happens in
		 * these classes. Instead just about everything is delegeted to lower level
		 * classes. Those lower level implementations can in turn be a number of
		 * different things, but in the end there has to be a plain/dense array like
		 * float[], double[] or Number[]. There is a wide range of such plain/dense
		 * array implementations in ojAlgo.
		 */

		/*
		 * Plain Java array based
		 */
		DenseArray.Factory<Double> plainByteArrayFactory = ArrayZ008.FACTORY; // 8-bit Integer (byte)
		DenseArray.Factory<Double> plainShortArrayFactory = ArrayZ016.FACTORY; // 16-bit Integer (short)
		DenseArray.Factory<Double> plainIntArrayFactory = ArrayZ032.FACTORY; // 32-bit Integer (int)
		DenseArray.Factory<Double> plainLongArrayFactory = ArrayZ064.FACTORY; // 64-bit Integer (long)
		DenseArray.Factory<Double> plainFloatArrayFactory = ArrayR032.FACTORY; // 32-bit Real (float)
		DenseArray.Factory<Double> plainDoubleArrayFactory = ArrayR064.FACTORY; // 64-bit Real (double)
		DenseArray.Factory<BigDecimal> plainBigDecimalArrayFactory = ArrayR256.FACTORY; // 128-bit Real (BigDecimal)
		DenseArray.Factory<RationalNumber> plainRationalNumberArrayFactory = ArrayQ128.FACTORY; // 128-bit Rational
																								// Number (2 x long)
		DenseArray.Factory<ComplexNumber> plainComplexNumberArrayFactory = ArrayC128.FACTORY; // 128-bit Complex Number
																								// (2 x double)
		DenseArray.Factory<Quaternion> plainQuaternionArrayFactory = ArrayH256.FACTORY; // 256-bit Quaternion (4 x
																						// double)

		/*
		 * Buffer based "arrays"
		 */
		DenseArray.Factory<Double> bufferByteArrayFactory = BufferArray.Z008;
		DenseArray.Factory<Double> bufferShortArrayFactory = BufferArray.Z016;
		DenseArray.Factory<Double> bufferIntArrayFactory = BufferArray.Z032;
		DenseArray.Factory<Double> bufferLongArrayFactory = BufferArray.Z064;
		DenseArray.Factory<Double> bufferFloatArrayFactory = BufferArray.R032;
		DenseArray.Factory<Double> bufferDoubleArrayFactory = BufferArray.R064;

		/*
		 * Using Unsafe there are also off-heap "arrays"
		 */
		DenseArray.Factory<Double> offHeapByteArrayFactory = OffHeapArray.Z008;
		DenseArray.Factory<Double> offHeapShortArrayFactory = OffHeapArray.Z016;
		DenseArray.Factory<Double> offHeapIntArrayFactory = OffHeapArray.Z032;
		DenseArray.Factory<Double> offHeapLongArrayFactory = OffHeapArray.Z064;
		DenseArray.Factory<Double> offHeapFloatArrayFactory = OffHeapArray.R032;
		DenseArray.Factory<Double> offHeapDoubleArrayFactory = OffHeapArray.R064;

		/*
		 * Those factories dictate both the element type and where/how they are stored.
		 * The factories can be used directly to instantiate plain/dense arrays,
		 */
		DenseArray<BigDecimal> plainBigDecimalArray = plainBigDecimalArrayFactory.make(512);
		DenseArray<ComplexNumber> plainComplexNumberArray = plainComplexNumberArrayFactory.makeFilled(512,
				Normal.standard());
		DenseArray<Double> bufferFloatArray = bufferFloatArrayFactory.makeFilled(512, Uniform.standard());
		DenseArray<Double> offHeapDoubleArray = offHeapDoubleArrayFactory.makeFilled(512, Cauchy.standard());
		DenseArray<Double> plainFloatArray = plainFloatArrayFactory.copy(plainBigDecimalArray);
		DenseArray<Double> plainDoubleArray = plainDoubleArrayFactory.copy(plainComplexNumberArray);
		DenseArray<Quaternion> plainQuaternionArray = plainQuaternionArrayFactory.copy(bufferFloatArray);
		DenseArray<RationalNumber> plainRationalNumberArray = plainRationalNumberArrayFactory.copy(offHeapDoubleArray);

		/*
		 * or they can be used as input to creating higher level factories (factories
		 * that create higher level data structures).
		 */

		array1D = Array1D.factory(plainBigDecimalArrayFactory).make(100);
		array2D = Array2D.factory(plainComplexNumberArrayFactory).make(10, 10);
		arrayAnyD = ArrayAnyD.factory(offHeapDoubleArrayFactory).make(10, 100, 1000, 3);

		SparseArray<Double> sparse = SparseArray.factory(plainFloatArrayFactory).make(1000);

		List<Double> list = NumberList.factory(bufferDoubleArrayFactory).make();
		LongToNumberMap<Double> map = LongToNumberMap.factory(offHeapDoubleArrayFactory).make();

		/*
		 * If you implement your own dense array factory, then just plug it in and get
		 * access to all higher level functionalaity.
		 */

		/*
		 * For the buffer based array classes there is also an option to create "arrays"
		 * backed by memory mapped files:
		 */
		File scratchPath = new File(System.getProperty("user.home") + File.separator + "ojscratch");
		scratchPath.mkdirs();
		BufferArray.MappedFileFactory fileBackedArrayFactory = BufferArray.R032.newMapped(scratchPath);

		/*
		 * Regarding Any-D Arrays
		 */

		long[] shape = { 2, 3, 1, 4 };

		arrayAnyD = ArrayAnyD.factory(plainDoubleArrayFactory).make(shape);

		/*
		 * The rank of an Any-D array is the length of the shape used to instatiate it.
		 */
		BasicLogger.debug("shape.lengtgh {} = rank() {}", shape.length, arrayAnyD.rank());
		BasicLogger.debug("Original shape: {}", arrayAnyD.shape());

		ArrayAnyD<Double> squeezed = arrayAnyD.squeeze();
		/*
		 * squeeze() removes all indices/dimensions of range 1.
		 */
		BasicLogger.debug("Squeezed shape: {}", squeezed.shape());

		ArrayAnyD<Double> reshaped = arrayAnyD.reshape(3, 2, 4);
		/*
		 * reshape(...) changes the indexing but must maintain the same total number of
		 * elements.
		 */
		BasicLogger.debug("Reshaped shape: {}", reshaped.shape());

		/*
		 * Now we have have 3 ArrayAnyD instances, with different shapes, all backed by
		 * the same lower level plain/dense array.
		 */
		BasicLogger.debug("3 different shapes: {}, {} and {}", arrayAnyD.shape(), squeezed.shape(), reshaped.shape());

		/*
		 * Let's fill that squeezed instance with some numbers
		 */
		squeezed.loopAllReferences(ref -> squeezed.set(ref, 1D + ref[2]));

		/*
		 * Now using the reshaped instance we'll check the contents. Apart from random
		 * access contents can be iterated element-wise, vector-wise or matrix-wise. In
		 * this case there are 24 elements, 8 vectors (each of length 3) and 4 matrices
		 * (each of 2 column vectors of length 3 – 3 rows and 2 columns).
		 */

		BasicLogger.debug();
		BasicLogger.debug("Element-wise iteration");
		for (ElementView1D<Double, ?> element : reshaped.elements()) {
			BasicLogger.debug("ElementView {}: {}", element.index(), element);
		}

		BasicLogger.debug();
		BasicLogger.debug("Vector-wise iteration");
		for (VectorView<Double> vector : reshaped.vectors()) {
			BasicLogger.debug("VectorView {}: {}", vector.index(), vector);
		}

		BasicLogger.debug();
		BasicLogger.debug("Matrix-wise iteration");
		for (MatrixView<Double> matrix : reshaped.matrices()) {
			BasicLogger.debug("MatrixView {}: {}", matrix.index(), matrix);
		}

	}

}