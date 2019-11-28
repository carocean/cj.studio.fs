package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;
import org.apache.commons.cli.Options;
import org.apache.jdbm.DB;
import org.apache.jdbm.DBMaker;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class FileSystem implements IDirectory {
    IServiceProvider site;
    File indexDir;
    File dataDir;
    DB db;

    public FileSystem(String homeDir) {
        File homeFile = new File(homeDir);
        if (!homeFile.exists()) {
            homeFile.mkdirs();
        }
        if (!homeFile.isDirectory()) {
            throw new RuntimeException("索引存储位置必须是目录");
        }
        site = new DefaultSite();
        this.indexDir = new File(String.format("%s%sindex", homeDir, File.separator));
        this.dataDir = new File(String.format("%s%sdata", homeDir, File.separator));
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        if (!this.dataDir.isDirectory()) {
            throw new RuntimeException("数据存储位置必须是目录");
        }

        this.db = DBMaker.openFile(this.indexDir.getAbsolutePath())
//                .deleteFilesAfterClose()
//                .enableEncryption("password", false)
                .disableLocking()
                .disableTransactions()
                .disableCache()
                .disableCacheAutoClear()
                .useRandomAccessFile()
                .make();
        if (!db.getCollections().containsKey("/")) {
            Utils.mkdirs(db, "/");
        }
    }

    @Override
    public void mkdirs(String dir) {
        Utils.mkdirs(db, dir);
    }

    public IFileWriter openWriter(String file) throws FileNotFoundException {
        IFileWriter writer = new FileWriter(this.site, file);
        return writer;
    }

    public IFileReader openReader(String file) throws FileNotFoundException {
        IFileReader reader = new FileReader(this.site, file);
        return reader;
    }

    @Override
    public List<String> listDir(String parent) {
        if ("".equals(parent)) {
            return Arrays.asList("/");
        }
        if (!"/".equals(parent)) {
            while (parent.endsWith("/")) {
                parent = parent.substring(0, parent.length() - 1);
            }
        }
        Map<String, FileInfo> dir = db.getTreeMap(parent);
        List<String> list = new ArrayList<>();
        if (dir == null) return list;
        for (String key : dir.keySet()) {
            FileInfo info = dir.get(key);
            if (info.type == FileType.dir) {
                list.add(String.format("%s%s", parent.endsWith("/") ? parent : String.format("%s/", parent), info.fileName));
            }
        }
        return list;
    }

    @Override
    public List<String> listFile(String parent) {
        if ("".equals(parent)) {
            return new ArrayList<>();
        }
        if (!"/".equals(parent)) {
            while (parent.endsWith("/")) {
                parent = parent.substring(0, parent.length() - 1);
            }
        }
        Map<String, FileInfo> dir = db.getTreeMap(parent);
        if (dir == null) {
            return new ArrayList<>();
        }
        Set<String> set = dir.keySet();
        List<String> list = new ArrayList<>();
        for (String key : set) {
            FileInfo info = dir.get(key);
            if (info.type == FileType.file) {
                list.add(String.format("%s%s", parent.endsWith("/") ? parent : String.format("%s/", parent), key));
            }
        }
        return list;
    }

    @Override
    public boolean existsDir(String dir) {
        if ("".equals(dir)) {
            return db.getCollections().containsKey("");
        }
        if (!"/".equals(dir)) {
            while (dir.endsWith("/")) {
                dir = dir.substring(0, dir.length() - 1);
            }
        }
        return this.db.getCollections().containsKey(dir);
    }

    @Override
    public void deleteDir(String dir) {//删除所有子目录及文件，包括当前目录
        Map<String, FileInfo> map = db.getTreeMap(dir);
        List<String> files = listFile(dir);
        for (String file : files) {
            String fn = Utils.getFileName(file);
            FileInfo info = map.get(fn);
            String realFile = String.format("%s%s%s", dataDir.getAbsoluteFile(), File.separator, info.fileName);
            File f = new File(realFile);
            f.delete();
            map.remove(fn);
        }
        List<String> childs = listDir(dir);
        for (String child : childs) {
            deleteDir(child);
        }
        if (db.getCollections().containsKey(dir) && !"".equals(dir) && !"/".equals(dir)) {
            db.deleteCollection(dir);
        }
        String parent = Utils.getParentDir(dir);
        Map<String, FileInfo> parentMap = db.getTreeMap(parent);
        if (parentMap != null) {
            String folder = Utils.getFolder(dir);
            parentMap.remove(folder);
        }
        db.commit();
    }

    @Override
    public void deleteFile(String file) {
        String parent = Utils.getParentDir(file);
        if (!db.getCollections().containsKey(parent)) {
            throw new RuntimeException("父路径不存在");
        }
        Map<String, FileInfo> map = db.getTreeMap(parent);
        String fn = Utils.getFileName(file);
        FileInfo info = map.get(fn);
        String realFile = String.format("%s%s%s", dataDir.getAbsoluteFile(), File.separator, info.fileName);
        File f = new File(realFile);
        f.delete();
        map.remove(fn);
        db.commit();
    }

    @Override
    public String parentDir(String dir) {
        String parent = Utils.getParentDir(dir);
        if (!this.db.getCollections().containsKey(parent)) {
            throw new RuntimeException("父路径不存在");
        }
        return Utils.getParentDir(dir);
    }

    public void close() {
        db.close();
    }

    @Override
    public boolean isDirectory(String path) {
        int pos = path.indexOf("?");
        if (pos > -1) {
            path = path.substring(0, pos);
        }
        if ("/".equals(path) || "".equals(path)) {
            return true;
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return db.getCollections().containsKey(path);
    }

    @Override
    public boolean isFile(String path) {
        String parentDir = Utils.getParentDir(path);
        Map<String, FileInfo> dir = null;
        if (!db.getCollections().containsKey(parentDir)) {
            return false;
        }
        dir = db.getTreeMap(parentDir);

        String fn = Utils.getFileName(path);
        if (!dir.containsKey(fn)) {
            return false;
        }
        return true;
    }

    public long lastModified(String file) {
        String parent = Utils.getParentDir(file);
        if (!db.getCollections().containsKey(parent)) {
            throw new RuntimeException("父路径不存在");
        }
        Map<String, FileInfo> map = db.getTreeMap(parent);
        String fn = Utils.getFileName(file);
        FileInfo info = map.get(fn);
        return info.getLastModified();
    }

    class DefaultSite implements IServiceProvider {


        @Override
        public Object getService(String name) {
            if ("$.db".equals(name)) {
                return db;
            }
            if ("$.dataDir".equals(name)) {
                return dataDir;
            }
            return null;
        }
    }
}
