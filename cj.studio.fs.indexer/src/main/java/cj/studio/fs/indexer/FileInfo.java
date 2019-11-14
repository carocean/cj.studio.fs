package cj.studio.fs.indexer;

import java.io.File;
import java.io.Serializable;

public class FileInfo implements Serializable {
    FileType type;
    String fileName;//物理文件名，或目录名

    public FileInfo() {
    }

    public FileInfo(FileType type, String fileName) {
        this.type = type;
        this.fileName = fileName;
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
