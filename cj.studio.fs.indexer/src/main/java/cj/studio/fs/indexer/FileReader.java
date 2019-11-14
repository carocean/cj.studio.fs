package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;
import org.apache.jdbm.DB;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

class FileReader implements IFileReader {
    RandomAccessFile file;

    public FileReader(IServiceProvider site, String file) throws FileNotFoundException {
        DB db = (DB) site.getService("$.db");
        String parentDir = Utils.getParentDir(file);
        Map<String, FileInfo> dir = null;
        if (!db.getCollections().containsKey(parentDir)) {
            throw new RuntimeException("文件不存在：" + file);
        }
        dir = db.getTreeMap(parentDir);

        String fn = Utils.getFileName(file);
        if (!dir.containsKey(fn)) {
            throw new RuntimeException("文件不存在：" + file);
        }
        FileInfo fileInfo = dir.get(fn);
        File dataDir = (File) site.getService("$.dataDir");
        String realFile = String.format("%s%s%s", dataDir.getAbsoluteFile(), File.separator, fileInfo.fileName);
        this.file = new RandomAccessFile(realFile, "r");
    }

    @Override
    public long length() throws IOException {
        return file.length();
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return file.read(buf);
    }
    @Override
    public void seek(long pos) throws IOException {
        file.seek(pos);
    }
    @Override
    public int read(byte[] buf, int pos, int length) throws IOException {
        return file.read(buf, pos, length);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
