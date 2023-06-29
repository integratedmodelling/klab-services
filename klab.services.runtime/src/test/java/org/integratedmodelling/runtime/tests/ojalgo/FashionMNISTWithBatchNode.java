package org.integratedmodelling.runtime.tests.ojalgo;

import static org.ojalgo.ann.ArtificialNeuralNetwork.Activator.*;
import static org.ojalgo.function.constant.PrimitiveMath.DIVIDE;

import java.awt.image.BufferedImage;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.ojalgo.OjAlgoUtils;
import org.ojalgo.ann.ArtificialNeuralNetwork;
import org.ojalgo.ann.NetworkInvoker;
import org.ojalgo.ann.NetworkTrainer;
import org.ojalgo.array.ArrayAnyD;
import org.ojalgo.concurrent.DaemonPoolExecutor;
import org.ojalgo.concurrent.Parallelism;
import org.ojalgo.data.DataBatch;
import org.ojalgo.data.batch.BatchManager;
import org.ojalgo.data.batch.BatchNode;
import org.ojalgo.matrix.store.Primitive32Store;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.DataInterpreter;
import org.ojalgo.netio.IDX;
import org.ojalgo.netio.ToFileWriter;
import org.ojalgo.random.FrequencyMap;
import org.ojalgo.structure.Access2D;
import org.ojalgo.structure.AccessAnyD.MatrixView;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.Stopwatch;
import org.ojalgo.type.format.NumberStyle;
import org.ojalgo.type.function.AutoConsumer;
import org.ojalgo.type.function.TwoStepMapper;

/**
 * This example program does two things:
 * <ol>
 * <li>Demonstrates basic usage of the BatchNode utility
 * <li>Train a neural network on the Fashion-MNIST dataset from Zalando Research
 * (https://github.com/zalandoresearch/fashion-mnist)
 * <p>
 * BatchNode is meant to be used with very very large datasets. The dataset used
 * in this example is not very large at all, and we don't always do things the
 * most efficient way. Just want to show a few different ways to use BatchNode.
 *
 * @see https://www.ojalgo.org/2022/05/introducing-batchnode/
 */
public class FashionMNISTWithBatchNode {

	/**
	 * A simple consumer that encapsulates a, batched, neural network trainer. We
	 * can have several of these working concurrently.
	 */
	static class ConcurrentTrainer implements Consumer<DataAndLabelPair> {

		private static final AtomicInteger COUNTER = new AtomicInteger();

		private final DataBatch myInputBatch;
		private final DataBatch myOutputBatch;
		private final NetworkTrainer myTrainer;

		ConcurrentTrainer(final ArtificialNeuralNetwork network, final int batchSize) {
			super();
			myTrainer = network.newTrainer(batchSize).rate(0.005).dropouts();
			myInputBatch = myTrainer.newInputBatch();
			myOutputBatch = myTrainer.newOutputBatch();
		}

		public void accept(final FashionMNISTWithBatchNode.DataAndLabelPair item) {

			myInputBatch.addRow(item.data);
			// The label is an integer [0,9] representing the digit in the image
			// That label is used as the index to set a single 1.0
			myOutputBatch.addRowWithSingleUnit(item.label);

			if (myInputBatch.isFull()) {
				myTrainer.train(myInputBatch, myOutputBatch);
				myInputBatch.reset();
				myOutputBatch.reset();
			}

			int iterations = COUNTER.incrementAndGet();
			if (iterations % 1_000_000 == 0) {
				BasicLogger.debug("Done {} training iterations: {}", iterations,
						STOPWATCH.stop(CalendarDateUnit.SECOND));
			}
		}
	}

	/**
	 * A simple class representing what is stored at each of the batch nodes. In
	 * this example it happens so that we store the same type of data at all the
	 * nodes. Usually that would not be case. It's more likely there is a unique
	 * type for each node.
	 */
	static class DataAndLabelPair {

