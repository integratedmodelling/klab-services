package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.scope.ContextScope;
import org.ojalgo.array.BufferArray;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One storage helper per context, or it could go in the associated "digital twin"
 */
public class StorageCore {

    public static final String KEY = StorageCore.class.getCanonicalName();

    public StorageCore(ContextScope scope) {
        // choose the mm files, parallelism level and the floating point representation
    }

    // FIXME this is for floats; there should be factories created on demand for each type and size
    static BufferArray.MappedFileFactory floatMappedArrayFactory = BufferArray.R032.newMapped(new File(System.getProperty("user.dir") + File.separator + "dioporco.dat"));

    static public void main(String[] args) throws InterruptedException {
        // TODO each thread should have its factory; their number should be capped at the number of processors and
        //  each tile should be capped at MAX_INTEGER size. OR maybe not - AsynchronousFileChannel?
        var made = floatMappedArrayFactory.make(1000 * 1000);
        AtomicInteger fatto = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        for (int offset = 0; offset < made.size(); offset += made.size() / 10) {
            int finalOffset = offset;
            new Thread(() -> {
                System.out.println("Zana madonna faccio da " + finalOffset + " a " + (made.size()/10 + finalOffset));
                for (long i = 0; i < made.size() / 10; i++) {
                    made.set(i + finalOffset, (float)i);
                }
                fatto.set(fatto.get() + 1);
                System.out.println("Puta madonna fatto " + fatto.get() + " da " + finalOffset + " a " + (made.size()/10 + finalOffset));
            }).start();
        }

        while(fatto.get() < 10) Thread.sleep(100);
        long end = System.currentTimeMillis();

        System.out.println("Ci ho messo " + (end - start));

        start = System.currentTimeMillis();
        for (long i = 0; i < 1000* 1000; i++) {
            made.set(i, i);
        }
        System.out.println("Invece di " + (System.currentTimeMillis() - start));
    }

}
