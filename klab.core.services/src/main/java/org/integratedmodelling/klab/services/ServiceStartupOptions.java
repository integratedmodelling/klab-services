package org.integratedmodelling.klab.services;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;

public class ServiceStartupOptions implements StartupOptions {

    @Option(name = "-dataDir", usage = "data directory (default: ~/.klab)", metaVar = "<DIRECTORY_PATH>")
    File dataDir = null;

    @Option(
            name = "-cert",
            usage = "certificate file (default: <dataDir>/" + KlabCertificate.DEFAULT_NODE_CERTIFICATE_FILENAME + ")",
            metaVar = "<FILE_PATH>")
    File certificateFile = null;

    @Option(
            name = "-certResource",
            usage = "certificate classpath resource (default null)",
            metaVar = "<CLASSPATH_RESOURCE>")
    String certificateResource = null;

    @Option(
            name = "-name",
            usage = "node name (overrides name in certificate)",
            metaVar = "<SIMPLE_STRING>")
    String nodeName = null;

    //    @Option(
    //            name = "-config",
    //            usage = "subdirectory of standard dataDir containing configuration files",
    //            metaVar = "<SIMPLE_STRING>")
    //    String configurationPath = null;

    @Option(
            name = "-url",
            usage = "the host URL without port or context path. Communicated to clients so it must be right" +
                    ". Defaults to localhost.",
            metaVar = "<SIMPLE_STRING>")
    String serviceHostUrl = "http://127.0.0.1";

    @Option(
            name = "-secret",
            usage = "the secret key to override the one saved on startup",
            metaVar = "<SIMPLE_STRING>")
    String serviceSecret = null;

    @Option(
            name = "-hub",
            usage = "URL of authenticating hub (default set in certificate)",
            metaVar = "<URL>")

    String authenticatingHub = null;

    @Option(name = "-port", usage = "http port for REST communication", metaVar = "<INT>")
    int port = -1;

    @Option(name = "-help", usage = "print command line options and exit")
    boolean help;

    @Option(name = "-clean", usage = "clean configuration on startup. CAUTION: destructive")
    boolean clean;

    @Option(name = "-cloudConfig", usage = "allow for external configuration of service")
    boolean cloudConfig;

    @Option(name = "-components", usage = "paths to any custom component")
    List<File> components = new ArrayList<>();

    KlabService.Type serviceType;


    private List<String> arguments = new ArrayList<>();

    @Option(
            name = "-contextPath",
            usage = "context path for application (default is service dependent)",
            metaVar = "<SIMPLE_STRING>")
    private String contextPath;

    /**
     * All defaults
     */
    private ServiceStartupOptions() {
    }

    //    public static ServiceStartupOptions create(String[] args) {
    //        var ret = new ServiceStartupOptions();
    //        ret.initialize(args);
    //        return ret;
    //    }

    public static ServiceStartupOptions create(KlabService.Type serviceType, String[] args) {
        var ret = defaultOptions(serviceType);
        ret.initialize(args);
        return ret;
    }

    /**
     * Produce startup options with all the defaults for the passed service type.
     *
     * @param serviceType
     * @return
     */
    public static ServiceStartupOptions defaultOptions(KlabService.Type serviceType) {
        var ret = new ServiceStartupOptions();
        ret.serviceType = serviceType;
        ret.port = serviceType.defaultPort;
        ret.contextPath = "/" + serviceType.defaultServicePath;
        //        ret.configurationPath = "services/" + serviceType.defaultServicePath;
        ret.nodeName = serviceType.defaultServicePath;
        return ret;
    }

    /**
     * Produce startup options with the defaults for a test environment.
     *
     * @param serviceType
     * @return
     */
    public static ServiceStartupOptions testOptions(KlabService.Type serviceType) {
        var ret = new ServiceStartupOptions();
        ret.serviceType = serviceType;
        ret.port = serviceType.defaultPort;
        ret.contextPath = "/" + serviceType.defaultServicePath;
        //        ret.configurationPath = "services/test/" + serviceType.defaultServicePath;
        ret.nodeName = "service." + serviceType.defaultServicePath + ".test";
        return ret;
    }

    public ServiceStartupOptions(String... args) {
        initialize(args);
    }

    @Override
    public String getServiceName() {
        return nodeName;
    }

    @Override
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

    @Override
    public File getCertificateFile() {
        if (certificateFile == null) {
            certificateFile =
                    new File(ServiceConfiguration.INSTANCE.getDataPath() + File.separator + KlabCertificate.DEFAULT_NODE_CERTIFICATE_FILENAME);
        }
        return certificateFile;
    }

    public static void main(String args[]) {
        System.out.println(new ServiceStartupOptions().usage());
    }

    @Override
    public File getDataDirectory() {
        if (dataDir == null) {
            dataDir = ServiceConfiguration.INSTANCE.getDataPath();
        }
        return dataDir;
    }

    @Override
    public int getPort() {
        // TODO use default from service type
        return port;
    }

    //    public String getConfigurationPath() {
    //        return configurationPath;
    //    }

    @Override
    public boolean isHelp() {
        return help;
    }

    @Override
    public Collection<File> getComponentPaths() {
        return components;
    }

    @Override
    public String getCertificateResource() {
        return certificateResource;
    }


    public void setDataDir(File dataDir) {
        this.dataDir = dataDir;
    }


    public void setCertificateFile(File certificateFile) {
        this.certificateFile = certificateFile;
    }


    public void setCertificateResource(String certificateResource) {
        this.certificateResource = certificateResource;
    }

    public void setPort(int port) {
        this.port = port;
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

    @Override
    public String getAuthenticatingHub() {
        return authenticatingHub;
    }

    public boolean isCloudConfig() {
        return cloudConfig;
    }

    public boolean isClean() {
        return clean;
    }

    public String getServiceSecret() {
        return serviceSecret;
    }

    public File getDataDir() {
        return dataDir;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    //    public void setConfigurationPath(String configurationPath) {
    //        this.configurationPath = configurationPath;
    //    }

    public void setServiceSecret(String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    public void setAuthenticatingHub(String authenticatingHub) {
        this.authenticatingHub = authenticatingHub;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public void setCloudConfig(boolean cloudConfig) {
        this.cloudConfig = cloudConfig;
    }

    public List<File> getComponents() {
        return components;
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

        File configurationDirectory = dataDir == null ? ServiceConfiguration.INSTANCE.getDataPath() : dataDir;
        if (!configurationDirectory.exists()) {
            configurationDirectory.mkdirs();
        }

        configurationDirectory = new File(configurationDirectory + File.separator + path);
        if (!configurationDirectory.exists()) {
            configurationDirectory.mkdirs();
        }

        return configurationDirectory;
    }

    public String getContextPath() {
        return contextPath;
    }


    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }


    public KlabService.Type getServiceType() {
        return serviceType;
    }

    public void setServiceType(KlabService.Type serviceType) {
        this.serviceType = serviceType;
    }


    public String getServiceHostUrl() {
        return serviceHostUrl;
    }

    public void setServiceHostUrl(String serviceHostUrl) {
        this.serviceHostUrl = serviceHostUrl;
    }

}
