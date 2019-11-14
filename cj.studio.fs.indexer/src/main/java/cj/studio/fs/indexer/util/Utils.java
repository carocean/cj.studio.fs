package cj.studio.fs.indexer.util;

import cj.studio.fs.indexer.FileInfo;
import cj.studio.fs.indexer.FileType;
import org.apache.jdbm.DB;

import java.util.Map;
import java.util.UUID;

public class Utils {
    public static String getParentDir(String file) {
        if(Utils.isEmpty(file)||"/".equals(file)){
            return "";
        }
        String parent = "";
        int pos = file.lastIndexOf("/");
        if (pos < 0) {
            throw new RuntimeException("路径错误");
        }
        parent = file.substring(0, pos);
        if (isEmpty(parent)) {
            parent = "/";
        }
        return parent;
    }

    public static boolean isEmpty(String str) {
        return str == null || "".equals(str);
    }

    public static String getFileName(String file) {
        int pos = file.lastIndexOf("/");
        if (pos < 0) {
            throw new RuntimeException("路径错误");
        }
        return file.substring(pos + 1, file.length());
    }

    public static String generalFileName(long prefix) {
        return String.format("%s#%s", prefix, UUID.randomUUID().toString());
    }

    public static void mkdirs(DB db, String dir) {
        String remaining = dir;
        String folder = "";
        String parentDir="/";
        do {
            while ((remaining.startsWith("/"))) {
                remaining = dir.substring(1, remaining.length());
            }
            int pos = remaining.indexOf("/");
            if (pos < 0) {
                folder = remaining;
                remaining="";
            } else {
                folder = remaining.substring(0, pos);
                remaining = remaining.substring(pos + 1, remaining.length());
            }
            parentDir=mkdir(db,parentDir,folder);
        } while (!Utils.isEmpty(remaining));
    }

    private synchronized static String mkdir(DB db, String parentDir,String folder) {
        Map<String, FileInfo> parent = null;
        if (!db.getCollections().containsKey(parentDir)) {
            parent = db.createTreeMap(parentDir);
        } else {
            parent = db.getTreeMap(parentDir);
        }
        String dir=String.format("%s%s",parentDir.endsWith("/")?parentDir:String.format("%s/",parentDir),folder);
        if(!db.getCollections().containsKey(dir)){
            db.createTreeMap(dir);
        }
        if(!Utils.isEmpty(folder)) {
            parent.put(folder, new FileInfo(FileType.dir, folder));
        }
        db.commit();
        return dir;
    }
}
