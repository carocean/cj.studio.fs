package cj.studio.fs.gateway.writer;

import cj.studio.fs.gateway.writer.pages.*;
import cj.studio.fs.indexer.*;
import cj.studio.fs.indexer.IPage;
import cj.studio.fs.indexer.util.Utils;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.cli.*;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Gateway implements IServiceProvider{
    IServerConfig config;
    private FileSystem fileSystem;
    IAccessController controller;
    IUCPorts iucPorts;
    OkHttpClient okhttp;
    Map<String, IPage> pages;
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
        HttpUploadServer server = new HttpUploadServer();
        server.start(gateway);
    }
    public void installServices(String yaml) throws IOException {
        config = ServerConfig.load(yaml);
        fileSystem = new FileSystem(config.dataDir());
        ConnectionPool pool = new ConnectionPool(
                config.uc_maxIdleConnections(),
                config.uc_keepAliveDuration(),
                TimeUnit.MILLISECONDS);
        okhttp = new OkHttpClient.Builder().
                connectionPool(pool).
                readTimeout(config.uc_readTimeout(),TimeUnit.MILLISECONDS).
                connectTimeout(config.uc_connectTimeout(),TimeUnit.MILLISECONDS).
                writeTimeout(config.uc_writeTimeout(),TimeUnit.MILLISECONDS).
                build();
        iucPorts = new DefaultUCPorts(this);
        controller = new DefaultReadAccessController(this);
        pages=new HashMap<>();
        IPage list=new ListFilePage(config);
        pages.put(list.path(),list);
        IPage login=new LoginPage();
        pages.put(login.path(), login);
        IPage index=new IndexPage();
        pages.put(index.path(), index);
        IPage upload=new UploadPage();
        pages.put(upload.path(), upload);
        IPage create=new CreateDirPage();
        pages.put(create.path(), create);
        IPage delete=new DeletePage();
        pages.put(delete.path(), delete);
    }

    @Override
    public Object getService(String name) {
        if ("$.config".equals(name)) {
            return config;
        }
        if ("$.fileSystem".equals(name)) {
            return fileSystem;
        }
        if ("$.accessController".equals(name)) {
            return controller;
        }
        if ("$.uc.ports".equals(name)) {
            return iucPorts;
        }
        if ("$.okhttp".equals(name)) {
            return okhttp;
        }
        if ("$.pages".equals(name)) {
            return pages;
        }
        return null;
    }
}
