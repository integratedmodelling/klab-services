package org.integratedmodelling.engine.client.distribution;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.integratedmodelling.klab.api.engine.Product;
import org.integratedmodelling.klab.api.engine.Release;
import org.integratedmodelling.klab.api.engine.RunningInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class RunningInstanceImpl implements RunningInstance {

	protected Product product;
	protected AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);
	protected DefaultExecutor executor;
	protected Consumer<Status> statusHandler;
//	protected Settings settings;
	protected Release release;

	public RunningInstanceImpl(Release release, ProductImpl product/*, Settings settings*/) {
		this.product = product;
//		this.settings = settings;
		this.release = release;
	}

	@Override
	public Product getProduct() {
		return product;
	}

	@Override
	public Release getRelease() {
	    return release;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	@Override
	public Status getStatus() {
		return status.get();
	}

/*
	@Override
	public Settings getSettings() {
	    return this.settings;
	}
*/

	protected abstract CommandLine getCommandLine();

	protected abstract boolean isRunning();

	@Override
	public boolean start() {

		CommandLine cmdLine = getCommandLine();

		/*
		 * assume error was reported
		 */
		if (cmdLine == null) {
			return false;
		}

		this.executor = new DefaultExecutor();
		this.executor.setWorkingDirectory(product.getLocalWorkspace());

		Map<String, String> env = new HashMap<>();
		env.putAll(System.getenv());

		status.set(Status.WAITING);
		if (this.statusHandler != null) {
			this.statusHandler.accept(status.get());
		}

		try {
			this.executor.execute(cmdLine, env, new ExecuteResultHandler() {

				@Override
				public void onProcessFailed(ExecuteException ee) {
					ee.printStackTrace();
//					logger.error(ee.getMessage());
					status.set(Status.ERROR);
					if (statusHandler != null) {
						statusHandler.accept(status.get());
					}
				}
				@Override
				public void onProcessComplete(int arg0) {
					status.set(Status.STOPPED);
					if (statusHandler != null) {
						statusHandler.accept(status.get());
					}
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
//			logger.error(e.getMessage());
			status.set(Status.ERROR);
			if (statusHandler != null) {
                statusHandler.accept(status.get());
            }
		}

		return true;
	}

	@Override
	public boolean stop() {
		// does nothing - override
		return false;
	}

}
