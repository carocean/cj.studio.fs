package cj.studio.fs.indexer;

import java.util.Map;

public interface IUCPorts {
    Map<String, Object> verifyToken(String appid, String token);
}
