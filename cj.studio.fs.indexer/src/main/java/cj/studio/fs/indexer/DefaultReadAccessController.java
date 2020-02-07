package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultReadAccessController implements IAccessController {
    IServiceProvider site;
    IUCPorts iucPorts;
    List<ACE> acl;
    boolean forceToken;
    String appid;

    public DefaultReadAccessController(IServiceProvider site) {
        this.site = site;
        iucPorts = (IUCPorts) site.getService("$.uc.ports");
        IServerConfig config = (IServerConfig) site.getService("$.config");
        appid = config.appid();
        loadRBAC(config);
    }

    private void loadRBAC(IServerConfig config) {
        if (!"default".equals(config.rbacStrategy())) {
            throw new RuntimeException("不是默认的策略");
        }
        forceToken = config.rbacForceToken();
        List<?> acl = config.rbacACL();
        this.acl = new ArrayList<>();
        for (Object ace : acl) {
            Map<String, Object> map = (Map<String, Object>) ace;
            ACE ace1 = parseACE(map);
            this.acl.add(ace1);
        }
        System.out.println("");
    }

    private ACE parseACE(Map<String, Object> ace) {
        ACE one = new ACE();
        String resource = "";
        for (String key : ace.keySet()) {
            resource = key;
            break;
        }
        String resourceRegex = resource.replace("/*/", "(\\w+)").replace("/**", "\\S*");
        one.resourcePattern = Pattern.compile(resourceRegex);
        List<Object> rights = (List<Object>) ace.get(resource);
        for (Object r : rights) {
            Map<String, Object> map = (Map<String, Object>) r;
            String rightsName = "";
            for (String key : map.keySet()) {
                rightsName = key;
                break;
            }
            List<String> roleList = new ArrayList<>();
            String roles = (String) map.get(rightsName);
            if (roles != null) {
                String[] arr = roles.split("\\|");
                for (String roleCode : arr) {
                    if (null == roleCode || "".equals(roleCode)) {
                        continue;
                    }
                    roleList.add(String.format("app:%s@%s", roleCode, appid));
                }
            }
            Rights rights1 = new Rights();
            rights1.roles = roleList == null ? new ArrayList<>() : roleList;
            rights1.rights = ERights.valueOf(rightsName);
            switch (rights1.rights) {
                case list:
                    one.listRights = rights1;
                    break;
                case read:
                    one.readRights = rights1;
                    break;
                case write:
                    one.writeRights = rights1;
                    break;
                case delete:
                    one.deleteRights = rights1;
                    break;
            }
        }
        return one;
    }

    private boolean _hasRight(String path, String accessToken, ERights eRights) {
        if (!forceToken) {
            return true;
        }
        if (Utils.isEmpty(accessToken)) {
            return false;
        }
        String dir = "";
        switch (eRights) {
            case read:
                dir = path;
                break;
            case write:
            case list:
            case delete:
                QueryStringDecoder decoder = new QueryStringDecoder(path, Charset.forName("utf-8"));
                List<String> plist = decoder.parameters().get("dir");
                if (plist != null && !plist.isEmpty()) {
                    dir = plist.get(0);
                }
                break;
        }
        Map<String, Object> info = iucPorts.verifyToken(accessToken);
        List<String> roles = (List<String>) info.get("roles");
        for (ACE ace : acl) {
            Matcher matcher=ace.resourcePattern.matcher(dir);
            if (matcher.matches()) {
                Rights rights = null;
                switch (eRights) {
                    case read:
                        rights = ace.readRights;
                        break;
                    case write:
                        rights = ace.writeRights;
                        break;
                    case list:
                        rights = ace.listRights;
                        break;
                    case delete:
                        rights = ace.deleteRights;
                        break;
                }
                for (String role : rights.roles) {
                    if (roles.contains(role) || String.format("app:everyone@%s",appid).equals(role)) {
                        return true;
                    }
                    if (String.format("app:yourself@%s", appid).equals(role)) {
                        String relpath=trimStartEquals(ace.resourcePattern.pattern(),dir);
                        while (relpath.startsWith("/")) {
                            relpath = relpath.substring(1, relpath.length());
                        }
                        int pos=relpath.indexOf("/");
                        String accountCodeDir="";
                        if (pos > -1) {
                            accountCodeDir = relpath.substring(0, pos);
                        }else{
                            accountCodeDir=relpath;
                        }
                        if (String.format("%s@%s", accountCodeDir, appid).equals(info.get("person"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String trimStartEquals(String resourcePattern, String dir) {
        int pos=0;
        for (int i = 0; i < dir.length(); i++) {
            if (resourcePattern.charAt(i) == dir.charAt(i)) {
                pos++;
                continue;
            }
            break;
        }
        return dir.substring(pos,dir.length());
    }

    @Override
    public boolean hasListRights(String path, String accessToken) {
        return _hasRight(path, accessToken, ERights.list);
    }

    @Override
    public boolean hasWriteRights(String path, String accessToken) throws AccessTokenExpiredException {
        return _hasRight(path, accessToken, ERights.write);
    }

    @Override
    public boolean hasReadRights(String path, String accessToken) throws AccessTokenExpiredException {
        return _hasRight(path, accessToken, ERights.read);
    }


}

class ACE {
    Pattern resourcePattern;
    Rights readRights;
    Rights deleteRights;
    Rights writeRights;
    Rights listRights;
}

class Rights {
    ERights rights;
    List<String> roles;

}

enum ERights {
    read,
    write,
    delete,
    list,
}