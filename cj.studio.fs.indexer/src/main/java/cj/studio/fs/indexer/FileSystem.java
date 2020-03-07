package cj.studio.fs.indexer;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileSystem implements IDirectory {
    IServiceProvider site;
    File homeDir;

    public FileSystem(String homeDir) {
        File homeFile = new File(homeDir);
        if (!homeFile.exists()) {
            homeFile.mkdirs();
        }
        if (!homeFile.isDirectory()) {
            throw new RuntimeException("索引存储位置必须是目录");
        }
        site = new DefaultSite();
        this.homeDir = homeFile;
    }

    @Override
    public void mkdirs(String dir) {
        String path = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, dir);
        File _dir = new File(path);
        if (_dir.exists()) {
            return;
        }
        _dir.mkdirs();
    }

    public IFileWriter openWriter(String file) throws IOException {
        String _file = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, file);
        IFileWriter writer = new FileWriter(this.site, _file);
        return writer;
    }

    public IFileReader openReader(String file) throws FileNotFoundException {
        String _file = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, file);
        IFileReader reader = new FileReader(this.site, _file);
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
        String parentDir = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, parent);
        File file = new File((parentDir));
        if (!file.exists()) {
            return new ArrayList<>();
        }
        File[] dirNames = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && !pathname.isHidden();
            }
        });
        if (dirNames == null) {
            return new ArrayList<>();
        }
        if (!parent.endsWith(File.separator)) {
            parent = String.format("%s%s", parent, File.separator);
        }
        List<String> _dirs = new ArrayList<>();
        for (File dir : dirNames) {
            String reldir = String.format("%s%s", parent, dir.getName());
            _dirs.add(reldir);
        }
        return _dirs;
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
        String parentDir = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, parent);
        File file = new File((parentDir));
        if (!file.exists()) {
            return new ArrayList<>();
        }
        File[] fileNames = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && !pathname.isHidden();
            }
        });
        if (fileNames == null) {
            return new ArrayList<>();
        }
        if (!parent.endsWith(File.separator)) {
            parent = String.format("%s%s", parent, File.separator);
        }
        List<String> _files = new ArrayList<>();
        for (File dir : fileNames) {
            String reldir = String.format("%s%s", parent, dir.getName());
            _files.add(reldir);
        }
        return _files;
    }

    @Override
    public boolean existsDir(String dir) {
        String parentDir = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, dir);
        File file = new File((parentDir));
        return file.exists();
    }

    @Override
    public void deleteDir(String dir) {//删除所有子目录及文件，包括当前目录
        List<String> files = listFile(dir);
        for (String file : files) {
            String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, file);
            File f = new File(realFile);
            if (f.exists())
                f.delete();
        }
        List<String> childs = listDir(dir);
        for (String child : childs) {
            deleteDir(child);
        }

    }

    @Override
    public void deleteFile(String file) {
        String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, file);
        File f = new File(realFile);
        if (f.exists()) {
            f.delete();
        }
    }

    @Override
    public String parentDir(String dir) {
        String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, dir);
        File f = new File(realFile);
        if (f.equals(homeDir)) {
            return "/";
        }
        return f.getParent();
    }

    public void close() {
        this.homeDir = null;
    }

    @Override
    public boolean isDirectory(String path) {
        String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, path);
        File f = new File(realFile);
        return f.isDirectory();
    }

    @Override
    public boolean isFile(String path) {
        String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, path);
        File f = new File(realFile);
        return f.isFile();
    }

    public long lastModified(String file) {
        String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, file);
        File f = new File(realFile);
        return f.lastModified();
    }

    public File getRealFile(String path) {
        String realFile = String.format("%s%s%s", homeDir.getAbsolutePath(), File.separator, path);
        File f = new File(realFile);
        return  f;
    }

    class DefaultSite implements IServiceProvider {


        @Override
        public Object getService(String name) {
            return null;
        }
    }
}
