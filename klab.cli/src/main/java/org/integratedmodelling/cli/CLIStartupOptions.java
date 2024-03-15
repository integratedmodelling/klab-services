package org.integratedmodelling.cli;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.services.KlabService;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CLIStartupOptions {


    @Option(name = "-dataDir", usage = "data directory (default: ~/.klab)", metaVar = "<DIRECTORY_PATH>")
    File dataDir = null;

    @Option(
            name = "-cert",
            usage = "certificate file (default: <dataDir>/" + KlabCertificate.DEFAULT_NODE_CERTIFICATE_FILENAME + ")",
            metaVar = "<FILE_PATH>")
    File certificateFile = null;

    @Option(
            name = "-hub",
            usage = "URL of authenticating hub (default set in certificate)",
            metaVar = "<URL>")

    String authenticatingHub = null;

    @Option(name = "-develop", usage = "force development configuration (using local source distribution)")
    boolean develop;

    @Option(name = "-help", usage = "print command line options and exit")
    boolean help;

    @Option(name = "-clean", usage = "clean configuration on startup. CAUTION: destructive")
    boolean clean;

    @Option(name = "-components", usage = "paths to any custom component")
    List<File> components = new ArrayList<>();

    private List<String> arguments = new ArrayList<>();

    /**
     * All defaults
     */
    private CLIStartupOptions() {
    }

    //    public static ServiceStartupOptions create(String[] args) {
    //        var ret = new ServiceStartupOptions();
    //        ret.initialize(args);
    //        return ret;
    //    }

    public static CLIStartupOptions create(String[] args) {
        var ret = defaultOptions();
        ret.initialize(args);
        return ret;
    }

    /**
     * Produce startup options with all the defaults for the passed service type.
     *
     * @return
     */
    public static CLIStartupOptions defaultOptions() {
        var ret = new CLIStartupOptions();
        return ret;
    }

    /**
     * Produce startup options with the defaults for a test environment.
     *
     * @return
     */
    public static CLIStartupOptions testOptions() {
        var ret = new CLIStartupOptions();
        return ret;
    }

    public CLIStartupOptions(String... args) {
        initialize(args);
    }

    public String[] getArguments(String... additionalArguments) {
        List<String> args = new ArrayList<>(this.arguments);
        if (additionalArguments != null) {
            for (String additionalArgument : additionalArguments) {
                args.add(additionalArgument);
            }
        }
        return args.toArray(new String[args.size()]);
    }

    /**
     * Read the passed arguments and initialize all fields from them.
     *
     * @param arguments
     * @return true if arguments were OK, false otherwise.
     */
    public boolean initialize(String[] arguments) {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(arguments);
        } catch (CmdLineException e) {
            return false;
        }
        return true;
    }

    public String usage() {
        ParserProperties properties = ParserProperties.defaults().withUsageWidth(110);
        CmdLineParser parser = new CmdLineParser(this, properties);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        parser.printUsage(baos);
        return "Usage:\n\n" + baos;
    }

    public File getCertificateFile() {
        if (certificateFile == null) {
            certificateFile =
                    new File(Configuration.INSTANCE.getDataPath() + File.separator + KlabCertificate.DEFAULT_NODE_CERTIFICATE_FILENAME);
        }
        return certificateFile;
    }

    public static void main(String args[]) {
        System.out.println(new CLIStartupOptions().usage());
    }

    public File getDataDirectory() {
        if (dataDir == null) {
            dataDir = Configuration.INSTANCE.getDataPath();
        }
        return dataDir;
    }

    public boolean isHelp() {
        return help;
    }

    public Collection<File> getComponentPaths() {
        return components;
    }

    public void setDataDir(File dataDir) {
        this.dataDir = dataDir;
    }

    public void setCertificateFile(File certificateFile) {
        this.certificateFile = certificateFile;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public void setComponents(List<File> components) {
        this.components = components;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public String getAuthenticatingHub() {
        return authenticatingHub;
    }
    public boolean isClean() {
        return clean;
    }

    public File getDataDir() {
        return dataDir;
    }

    public void setAuthenticatingHub(String authenticatingHub) {
        this.authenticatingHub = authenticatingHub;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public List<File> getComponents() {
        return components;
    }

    public boolean isDevelop() {
        return develop;
    }

    public void setDevelop(boolean develop) {
        this.develop = develop;
    }

    public List<String> getArguments() {
        return arguments;
    }
    /*
     * Utils
     */

    /**
     * Create a directory name for the passed path relative to the configured datapath, resolved from the
     * overall configuration unless redefined here. If the path does not exist, create it before returning
     * it.
     *
     * @param path
     * @return
     */
    public File fileFromPath(String path) {

        File configurationDirectory = dataDir == null ? Configuration.INSTANCE.getDataPath() : dataDir;
        if (!configurationDirectory.exists()) {
            configurationDirectory.mkdirs();
        }

        configurationDirectory = new File(configurationDirectory + File.separator + path);
        if (!configurationDirectory.exists()) {
            configurationDirectory.mkdirs();
        }

        return configurationDirectory;
    }
}
