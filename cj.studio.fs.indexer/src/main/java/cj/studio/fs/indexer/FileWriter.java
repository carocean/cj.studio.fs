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

    public FileWriter(IServiceProvider site, String file) throws IOException {
        File f = new File(file);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                throw e;
            }
        }
        this.file = new RandomAccessFile(f, "rw");
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
        file.write(buf, pos, length);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
