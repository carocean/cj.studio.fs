package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ServerConfig implements IServerConfig {
    private int readerServerChunkedSize;
    private int readerServerWorkThreadCount;
    private boolean readerServerSSL;
    private int readerServerPort;
    private String readerServerIP;
    private String dataDir;
    private int uc_maxIdleConnections;
    private long uc_keepAliveDuration;
    private long uc_readTimeout;
    private long uc_connectTimeout;
    private long uc_writeTimeout;
    private List<String> ucAddresses;
    private String rbacStrategy;
    private List<String> rbacACL;
    private boolean rbacForceToken;
    private String writerReaderServer;
    private int writerServerWorkThreadCount;
    private boolean writerServerSSL;
    private String writerServerIP;
    private int writerServerPort;
    private int writerServerChunkedSize;
    private String appid;
    private String appKey;
    private String appSecret;
    private String device;
    @Override
    public String appid() {
        return appid;
    }

    public void appid(String appid) {
        this.appid = appid;
    }
    @Override
    public String appKey() {
        return appKey;
    }

    public void appKey(String appKey) {
        this.appKey = appKey;
    }
    @Override
    public String appSecret() {
        return appSecret;
    }

    public void appSecret(String appSecret) {
        this.appSecret = appSecret;
    }
    @Override
    public String device() {
        return device;
    }

    public void device(String device) {
        this.device = device;
    }

    @Override
    public String writerReaderServer() {
        return writerReaderServer;
    }

    @Override
    public String rbacStrategy() {
        return rbacStrategy;
    }

    @Override
    public List<String> rbacACL() {
        return rbacACL;
    }

    @Override
    public int uc_maxIdleConnections() {
        return uc_maxIdleConnections;
    }

    @Override
    public long uc_keepAliveDuration() {
        return uc_keepAliveDuration;
    }

    @Override
    public long uc_readTimeout() {
        return uc_readTimeout;
    }

    @Override
    public long uc_connectTimeout() {
        return uc_connectTimeout;
    }

    @Override
    public long uc_writeTimeout() {
        return uc_writeTimeout;
    }

    @Override
    public List<String> ucAddresses() {
        return ucAddresses;
    }

    @Override
    public String dataDir() {
        return dataDir;
    }

    @Override
    public String readerServerIP() {
        return readerServerIP;
    }

    @Override
    public int readerServerPort() {
        return readerServerPort;
    }

    @Override
    public boolean readerServerSSL() {
        return readerServerSSL;
    }


    @Override
    public int readerServerWorkThreadCount() {
        return readerServerWorkThreadCount;
    }

    @Override
    public int chunkedSize() {
        return readerServerChunkedSize;
    }

    public static IServerConfig load(String yamlFile) throws IOException {
        Yaml yaml = new Yaml();
        FileReader freader = null;
        Map<String, Object> info = null;
        try {
            freader = new FileReader(yamlFile);
            info = yaml.load(freader);
        } finally {
            if (freader != null) {
                freader.close();
            }
        }
        ServerConfig config = new ServerConfig();
        String dataDir = (String) info.get("dataDir");
        Map<String, Object> reader = (Map<String, Object>) info.get("reader");
        Map<String, Object> writer = (Map<String, Object>) info.get("writer");
        parseWriterServer(config, writer);
        parseReaderServer(config, reader);
        config.dataDir(dataDir);
        Map<String, Object> ucs = (Map<String, Object>) info.get("ucs");
        config.uc_connectTimeout = Long.valueOf(ucs.get("connectTimeout") + "");
        config.uc_keepAliveDuration = Long.valueOf(ucs.get("keepAliveDuration") + "");
        config.appid=ucs.get("appid") + "";
        config.appKey=ucs.get("appKey") + "";
        config.appSecret=ucs.get("appSecret") + "";
        config.device=ucs.get("device") + "";
        config.uc_writeTimeout = Long.valueOf(ucs.get("writeTimeout") + "");
        config.uc_maxIdleConnections = (int) ucs.get("maxIdleConnections");
        config.uc_readTimeout = Long.valueOf(ucs.get("readTimeout") + "");
        config.ucAddresses = (List<String>) ucs.get("addresses");

        Map<String, Object> rbac = (Map<String, Object>) info.get("rbac");
        config.rbacStrategy = (String) rbac.get("strategy");
        config.rbacACL = (List<String>) rbac.get("acl");
        config.rbacForceToken = (boolean) rbac.get("forceToken");
        return config;
    }

    private static void parseReaderServer(ServerConfig config, Map<String, Object> reader) {
        Map<String, Object> server = (Map<String, Object>) reader.get("server");
        String listen = (String) server.get("listen");
        if (Utils.isEmpty(listen)) {
            throw new RuntimeException("侦听地址为空");
        }
        int pos = listen.indexOf(":");
        String ip = "";
        int port = 0;
        if (pos < 0) {
            throw new RuntimeException("侦听地址错误");
        }
        ip = listen.substring(0, pos);
        port = Integer.valueOf(listen.substring(pos + 1, listen.length()));
        config.readerServerIP(ip);
        config.readerServerPort(port);
        config.readerServerSSL((boolean) server.get("ssl"));
        config.readerServerWorkThreadCount((int) server.get("workThreadCount"));
        config.readerServerChunkedSize((int) server.get("chunkedSize"));
    }

    private static void parseWriterServer(ServerConfig config, Map<String, Object> writer) {
        Map<String, Object> server = (Map<String, Object>) writer.get("server");
        String listen = (String) server.get("listen");
        if (Utils.isEmpty(listen)) {
            throw new RuntimeException("侦听地址为空");
        }
        int pos = listen.indexOf(":");
        String ip = "";
        int port = 0;
        if (pos < 0) {
            throw new RuntimeException("侦听地址错误");
        }
        ip = listen.substring(0, pos);
        port = Integer.valueOf(listen.substring(pos + 1, listen.length()));
        config.writerServerIP(ip);
        config.writerServerPort(port);
        config.writerServerSSL((boolean) server.get("ssl"));
        config.writerServerWorkThreadCount((int) server.get("workThreadCount"));
        config.writerServerChunkedSize((int) server.get("chunkedSize"));
        config.writerReaderServer((String) writer.get("readerServer"));
    }

    @Override
    public String writerServerIP() {
        return writerServerIP;
    }


    @Override
    public int writerServerPort() {
        return writerServerPort;
    }

    @Override
    public int writerServerChunkedSize() {
        return writerServerChunkedSize;
    }

    @Override
    public int writerServerWorkThreadCount() {
        return writerServerWorkThreadCount;
    }

    @Override
    public boolean writerServerSSL() {
        return writerServerSSL;
    }

    private void writerReaderServer(String readerServer) {
        this.writerReaderServer = readerServer;
    }


    private void writerServerChunkedSize(int chunkedSize) {
        this.writerServerChunkedSize = chunkedSize;
    }

    private void writerServerWorkThreadCount(int workThreadCount) {
        this.writerServerWorkThreadCount = workThreadCount;
    }

    private void writerServerSSL(boolean ssl) {
        this.writerServerSSL = ssl;
    }

    private void writerServerPort(int port) {
        this.writerServerPort = port;
    }

    private void writerServerIP(String ip) {
        this.writerServerIP = ip;
    }

    @Override
    public boolean rbacForceToken() {
        return rbacForceToken;
    }


    private void dataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    private void readerServerChunkedSize(int chunkedSize) {
        this.readerServerChunkedSize = chunkedSize;
    }

    private void readerServerWorkThreadCount(int workThreadCount) {
        this.readerServerWorkThreadCount = workThreadCount;
    }

    private void readerServerSSL(boolean ssl) {
        this.readerServerSSL = ssl;
    }

    private void readerServerPort(int port) {
        this.readerServerPort = port;
    }

    private void readerServerIP(String ip) {
        this.readerServerIP = ip;
    }
}
