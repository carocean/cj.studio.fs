package cj.studio.fs.indexer;

import java.io.File;
import java.io.Serializable;

public class FileInfo implements Serializable {
    FileType type;
    String fileName;//物理文件名，或目录名
    long createtime;
    long lastModified;
    public FileInfo() {
    }

    public FileInfo(FileType type, String fileName,long createtime) {
        this.type = type;
        this.fileName = fileName;
        this.createtime=createtime;
        this.lastModified=createtime;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getCreatetime() {
        return createtime;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return String.format("%s %s",type,fileName);
    }
}
