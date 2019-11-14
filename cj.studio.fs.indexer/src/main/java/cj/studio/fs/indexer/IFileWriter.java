package cj.studio.fs.indexer;

import java.io.IOException;

public interface IFileWriter {
    void seek(long pos) throws IOException;

    void write(byte[] buf) throws IOException;
    void write(byte[] buf,int pos,int length) throws IOException;
    void close() throws IOException;
}
