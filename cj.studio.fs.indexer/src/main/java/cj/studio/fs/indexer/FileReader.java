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
        File f = new File(file);
        if (!f.exists()) {
            throw new FileNotFoundException("文件不存在：" + file);
        }
        this.file = new RandomAccessFile(f, "r");
    }

    @Override
    public long length() throws IOException {
        return file.length();
    }

    @Override
    public void readFully(byte[] array, int pos, int chunkSize) throws IOException {
        file.readFully(array, pos, chunkSize);
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
