package cj.studio.fs.indexer;

import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultUCPorts implements IUCPorts {
    OkHttpClient client;
    List<String> ucAddresses;

    public DefaultUCPorts(IServiceProvider site) {
        client = (OkHttpClient) site.getService("$.okhttp");
        IServerConfig config = (IServerConfig) site.getService("$.config");
        ucAddresses = config.ucAddresses();
    }

    @Override
    public Map<String, Object> verifyToken(String appid, String token) {
        int index = Math.abs(token.hashCode()) % ucAddresses.size();
        String url = ucAddresses.get(index);
        url = String.format("%s?appid=%s&token=%s", url, appid, token);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Rest-Command", "verification")
                .build();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            String json = response.body().string();
            Gson gson = new Gson();
            Map<String, Object> map = gson.fromJson(json, HashMap.class);
            if ((double) map.get("status") >= 400) {
                throw new RuntimeException("远程出错:" + map.get("status") + " " + map.get("message"));
            }
            return gson.fromJson(map.get("dataText") + "", HashMap.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public Map<String, Object> auth(String appid, String accountName,String password) {
        int index = Math.abs(String.format("%s-%s-%s",appid,accountName,password).hashCode()) % ucAddresses.size();
        String url = ucAddresses.get(index);
        url = String.format("%s?appid=%s&accountName=%s&password=%s", url, appid, accountName,password);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Rest-Command", "auth")
                .build();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            String json = response.body().string();
            Gson gson = new Gson();
            Map<String, Object> map = gson.fromJson(json, HashMap.class);
            if ((double) map.get("status") >= 400) {
                throw new RuntimeException("远程出错:" + map.get("status") + " " + map.get("message"));
            }
            return gson.fromJson(map.get("dataText") + "", HashMap.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

