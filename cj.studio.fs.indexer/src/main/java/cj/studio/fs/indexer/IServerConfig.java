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

    int bufferSize();

    int uc_maxIdleConnections();

    long uc_keepAliveDuration();

    long uc_readTimeout();

    long uc_connectTimeout();

    long uc_writeTimeout();

    List<String> ucAddresses();

    String rbacStrategy();

    List<String> rbacACL();
    boolean rbacForceToken();

}
