package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultReadAccessController implements IAccessController {
    IServiceProvider site;
    IUCPorts iucPorts;
    List<ACE> reads;
    List<ACE> list;
    List<String> unlimit;//urls
    List<ACE> selfACEs;//slef说明充许用户只能访问指定的url目录下其用户名作为子目录的目录，不能跨目录访问

    public DefaultReadAccessController(IServiceProvider site) {
        this.site = site;
        iucPorts = (IUCPorts) site.getService("$.uc.ports");
        IServerConfig config = (IServerConfig) site.getService("$.config");
        loadRBAC(config);
    }

    private void loadRBAC(IServerConfig config) {
        if (!"default".equals(config.rbacStrategy())) {
            throw new RuntimeException("不是默认的策略");
        }
        List<String> acl = config.rbacACL();
        this.reads = new ArrayList<>();
        this.list = new ArrayList<>();
        this.unlimit = new ArrayList<>();
        this.selfACEs = new ArrayList<>();
        for (String ace : acl) {
            ACE e = parseACE(ace);
            switch (e.action) {
                case "read":
                    reads.add(e);
                    break;
                case "list":
                    list.add(e);
                    break;
            }
            if ("unlimit".equals(e.limitType) && "*@users".equals(e.objects)) {
                unlimit.add(e.url);
            }
            if ("self".equals(e.by)) {
                selfACEs.add(e);
            }
        }
    }

    private ACE parseACE(String ace) {
        ace = trimStart(ace);
        int pos = ace.indexOf(" ");
        if (pos < 0) {
            throw new RuntimeException("缺少授权对象");
        }
        ACE e = new ACE();
        String cmd = ace.substring(0, pos);
        e.command = cmd;
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        pos = ace.indexOf(" ");
        if (pos < 0) {
            throw new RuntimeException("缺少操作");
        }
        String objects = ace.substring(0, pos);
        e.objects = objects;
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        pos = ace.indexOf(" ");
        if (pos < 0) {
            throw new RuntimeException("缺少资源地址");
        }
        String action = ace.substring(0, pos);
        e.action = action;
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        pos = ace.indexOf(" ");
        if (pos < 0) {
            String url = ace.trim();
            e.url = url;
            return e;
        }
        String url = ace.substring(0, pos);
        e.url = url;
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        pos = ace.indexOf(" ");
        if (pos < 0) {
            if (Utils.isEmpty(ace)) {
                String limitType = ace;
                e.limitType = ace;
                return e;
            }
            throw new RuntimeException("缺少limitType的值");
        }
        String limitType = ace.substring(0, pos);
        e.limitType = limitType;
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        pos = ace.indexOf(" ");
        if (pos < 0) {
            String limitText = ace.trim();
            e.limitText = limitText;
            return e;
        }
        String limitText = ace.substring(0, pos);
        e.limitText = limitText;
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        if (Utils.isEmpty(ace)) {
            return e;
        }

        pos = ace.indexOf(" ");
        if (pos < 0) {
            throw new RuntimeException("缺少by的值");
        }
        String by = ace.substring(0, pos);
        ace = ace.substring(pos + 1, ace.length());
        ace = trimStart(ace);

        String byText = ace.trim();
        e.by = byText;
        return e;
    }

    private String trimStart(String ace) {
        while (ace.startsWith(" ")) {
            ace = ace.substring(1, ace.length());
        }
        return ace;
    }

    @Override
    public boolean hasListRights(String uri, String appid, String accessToken) {
        for (String url : unlimit) {
            if (uri.startsWith(url)) {
                return true;
            }
        }
        if (Utils.isEmpty(appid) || Utils.isEmpty(accessToken)) {
            return false;
        }
        Map<String, Object> info = iucPorts.verifyToken(appid, accessToken);
        List<Map<String, Object>> ucroleList = (List<Map<String, Object>>) info.get("uc-roles");
        List<String> ucroles = new ArrayList<>();
        for (Map<String, Object> obj : ucroleList) {
            ucroles.add(obj.get("roleId") + "");
        }
        String uid = (String) info.get("sub");
        for (ACE e : selfACEs) {
            if (uri.startsWith(e.url)) {
                String furl = String.format("%s%s", e.url.endsWith("/") ? e.url : e.url + "/", uid);
                if (uri.startsWith(furl)) {
                    if (e.action.equals("read") && e.command.equals("allow")) {
                        return true;
                    }
                    //由于该类主要检查读的，所以写被直接拒绝
                    return false;
                }
                return false;
            }
        }
        for (ACE e : reads) {
            if (!"deny".equals(e.command)) {
                continue;
            }
            if (!uri.startsWith(e.url)) {
                continue;
            }

            if (e.objects.equals("*@users")) {
                return false;
            }
            if (e.objects.equals("*@ucroles")) {
                return false;
            }
            if (e.objects.equals(String.format("%s@users", uid))) {
                return false;
            }
            int pos = e.objects.indexOf("@");
            if (pos < 0) {
                continue;
            }
            String type = e.objects.substring(pos + 1, e.objects.length());
            String id = e.objects.substring(0, pos);
            if ("ucroles".equals(type) && ucroles.contains(id)) {
                return true;
            }
            if ("users".equals(type) && id.equals(uid)) {
                return true;
            }
        }
        for (ACE e : list) {
            if ("deny".equals(e.command)) {
                continue;
            }
            if (!uri.startsWith(e.url)) {
                continue;
            }
            if (e.objects.equals("*@users")) {
                return true;
            }
            if (e.objects.equals("*@ucroles")) {
                return true;
            }
            if (e.objects.equals(String.format("%s@users", uid))) {
                return true;
            }
            int pos = e.objects.indexOf("@");
            if (pos < 0) {
                continue;
            }
            String type = e.objects.substring(pos + 1, e.objects.length());
            String id = e.objects.substring(0, pos);
            if ("ucroles".equals(type) && ucroles.contains(id)) {
                return true;
            }
            if ("users".equals(type) && id.equals(uid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasReadRights(String uri, String appid, String accessToken) {
        for (String url : unlimit) {
            if (uri.startsWith(url)) {
                return true;
            }
        }
        if (Utils.isEmpty(appid) || Utils.isEmpty(accessToken)) {
            return false;
        }
        Map<String, Object> info = iucPorts.verifyToken(appid, accessToken);
        List<Map<String, Object>> ucroleList = (List<Map<String, Object>>) info.get("uc-roles");
        List<String> ucroles = new ArrayList<>();
        for (Map<String, Object> obj : ucroleList) {
            ucroles.add(obj.get("roleId") + "");
        }
        String uid = (String) info.get("sub");
        for (ACE e : selfACEs) {
            if (uri.startsWith(e.url)) {
                String furl = String.format("%s%s", e.url.endsWith("/") ? e.url : e.url + "/", uid);
                if (uri.startsWith(furl)) {
                    if (e.action.equals("read") && e.command.equals("allow")) {
                        return true;
                    }
                    //由于该类主要检查读的，所以写被直接拒绝
                    return false;
                }
                return false;
            }
        }
        for (ACE e : reads) {
            if (!"allow".equals(e.command)) {
                continue;
            }
            if (!uri.startsWith(e.url)) {
                continue;
            }

            if (e.objects.equals("*@users")) {
                return true;
            }
            if (e.objects.equals("*@ucroles")) {
                return true;
            }
            if (e.objects.equals(String.format("%s@users", uid))) {
                return true;
            }
            int pos = e.objects.indexOf("@");
            if (pos < 0) {
                continue;
            }
            String type = e.objects.substring(pos + 1, e.objects.length());
            String id = e.objects.substring(0, pos);
            if ("ucroles".equals(type) && ucroles.contains(id)) {
                return true;
            }
            if ("users".equals(type) && id.equals(uid)) {
                return true;
            }
        }

        return false;
    }

    class ACE {
        String command;//allow|deny
        String objects;//*@users|tenantAdministrators@ucroles
        String url;//受保护资源地址
        String limitType;//limit|unlimit
        String limitText;// 一般为token
        String by;//一般为self
        String action;//具体操作
    }
}