		/**
		 * This is what we feed the {@link BatchNode} builder so that the node knows how
		 * to read/write data from disk.
		 */
		static final DataInterpreter<DataAndLabelPair> INTERPRETER = new DataInterpreter<>() {

			@Override
			public FashionMNISTWithBatchNode.DataAndLabelPair deserialize(final DataInput input) throws IOException {

				int nbRows = input.readInt();
				int nbCols = input.readInt();

				Primitive32Store data = Primitive32Store.FACTORY.make(nbRows, nbCols);

				for (int i = 0; i < nbRows; i++) {
					for (int j = 0; j < nbCols; j++) {
						data.set(i, j, input.readFloat());
					}
				}

				int label = input.readInt();

				return new DataAndLabelPair(data, label);
			}

			@Override
			public void serialize(final FashionMNISTWithBatchNode.DataAndLabelPair pair, final DataOutput output)
					throws IOException {

				int nbRows = pair.data.getRowDim();
				int nbCols = pair.data.getColDim();

				output.writeInt(nbRows);
				output.writeInt(nbCols);

				for (int i = 0; i < nbRows; i++) {
					for (int j = 0; j < nbCols; j++) {
						output.writeFloat(pair.data.floatValue(i, j));
					}
				}

				output.writeInt(pair.label);
			}

		};

		/**
		 * Training data - the image
		 */
		Primitive32Store data;
		/**
		 * The correct/expected category
		 */
		int label;

		DataAndLabelPair(final Access2D<Double> data, final int label) {
			super();
			if (data instanceof Primitive32Store) {
				this.data = (Primitive32Store) data;
			} else {
				this.data = Primitive32Store.FACTORY.copy(data);
			}
			this.label = label;
		}

	}

	static final AtomicInteger INCREMENTOR = new AtomicInteger();
	static final String[] LABELS = { "T-shirt/top", "Trouser", "Pullover", "Dress", "Coat", "Sandal", "Shirt",
			"Sneaker", "Bag", "Ankle boot" };
	static final File OUTPUT_TEST_IMAGES = new File(System.getProperty("user.home") + "/Developer/data/images/test/");
	static final File OUTPUT_TRAINING_IMAGES = new File(
			System.getProperty("user.home") + "/Developer/data/images/training/");
	static final Stopwatch STOPWATCH = new Stopwatch();
	static final File TEMP_BATCH_DIR = new File(System.getProperty("user.home") + "/Developer/data/temp/batch/");
	static final File TEST_IMAGES = new File(
			System.getProperty("user.home") + "/Developer/data/fashion/t10k-images-idx3-ubyte.gz");
	static final File TEST_LABELS = new File(
			System.getProperty("user.home") + "/Developer/data/fashion/t10k-labels-idx1-ubyte.gz");
	static final File TRAINING_IMAGES = new File(
			System.getProperty("user.home") + "/Developer/data/fashion/train-images-idx3-ubyte.gz");
	static final File TRAINING_LABELS = new File(
			System.getProperty("user.home") + "/Developer/data/fashion/train-labels-idx1-ubyte.gz");

