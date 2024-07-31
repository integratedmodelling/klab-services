package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.digitaltwin.StateStorage;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.ojalgo.array.BufferArray;
import org.ojalgo.concurrent.Parallelism;

import java.io.File;

/**
 * There is one separate <code>StorageScope</code> in each {@link ContextScope}. It's built on demand based on the
 * configuration available from the context data, including whatever user-level configuration was passed, and stored in
 * the context data at the runtime side. The StorageScope is managed by the StorageManager, which is a singleton used by
 * the DigitalTwin.
 */
public class StateStorageImpl implements StateStorage {

    private File workspace;
    private File floatBackupFile;
    private File doubleBackupFile;
    private File intBackupFile;
    private File booleanBackupFile;
    private int histogramBinSize = 20;

    public boolean isRecordHistogram() {
        return recordHistogram;
    }

    private boolean recordHistogram = true;

    private Parallelism parallelism = Parallelism.ONE;

    public StateStorageImpl(ContextScope scope) {
        // choose the mm files, parallelism level and the floating point representation
        this.workspace = ServiceConfiguration.INSTANCE.getScratchDataDirectory("ktmp");
        this.floatBackupFile = new File(this.workspace + File.separator + "fstorage.bin");
        this.doubleBackupFile = new File(this.workspace + File.separator + "dstorage.bin");
        this.intBackupFile = new File(this.workspace + File.separator + "istorage.bin");
        this.booleanBackupFile = new File(this.workspace + File.separator + "bstorage.bin");
    }

    // FIXME this is for floats; there should be factories created on demand for each type and size. Put this in the
    //  constructor
    BufferArray.MappedFileFactory floatMappedArrayFactory = null;
    BufferArray.MappedFileFactory doubleMappedArrayFactory = null;
    BufferArray.MappedFileFactory intMappedArrayFactory = null;
    BufferArray.MappedFileFactory booleanMappedArrayFactory = null;

    static public void main(String[] args) throws InterruptedException {

//        for (int n = 1; n < 10; n++) {
//
//            var made = getFloatFactory().make(1000 * 1000);
//            AtomicInteger fatto = new AtomicInteger(0);
//            long start = System.currentTimeMillis();
//            for (int offset = 0; offset < made.size(); offset += made.size() / 10) {
//                int finalOffset = offset;
//                new Thread(() -> {
//                    System.out.println("Zana madonna faccio da " + finalOffset + " a " + (made.size() / 10 +
//                    finalOffset));
//                    for (long i = 0; i < made.size() / 10; i++) {
//                        made.set(i + finalOffset, (float) i);
//                    }
//                    fatto.set(fatto.get() + 1);
//                    System.out.println("Puta madonna fatto " + fatto.get() + " da " + finalOffset + " a " + (made
//                    .size() / 10 + finalOffset));
//                }).start();
//            }
//
//            while (fatto.get() < 10) Thread.sleep(100);
//            long end = System.currentTimeMillis();
//
//            System.out.println("Ci ho messo " + (end - start));
//
//            start = System.currentTimeMillis();
//            for (long i = 0; i < 1000 * 1000; i++) {
//                made.set(i, i);
//            }
//            System.out.println("Invece di " + (System.currentTimeMillis() - start));
//        }
    }

    private BufferArray.MappedFileFactory getFloatFactory() {
        if (this.floatMappedArrayFactory == null) {
            this.floatMappedArrayFactory = BufferArray.R032.newMapped(this.floatBackupFile);
        }
        return this.floatMappedArrayFactory;
    }

    private BufferArray.MappedFileFactory getDoubleFactory() {
        if (this.doubleMappedArrayFactory == null) {
            this.doubleMappedArrayFactory = BufferArray.R064.newMapped(this.doubleBackupFile);
        }
        return this.doubleMappedArrayFactory;
    }

    /*
    SHORT int. For now we use floats to encode longs
     */
    private BufferArray.MappedFileFactory getIntFactory() {
        if (this.intMappedArrayFactory == null) {
            this.intMappedArrayFactory = BufferArray.Z016.newMapped(this.intBackupFile);
        }
        return this.intMappedArrayFactory;
    }

    /**
     * Bytes. Not sure we should have the overhead of packing bytes but maybe we should as bitmaps make wonderful cheap
     * masks and they may intersect more cheaply than 2D geometries.
     *
     * @return
     */
    private BufferArray.MappedFileFactory getBooleanFactory() {
        if (this.booleanMappedArrayFactory == null) {
            this.booleanMappedArrayFactory = BufferArray.Z008.newMapped(this.booleanBackupFile);
        }
        return this.booleanMappedArrayFactory;
    }

    public void close() {
        // TODO this is crucial for everything, ensure it's complete
        if (doubleMappedArrayFactory != null) {
            doubleMappedArrayFactory = null;
            Utils.Files.deleteQuietly(doubleBackupFile);
        }
        if (floatMappedArrayFactory != null) {
            floatMappedArrayFactory = null;
            Utils.Files.deleteQuietly(floatBackupFile);
        }
        if (intMappedArrayFactory != null) {
            intMappedArrayFactory = null;
            Utils.Files.deleteQuietly(intBackupFile);
        }
        if (booleanMappedArrayFactory != null) {
            booleanMappedArrayFactory = null;
            Utils.Files.deleteQuietly(booleanBackupFile);
        }
    }

    public BufferArray getIntBuffer(long sliceSize) {
        return getIntFactory().make(sliceSize);
    }
    public BufferArray getFloatBuffer(long sliceSize) {
        return getFloatFactory().make(sliceSize);
    }
    public BufferArray getBooleanBuffer(long sliceSize) {
        return getBooleanFactory().make(sliceSize);
    }
    public BufferArray getDoubleBuffer(long sliceSize) {
        return getDoubleFactory().make(sliceSize);
    }

    public int getHistogramBinSize() {
        return histogramBinSize;
    }
}
