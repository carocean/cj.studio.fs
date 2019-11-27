package cj.studio.fs.indexer.util;

import cj.studio.fs.indexer.FileInfo;
import cj.studio.fs.indexer.FileType;
import cj.studio.fs.indexer.IPage;
import io.netty.handler.codec.http.*;
import org.apache.jdbm.DB;

import java.text.DecimalFormat;
import java.util.*;

public class Utils {
    public static String getParentDir(String file) {
        if (Utils.isEmpty(file) || "/".equals(file)) {
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
        int pos = file.lastIndexOf("?");
        if (pos > -1) {
            file = file.substring(0, pos);
        }
        pos = file.lastIndexOf("/");
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
        String parentDir = "/";
        do {
            while ((remaining.startsWith("/"))) {
                remaining = dir.substring(1, remaining.length());
            }
            int pos = remaining.indexOf("/");
            if (pos < 0) {
                folder = remaining;
                remaining = "";
            } else {
                folder = remaining.substring(0, pos);
                remaining = remaining.substring(pos + 1, remaining.length());
            }
            parentDir = mkdir(db, parentDir, folder);
        } while (!Utils.isEmpty(remaining));
    }

    private synchronized static String mkdir(DB db, String parentDir, String folder) {
        Map<String, FileInfo> parent = null;
        if (!db.getCollections().containsKey(parentDir)) {
            parent = db.createTreeMap(parentDir);
        } else {
            parent = db.getTreeMap(parentDir);
        }
        String dir = String.format("%s%s", parentDir.endsWith("/") ? parentDir : String.format("%s/", parentDir), folder);
        if (!db.getCollections().containsKey(dir)) {
            db.createTreeMap(dir);
        }
        if (!Utils.isEmpty(folder)) {
            parent.put(folder, new FileInfo(FileType.dir, folder, System.currentTimeMillis()));
        }
        db.commit();
        return dir;
    }

    public static Map<String, String> parseQueryString(String uri) {
        int pos = uri.indexOf("?");
        Map<String, String> params = new HashMap<>();
        if (pos < 0) return params;
        String qs = uri.substring(pos + 1, uri.length());
        String kv = "";
        String remain = qs;

        while (true) {
            pos = remain.indexOf("&");
            if (pos < 0) {
                kv = remain;
                parseKV(kv, params);
                break;
            }
            kv = remain.substring(0, pos);
            parseKV(kv, params);
            remain = remain.substring(pos + 1, remain.length());
        }
        return params;
    }

    private static void parseKV(String kv, Map<String, String> params) {
        int pos = kv.indexOf("=");
        if (pos < 0) return;
        String k = kv.substring(0, pos);
        if (isEmpty(k)) {
            return;
        }
        String v = kv.substring(pos + 1, kv.length());
        if (isEmpty(v)) {
            v = "";
        }
        params.put(k, v);
    }

    public static String getToken(String uri) {
        return parseQueryString(uri).get("Access-Token");
    }

    public static String getTokenFromCookie(FullHttpRequest request) {
        String value = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
        if (Utils.isEmpty(value)) {
            return "";
        }
        Set<Cookie> cookies = ServerCookieDecoder.decode(value);
        for (Cookie cookie : cookies) {
            if ("Access-Token".equals(cookie.name())) {
                return cookie.value();
            }
        }
        return "";
    }

    public static String getAppidFromCookie(FullHttpRequest request) {
        String value = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
        if (Utils.isEmpty(value)) {
            return "";
        }
        Set<Cookie> cookies = ServerCookieDecoder.decode(value);
        for (Cookie cookie : cookies) {
            if ("App-ID".equals(cookie.name())) {
                return cookie.value();
            }
        }
        return "";
    }

    public static synchronized String getPathWithoutQuerystring(String uri) {
        int pos = uri.indexOf("?");
        if (pos < 0) return uri;
        return uri.substring(0, pos);
    }

    public static String getQuerystring(String uri) {
        int pos = uri.indexOf("?");
        if (pos < 0) return "";
        return uri.substring(pos + 1, uri.length());
    }

    public static String getPrintSize(long size) {
        //获取到的size为：1705230
        int GB = 1024 * 1024 * 1024;//定义GB的计算常量
        int MB = 1024 * 1024;//定义MB的计算常量
        int KB = 1024;//定义KB的计算常量
        DecimalFormat df = new DecimalFormat("0.00");//格式化小数
        String resultSize = "";
        if (size / GB >= 1) {
            //如果当前Byte的值大于等于1GB
            resultSize = df.format(size / (float) GB) + "GB   ";
        } else if (size / MB >= 1) {
            //如果当前Byte的值大于等于1MB
            resultSize = df.format(size / (float) MB) + "MB   ";
        } else if (size / KB >= 1) {
            //如果当前Byte的值大于等于1KB
            resultSize = df.format(size / (float) KB) + "KB   ";
        } else {
            resultSize = size + "B   ";
        }
        return resultSize;
    }

    public static String getFolder(String dir) {
        if ("/".equals(dir)) {
            return "";
        }
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        int pos = dir.lastIndexOf("/");
        if (pos < 0) {
            return "";
        }
        return dir.substring(pos + 1, dir.length());
    }

    public static synchronized IPage getPage(String uri, Map<String, IPage> pages) {
        String path = getPathWithoutQuerystring(uri);
        IPage page = pages.get(path);
        if (page != null) return page;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        } else {
            path = path + "/";
        }
        return pages.get(path);
    }

    public static Map<String, List<String>> parameters(HttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, List<String>> params= decoder.parameters();
        return params;
    }
}
