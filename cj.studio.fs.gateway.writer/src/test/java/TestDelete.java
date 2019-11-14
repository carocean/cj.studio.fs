import cj.studio.fs.indexer.FileSystem;

public class TestDelete {
    public static void main(String[] args) {
        FileSystem fileSystem = new FileSystem("/Users/caroceanjofers/Downloads/test/");
        fileSystem.deleteDir("/");
    }
}
