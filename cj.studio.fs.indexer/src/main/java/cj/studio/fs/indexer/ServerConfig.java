package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class ServerConfig implements IServerConfig {
    private int chunkedSize;
    private int workThreadCount;
    private boolean ssl;
    private int port;
    private String ip;
    private String dataDir;
    private int bufferSize;


    @Override
    public String dataDir() {
        return dataDir;
    }

    @Override
    public int bufferSize() {
        return bufferSize;
    }

    @Override
    public String readerServerIP() {
        return ip;
    }

    @Override
    public int readerServerPort() {
        return port;
    }

    @Override
    public boolean readerServerSSL() {
        return ssl;
    }

    @Override
    public int readerServerWorkThreadCount() {
        return workThreadCount;
    }

    @Override
    public int chunkedSize() {
        return chunkedSize;
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
        config.readerServerBufferSize((int) reader.get("bufferSize"));
        config.dataDir(dataDir);
        return config;
    }

    private void readerServerBufferSize(int bufferSize) {
        this.bufferSize=bufferSize;
    }

    private void dataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    private void readerServerChunkedSize(int chunkedSize) {
        this.chunkedSize = chunkedSize;
    }

    private void readerServerWorkThreadCount(int workThreadCount) {
        this.workThreadCount = workThreadCount;
    }

    private void readerServerSSL(boolean ssl) {
        this.ssl = ssl;
    }

    private void readerServerPort(int port) {
        this.port = port;
    }

    private void readerServerIP(String ip) {
        this.ip = ip;
    }
}
