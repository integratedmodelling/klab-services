package org.integratedmodelling.engine.client.distribution;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.engine.distribution.Settings;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class RunningInstanceImpl implements RunningInstance {

    protected AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);
    protected DefaultExecutor executor;
    protected Consumer<Status> statusHandler;
    protected StartupOptions startupOptions;
    protected Build build;
    private int njars;
    private int ndirs;
    private Scope scope;

    public RunningInstanceImpl(Build release, Scope scope, StartupOptions startupOptions) {
        this.startupOptions = startupOptions;
        this.build = release;
        this.scope = scope;
    }

    @Override
    public Build getBuild() {
        return build;
    }

    @Override
    public Status getStatus() {
        return status.get();
    }

    @Override
    public StartupOptions getSettings() {
        return startupOptions;
    }

    private String[] getJavaOptions(int minMemM, int maxMemM, boolean isServer) {

        ArrayList<String> ret = new ArrayList<>();

        ret.add("-Xms" + minMemM + "M");
        ret.add("-Xmx" + maxMemM + "M");
        if (isServer) {
            ret.add("-server");
        }
        return ret.toArray(new String[ret.size()]);
    }

    private int debugPort() {
        // TODO link to product
        return 8000;
    }

    protected CommandLine getCommandLine(Scope scope) {
        //            /*
        //            create JavaOptions with StartupOptions, use it for createCommandLine in a new
        //            RunningInstanceImpl
        //             */
        Settings settings = new Settings(build.getRelease());
        CommandLine ret = new CommandLine(JreModel.INSTANCE.getJavaExecutable());
        ret.addArguments(getJavaOptions(512, settings.getMaxEngineMemory().getValue(), isServer()));
        //
        if (settings.isUseDebugParameters().getValue()) {
            ret.addArgument("-Xdebug");
            ret.addArgument("-Xbootclasspath/p:lib/jsr166.jar");
            ret.addArgument("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" +
                    debugPort());
        }
        ret.addArgument("-Dfile.encoding=UTF-8");
        if (isServer()) {
            ret.addArgument("-Dserver-port=" + instancePort());
        }
        //
        String classpath = getClassPath();
        String mainclass = build.getExecutable();

        if (mainclass == null && njars == 1 && ndirs == 0) {
            ret.addArguments(new String[]{"-jar", classpath});
        } else if (mainclass != null) {
            ret.addArguments(new String[]{"-cp", getClassPath()});
            ret.addArgument(mainclass);
        } else {
            scope.error(
                    "Remote distribution error: main class is not defined for " + build.getProduct().getName() + " product", false);
            ret = null;
        }

        if (startupOptions != null && ret != null) {
            // add the remaining startup options to the command line
            for (var arg : startupOptions.getArguments()) {
                ret.addArgument(arg);
            }
        }

        return ret;
    }

    private String getClassPath() {

        String ret = "";
        this.njars = 0;
        this.ndirs = 0;
        for (File file : build.getLocalWorkspace().listFiles()) {
            if (file.toString().endsWith(".jar")) {
                this.njars++;
                ret += (ret.isEmpty() ? "" : Utils.OS.get().getClasspathSeparator()) + file.getName();
            } else if (file.isDirectory()) {
                this.ndirs++;
                ret += (ret.isEmpty() ? "" : Utils.OS.get().getClasspathSeparator()) + file.getName() + File.separator + "*";
            }
        }
        return ret;
    }

    private int instancePort() {
        return switch (build.getProduct().getProductType()) {
            case CLI, MODELER -> 0;
            case RESOURCES_SERVICE -> KlabService.Type.RESOURCES.defaultPort;
            case REASONER_SERVICE -> KlabService.Type.REASONER.defaultPort;
            case RESOLVER_SERVICE -> KlabService.Type.RESOLVER.defaultPort;
            case COMMUNITY_SERVICE -> KlabService.Type.COMMUNITY.defaultPort;
            case RUNTIME_SERVICE -> KlabService.Type.RUNTIME.defaultPort;
        };
    }

    private boolean isServer() {
        return switch (build.getProduct().getProductType()) {
            case CLI, MODELER -> false;
            default -> true;
        };
    }

    @Override
    public boolean start() {

        CommandLine cmdLine = getCommandLine(scope);

        /*
         * assume error was reported
         */
        if (cmdLine == null) {
            return false;
        }

        this.executor = new DefaultExecutor();
        this.executor.setWorkingDirectory(build.getLocalWorkspace());

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
        // does nothing - override based on product type
        return false;
    }

    @Override
    public void pollStatus(Consumer<Status> listener) {
        System.out.println("PUTO CAN POLL STATUS");
    }

}
