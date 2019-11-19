package cj.studio.fs.indexer;

public interface IAccessController {

    boolean hasListRights(String uri, String appid, String accessToken);

    boolean hasReadRights(String uri, String appid, String accessToken);
}
