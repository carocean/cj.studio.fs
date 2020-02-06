package cj.studio.fs.indexer;

import cj.studio.fs.indexer.util.Encript;
import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultUCPorts implements IUCPorts {
    OkHttpClient client;
    List<String> ucAddresses;
    String appid;
    String appKey;
    String appSecret;
    String device;

    public DefaultUCPorts(IServiceProvider site) {
        client = (OkHttpClient) site.getService("$.okhttp");
        IServerConfig config = (IServerConfig) site.getService("$.config");
        ucAddresses = config.ucAddresses();
        appid = config.appid();
        appKey = config.appKey();
        appSecret = config.appSecret();
        device = config.device();
    }

    @Override
    public Map<String, Object> verifyToken(String accessToken) {

        String nonce = Encript.md5(UUID.randomUUID().toString());
        String sign = Encript.md5(String.format("%s%s%s", appKey, nonce, appSecret));

        int index = Math.abs(accessToken.hashCode()) % ucAddresses.size();
        String url = ucAddresses.get(index);
        url = String.format("%s?token=%s", url, accessToken);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Rest-Command", "verification")
                .addHeader("Rest-Command", "auth")
                .addHeader("App-Id", appid)
                .addHeader("App-Key", appKey)
                .addHeader("App-Nonce", nonce)
                .addHeader("App-Sign", sign)
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
    public Map<String, Object> auth(String accountCode, String password) {
        String nonce = Encript.md5(UUID.randomUUID().toString());
        String sign = Encript.md5(String.format("%s%s%s", appKey, nonce, appSecret));
        int index = Math.abs(String.format("%s-%s-%s", appid, accountCode, password).hashCode()) % ucAddresses.size();
        String url = ucAddresses.get(index);
        url = String.format("%s?device=%s&accountCode=%s&password=%s", url, device, accountCode, password);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Rest-Command", "auth")
                .addHeader("App-Id", appid)
                .addHeader("App-Key", appKey)
                .addHeader("App-Nonce", nonce)
                .addHeader("App-Sign", sign)
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

