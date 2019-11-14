import cj.studio.fs.indexer.FileInfo;
import cj.studio.fs.indexer.FileSystem;
import cj.studio.fs.indexer.IFileReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TestReader {
    public static void main(String[] args) throws IOException {
        FileSystem fileSystem = new FileSystem("/Users/caroceanjofers/Downloads/test/");
        testRead(fileSystem,"/");
    }

    private static void testRead(FileSystem fileSystem,String dir) throws IOException {
        String copyToDir="/Users/caroceanjofers/Downloads/test2/";
        List<String> files =fileSystem.listFile(dir);
        for (String file : files) {
            System.out.println("\t" + file);
            IFileReader reader = fileSystem.openReader(file);
            File cf = new File(copyToDir+file);
            if (!cf.getParentFile().exists()) {
                cf.getParentFile().mkdirs();
            }
            FileOutputStream out=new FileOutputStream(cf);
            byte[] buf = new byte[8192];
            int readlen=0;
            while (true) {
                readlen = reader.read(buf, 0, buf.length);
                if(readlen<0){
                    break;
                }
                out.write(buf, 0, readlen);
            }
            reader.close();
        }
        List<String> dirs = fileSystem.listDir(dir);
        for (String child : dirs) {
            testRead(fileSystem,child);
        }
    }

}
