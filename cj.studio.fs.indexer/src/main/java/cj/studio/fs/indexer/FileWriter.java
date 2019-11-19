package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;
import org.apache.jdbm.DB;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

public class FileWriter implements IFileWriter {
    RandomAccessFile file;
    public FileWriter(IServiceProvider site, String file) throws FileNotFoundException {
        DB db = (DB) site.getService("$.db");
        String parentDir = Utils.getParentDir(file);
        Map<String, FileInfo> dir =null;
        if (!db.getCollections().containsKey(parentDir)) {
            Utils.mkdirs(db,parentDir);
            dir = db.getTreeMap(parentDir);
        }else{
            dir = db.getTreeMap(parentDir);
        }

        String fn = Utils.getFileName(file);
        if (!dir.containsKey(fn)) {
            FileInfo info=new FileInfo(FileType.file,Utils.generalFileName(parentDir.hashCode()),System.currentTimeMillis());
            dir.put(fn,info);
            db.commit();
        }
        FileInfo info = dir.get(fn);
        File dataDir = (File) site.getService("$.dataDir");
        String realFile = String.format("%s%s%s", dataDir.getAbsoluteFile(), File.separator, info.fileName);
        this.file = new RandomAccessFile(realFile, "rw");
    }
    @Override
    public void seek(long pos) throws IOException {
        file.seek(pos);
    }
    @Override
    public void write(byte[] buf) throws IOException {
        file.write(buf);
    }

    @Override
    public void write(byte[] buf, int pos, int length) throws IOException {
        file.write(buf,pos,length);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