	public static void main(final String[] args) throws IOException {

		BasicLogger.debug();
		BasicLogger.debug(FashionMNISTWithBatchNode.class);
		BasicLogger.debug(OjAlgoUtils.getTitle());
		BasicLogger.debug(OjAlgoUtils.getDate());
		BasicLogger.debug();

		int numberToPrint = 10;
		boolean generateImages = false;
		int epochs = 128;
		int batchSize = 100;

		/*
		 * Using a BatchManager is entirely optional. It just makes it a bit simpler to
		 * create multiple BatchNode instances with common configurations.
		 */
		BatchManager batchManager = new BatchManager(TEMP_BATCH_DIR); // Specifying a temp dir for all the node data
		batchManager.executor(DaemonPoolExecutor.newCachedThreadPool("Batch Worker")); // The thread pool used when
																						// processing data
		batchManager.fragmentation(epochs); // The number of shards - we'll make use of the fact that it matches the
											// epochs
		batchManager.parallelism(Parallelism.CORES); // The number of worker threads
		batchManager.queue(1024); // Capacity of the queues used when reading/writing to disk
		/*
		 * Using BatchManager is optional, and also all of those configurations have
		 * usable defaults.
		 */

		ArrayAnyD<Double> trainingLabels = IDX.parse(TRAINING_LABELS);
		ArrayAnyD<Double> trainingImages = IDX.parse(TRAINING_IMAGES);

		BasicLogger.debug("Parsed IDX training data files: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * Declaring a node to store the initial data. We just specify the name of a
		 * subdirectory and how to "interpret" its data.
		 */
		BatchNode<DataAndLabelPair> initialData = batchManager.newNodeBuilder("initial", DataAndLabelPair.INTERPRETER)
				.build();

		/*
		 * Write to that initial data node
		 */
		try (AutoConsumer<DataAndLabelPair> initialDataWriter = initialData.newWriter()) {

			for (MatrixView<Double> imageMatrix : trainingImages.matrices()) {

				long imageIndex = imageMatrix.index();
				int label = trainingLabels.intValue(imageIndex);

				DataAndLabelPair pair = new DataAndLabelPair(imageMatrix, label);

				initialDataWriter.write(pair);

				if (generateImages) {
					FashionMNISTWithBatchNode.generateImage(imageMatrix, label, OUTPUT_TRAINING_IMAGES);
				}
			}

		} catch (Exception cause) {
			throw new RuntimeException(cause);
		}

		BasicLogger.debug("Initial training data: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * Need to scale the matrices that make out the image. The range [0,255] should
		 * be scaled to [0,1] to be used as input to the neural network.
		 */
		BatchNode<DataAndLabelPair> scaledData = batchManager.newNodeBuilder("scaled", DataAndLabelPair.INTERPRETER)
				.build();

		try (AutoConsumer<DataAndLabelPair> scaledDataWriter = scaledData.newWriter()) {

			initialData.processAll(item -> { // Read inital data
				item.data.modifyAll(DIVIDE.by(255)); // Divide by 255
				scaledDataWriter.write(item); // Write scaled data
			});

		} catch (Exception cause) {
			throw new RuntimeException(cause);
		}

		BasicLogger.debug("Scaled training data: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * As a (wasteful) way to enable training on multiple epochs we'll create a
		 * dataset of multiple copies of the original.
		 */
		BatchNode<DataAndLabelPair> duplicatedData = batchManager
				.newNodeBuilder("duplicated", DataAndLabelPair.INTERPRETER)
				.distributor(item -> INCREMENTOR.getAndIncrement()).build();

		try (AutoConsumer<DataAndLabelPair> duplicatedDataWriter = duplicatedData.newWriter()) {

			scaledData.processAll(item -> { // Read once
				for (int e = 0; e < epochs; e++) { // Write 'epochs' times
					duplicatedDataWriter.write(item);
				} // Because of how the distributor works, and because the number
			}); // of shards match the number of epochs, this will write once to each shard.

		} catch (Exception cause) {
			throw new RuntimeException(cause);
		}

		BasicLogger.debug("Duplicated training data: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * Training works better if we shuffle the data randomly. When we read the data
		 * it is essentially read sequeltially from the shards (a few are worked on in
		 * parallel) but we write to all shards simultaneously using the distributor to
		 * decide to which shard an individual data item is sent. The default
		 * distributor is random.
		 */
		BatchNode<DataAndLabelPair> randomisedData = batchManager
				.newNodeBuilder("randomised", DataAndLabelPair.INTERPRETER).build();

		try (AutoConsumer<DataAndLabelPair> randomisedDataWriter = randomisedData.newWriter()) {

			duplicatedData.processAll(randomisedDataWriter::write);

		} catch (Exception cause) {
			throw new RuntimeException(cause);
		}

		BasicLogger.debug("Randomised training data: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * Now we have a dataset that is scaled, duplicated (many times) and suffled
		 * randomly. Maybe we should verify it somehow. Let's just count the occurrences
		 * of the different labels. There should be an equal amout of each and it should
		 * be 60,000 x 128 / 10 = 768,000.
		 */

		/*
		 * This will count the keys/labels in each of the shards, and then combine the
		 * results to a single returned FrequencyMap.
		 */
		FrequencyMap<String> frequencyMap = randomisedData
				.reduceMapped(() -> new TwoStepMapper.KeyCounter<>(pair -> LABELS[pair.label]));
		for (int i = 0; i < LABELS.length; i++) {
			BasicLogger.debug("There are {} {} instances in the scaled/duplicated/randomised traing set.",
					frequencyMap.getFrequency(LABELS[i]), LABELS[i]);
		}
		BasicLogger.debug(frequencyMap.sample());

		BasicLogger.debug("Training data verified: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * This is exacly the same network structure as used in the example with the
		 * original MNIST dataset.
		 */
		ArtificialNeuralNetwork network = ArtificialNeuralNetwork.builder(28 * 28).layer(200, SIGMOID)
				.layer(10, SOFTMAX).get();

		/*
		 * We need to have 1 trainer per worker thread, so we suplly a factory rather
		 * than a direct consumer. Internally the BatchNode will create 1
		 * ConcurrentTrainer per worker thread.
		 */
		randomisedData.processAll(() -> new ConcurrentTrainer(network, batchSize));

		BasicLogger.debug("Training done: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		/*
		 * We have deliberately kept all node data rather than disposing as we're done
		 * with them. Set a break point here if you want to inspect the (full) file
		 * structure on disk. With this one call we dispose of everything.
		 */
		batchManager.dispose();

		/*
		 * Now we need to test how the neural network performs. We wont use BatchNode
		 * for this - just do it the easiest most direct way.
		 */

		ArrayAnyD<Double> testLabels = IDX.parse(TEST_LABELS);
		ArrayAnyD<Double> testImages = IDX.parse(TEST_IMAGES);
		testImages.modifyAll(DIVIDE.by(255));

		BasicLogger.debug("Parsed IDX test data files: {}", STOPWATCH.stop(CalendarDateUnit.SECOND));

		NetworkInvoker invoker = network.newInvoker();

		int correct = 0;
		int wrong = 0;

		for (MatrixView<Double> imageData : testImages.matrices()) {

			int expected = testLabels.intValue(imageData.index());
			int actual = Math.toIntExact(invoker.invoke(imageData).indexOfLargest());

			if (actual == expected) {
				correct++;
			} else {
				wrong++;
			}

			if (imageData.index() < numberToPrint) {
				BasicLogger.debug("");
				BasicLogger.debug("Image {}: {} <=> {}", imageData.index(), LABELS[expected], LABELS[actual]);
				IDX.print(imageData, BasicLogger.DEBUG);
			}

			if (generateImages) {
				FashionMNISTWithBatchNode.generateImage(imageData, expected, OUTPUT_TEST_IMAGES);
			}
		}

		BasicLogger.debug("Done: {} or {}", STOPWATCH.stop(CalendarDateUnit.SECOND),
				STOPWATCH.stop(CalendarDateUnit.MINUTE));

		BasicLogger.debug("");
		BasicLogger.debug("===========================================================");
		BasicLogger.debug("Error rate: {}", (double) wrong / (double) (correct + wrong));
	}

	static void generateImage(final MatrixView<Double> imageData, final long imageLabel, final File directory)
			throws IOException {

		ToFileWriter.mkdirs(directory);

		int nbRows = imageData.getRowDim();
		int nbCols = imageData.getColDim();

		BufferedImage image = new BufferedImage(nbRows, nbCols, BufferedImage.TYPE_INT_ARGB);

		for (int i = 0; i < nbRows; i++) {
			for (int j = 0; j < nbCols; j++) {
				// The colours are stored inverted in the IDX-files (255 means "ink"
				// and 0 means "no ink". In computer graphics 255 usually means "white"
				// and 0 "black".) In addition the image data has already been rescaled
				// to be in the range [0,1]. That's why...
				int gray = (int) (255.0 * (1.0 - imageData.doubleValue(i, j)));
				int rgb = 0xFF000000 | gray << 16 | gray << 8 | gray;
				image.setRGB(i, j, rgb);
			}
		}

		String name = NumberStyle.toUniformString(imageData.index(), 60_000) + "_" + imageLabel + ".png";
		File outputfile = new File(directory, name);

		ImageIO.write(image, "png", outputfile);
	}

}