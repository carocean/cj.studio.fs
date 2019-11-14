import cj.studio.fs.indexer.FileSystem;
import cj.studio.fs.indexer.IFileWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TestWriter {
    public static void main(String[] args) throws IOException {
        FileSystem fileSystem = new FileSystem("/Users/caroceanjofers/Downloads/test");
        testWriter(fileSystem, new File("/Users/caroceanjofers/studio/apache-jmeter-5.0"));
        System.out.println("\t写入完毕...");
    }
    static File rootDir;
    private static void testWriter(FileSystem fileSystem, File scanDir) throws IOException {
        if(rootDir==null){
            rootDir=scanDir;
        }
        File[] files = scanDir.listFiles();
        for (File f : files) {
            if(f.isFile()) {
                String refile = f.getAbsolutePath().substring(rootDir.getAbsolutePath().length(), f.getAbsolutePath().length());
                System.out.println("rel--"+refile);
                IFileWriter writer = fileSystem.openWriter(refile);
                byte[] buf = new byte[8192];
                FileInputStream inputStream = new FileInputStream(f);
                while (true) {
                    int read = inputStream.read(buf, 0, buf.length);
                    if (read < 0) {
                        break;
                    }
                    writer.write(buf, 0, read);
                }
                writer.close();
            }else{
                testWriter(fileSystem,f);
            }
        }
    }
}
