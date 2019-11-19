package cj.studio.fs.indexer;

public interface IServerConfig {
    String dataDir();

    String readerServerIP();

    int readerServerPort();

    boolean readerServerSSL();

    int readerServerWorkThreadCount();

    int chunkedSize();

    int bufferSize();
}
