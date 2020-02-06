package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultReadAccessController implements IAccessController {
    IServiceProvider site;
    IUCPorts iucPorts;
    Map<String, ACE> acl;
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
        List<String> acl = config.rbacACL();
        this.acl = new HashMap<>();
        for (String ace : acl) {
            ACE one = parseACE(ace);
            this.acl.put(one.dir, one);
        }
    }

    private ACE parseACE(String ace) {
        String remain = ace;
        remain = trimStart(remain);
        int pos = remain.indexOf(" ");
        String dirType = remain.substring(0, pos);
        remain = remain.substring(pos + 1, remain.length());
        remain = trimStart(remain);
        pos = remain.indexOf(" ");
        String dir = "";
        List<String> roles = new ArrayList<>();
        if (pos > -1) {
            dir = remain.substring(0, pos);
            remain = remain.substring(pos + 1, remain.length());
            remain = trimStart(remain);
            pos = remain.indexOf(" ");
            String rolesStr = "";
            if (pos > -1) {
                rolesStr = remain.substring(0, pos);
            } else {
                rolesStr = remain;
            }
            if (rolesStr != null && !"".equals(rolesStr)) {
                String[] spRoles = rolesStr.split("\\|");
                for (String str : spRoles) {
                    if (str == null || "".equals(str)) {
                        continue;
                    }
                    roles.add(String.format("app:%s@%s", str, appid));
                }
            }
        } else {
            dir = remain;
        }
        ACE one = new ACE();
        one.dir = dir;
        one.dirType = dirType;
        one.roles = roles;
        return one;
    }

    private String trimStart(String ace) {
        while (ace.startsWith(" ")) {
            ace = ace.substring(1, ace.length());
        }
        return ace;
    }

    @Override
    public boolean hasListRights(String path, String accessToken) {
        if (!forceToken) {
            return true;
        }
        if (Utils.isEmpty(accessToken)) {
            return false;
        }
        Map<String, Object> info = iucPorts.verifyToken(accessToken);
        List<String> roles = (List<String>) info.get("roles");
        String person = (String) info.get("person");
        String accountCode = person.substring(0, person.indexOf("@"));
        for (String dir : acl.keySet()) {
            if (!path.startsWith(dir)) {
                continue;
            }
            ACE one = acl.get(dir);
            switch (one.dirType) {
                case "rootDir":
                case "systemDir":
                    for (String role : one.roles) {
                        if (roles.contains(role)) {
                            return true;
                        }
                    }
                    break;
                case "usersDir":
                    String prev = String.format("%s/%s", one.dir, accountCode);
                    if (path.startsWith(prev)) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    @Override
    public boolean hasWriteRights(String path, String accessToken) throws AccessTokenExpiredException {
        if (!forceToken) {
            return true;
        }
        if (Utils.isEmpty(accessToken)) {
            return false;
        }
        Map<String, Object> info = iucPorts.verifyToken(accessToken);
        if ((boolean) info.get("isExpired")) {
            throw new AccessTokenExpiredException(String.format("pubTime=%s,expireTime=%s", info.get("pubTime"), info.get("expireTime")));
        }
        List<String> roles = (List<String>) info.get("roles");
        String person = (String) info.get("person");
        String accountCode = person.substring(0, person.indexOf("@"));

        for (String dir : acl.keySet()) {
            if (!path.startsWith(dir)) {
                continue;
            }
            ACE one = acl.get(dir);
            switch (one.dirType) {
                case "rootDir":
                case "systemDir":
                    for (String role : one.roles) {
                        if (!roles.contains(role)) {
                            return false;
                        }
                    }
                    break;
                case "usersDir":
                    String prev = String.format("%s/%s", one.dir, accountCode);
                    if (!path.startsWith(prev)) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    @Override
    public boolean hasReadRights(String path, String accessToken) throws AccessTokenExpiredException {
        if (!forceToken) {
            return true;
        }
        if (Utils.isEmpty(accessToken)) {
            return false;
        }
        Map<String, Object> info = iucPorts.verifyToken(accessToken);
        if ((boolean) info.get("isExpired")) {
            throw new AccessTokenExpiredException(String.format("pubTime=%s,expireTime=%s", info.get("pubTime"), info.get("expireTime")));
        }
        List<String> roles = (List<String>) info.get("roles");
        String person = (String) info.get("person");
        String accountCode = person.substring(0, person.indexOf("@"));
        for (String dir : acl.keySet()) {
            if (!path.startsWith(dir)) {
                continue;
            }
            ACE one = acl.get(dir);
            switch (one.dirType) {
                case "rootDir":
                case "systemDir":
                    for (String role : one.roles) {
                        if (!roles.contains(role)) {
                            return false;
                        }
                    }
                    break;
                case "usersDir":
                    String prev = String.format("%s/%s", one.dir, accountCode);
                    if (!path.startsWith(prev)) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }


}

class ACE {
    String dirType;
    String dir;
    List<String> roles;
}