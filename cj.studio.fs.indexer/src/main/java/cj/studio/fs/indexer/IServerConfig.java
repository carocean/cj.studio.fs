package cj.studio.fs.indexer;

import java.time.Duration;
import java.util.List;

public interface IServerConfig {
    String dataDir();

    String readerServerIP();

    int readerServerPort();

    boolean readerServerSSL();

    int readerServerWorkThreadCount();

    int chunkedSize();

    int uc_maxIdleConnections();

    long uc_keepAliveDuration();

    long uc_readTimeout();

    long uc_connectTimeout();

    long uc_writeTimeout();

    List<String> ucAddresses();

    String writerReaderServer();

    String rbacStrategy();

    List<String> rbacACL();

    String writerServerIP();



    int writerServerPort();

    int writerServerChunkedSize();

    int writerServerWorkThreadCount();

    boolean writerServerSSL();

    boolean rbacForceToken();


}
