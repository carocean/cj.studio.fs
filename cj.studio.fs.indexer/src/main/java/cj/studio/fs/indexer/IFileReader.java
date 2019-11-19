package cj.studio.fs.indexer;

import java.io.IOException;

public interface IFileReader {
    int read(byte[] buf) throws IOException;

    void seek(long pos) throws IOException;

    int read(byte[] buf, int pos, int length) throws IOException;
    void close() throws IOException;

    long length() throws IOException;

    void readFully(byte[] array, int arrayOffset, int chunkSize) throws IOException;

}
