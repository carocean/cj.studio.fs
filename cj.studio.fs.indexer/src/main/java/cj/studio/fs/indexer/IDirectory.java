package cj.studio.fs.indexer;

import java.util.List;

public interface IDirectory {
    void mkdirs(String dir);

    List<String> listDir(String parent);
    List<String> listFile(String parent);
    boolean existsDir(String dir);
    void deleteDir(String dir);

    void deleteFile(String file);

    String parentDir(String dir);

    boolean isDirectory(String path);

    boolean isFile(String path);
}
