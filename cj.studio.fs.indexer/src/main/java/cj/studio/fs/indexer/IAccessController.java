package cj.studio.fs.indexer;

public interface IAccessController {

    boolean hasListRights(String uri,  String accessToken);

    boolean hasReadRights(String uri,  String accessToken,String nonce) throws AccessTokenExpiredException;

    boolean hasWriteRights(String url, String accessToken) throws AccessTokenExpiredException;

}
