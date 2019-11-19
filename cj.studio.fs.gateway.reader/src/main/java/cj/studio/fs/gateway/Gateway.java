package cj.studio.fs.gateway;

import cj.studio.fs.indexer.FileSystem;
import cj.studio.fs.indexer.IServerConfig;
import cj.studio.fs.indexer.IServiceProvider;
import cj.studio.fs.indexer.ServerConfig;
import cj.studio.fs.indexer.util.Utils;
import org.apache.commons.cli.*;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.IOException;

public class Gateway implements IServiceProvider {
    IServerConfig config;
    private FileSystem fileSystem;


    public Options getOptions() {
        Options options = new Options();
        options.addOption(new Option("d", "debug", true, "调试目录"));
        options.addOption(new Option("m", "man", true, "帮助"));
        return options;
    }

    public static void main(String[] args) throws Exception {
        Gateway gateway = new Gateway();

        GnuParser parser = new GnuParser();
        Options options = gateway.getOptions();
        CommandLine line = parser.parse(options, args);
        if (line.hasOption("m")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("gateway", options);
            return;
        }
        String debug = "";
        if (line.hasOption("d")) {
            debug = line.getOptionValue("d");
        }
        String homeDir = "";
        if (Utils.isEmpty(debug)) {
            homeDir = System.getProperty("user.dir");
        } else {
            homeDir = debug;
        }
        String yaml = String.format("%s%sconf%sserver.yaml", homeDir, File.separator, File.separator);
        String log4jFile = String.format("%s%sconf%slog4j.properties", homeDir, File.separator, File.separator);
        PropertyConfigurator.configure(log4jFile);
        gateway.installServices(yaml);
        HttpStaticFileServer server = new HttpStaticFileServer();
        server.start(gateway);
    }

    public void installServices(String yaml) throws IOException {
        config = ServerConfig.load(yaml);
        fileSystem = new FileSystem(config.dataDir());
    }

    @Override
    public Object getService(String name) {
        if ("$.config".equals(name)) {
            return config;
        }
        if("$.fileSystem".equals(name)){
            return fileSystem;
        }
        return null;
    }
}
