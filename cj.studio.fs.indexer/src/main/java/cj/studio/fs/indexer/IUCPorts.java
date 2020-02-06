package cj.studio.fs.indexer;

import java.util.Map;

public interface IUCPorts {
    Map<String, Object> verifyToken(String accessToken);

    Map<String, Object> auth( String accountCode, String password);
}
