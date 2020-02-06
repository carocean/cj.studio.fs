package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.*;
import cj.studio.fs.indexer.util.Utils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LoginPage implements IPage {
    @Override
    public String path() {
        return "/public/login.html";
    }

    @Override
    public void doService(IPageContext context) throws IOException {
        String url=context.request().uri();
        HttpRequest request=context.request();
        IServiceProvider site=context.site();
        ChannelHandlerContext ctx=context.ctx();
        Map<String, String> params = Utils.parseQueryString(url);
        String user = params.get("user");
        user = URLDecoder.decode(user, "utf-8");
        String pwd = params.get("pwd");
        IUCPorts iucPorts = (IUCPorts) site.getService("$.uc.ports");
        Map<String, Object> result = iucPorts.auth(user, pwd);
        Map<String, Object> subject =(Map<String, Object>) result.get("subject");
        List<String> roles = (List<String>) subject.get("roles");
        IServerConfig config = (IServerConfig) context.site().getService("$.config");
        if (!roles.contains(String.format("app:administrators@%s",config.appid()))) {
            throw new RuntimeException("不是管理员");
        }
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().add(HttpHeaderNames.LOCATION, "/mg/index.html");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        List<Cookie> cookies = new ArrayList<>();
        Cookie tokenCookie = new DefaultCookie("accessToken", ((Map<String,Object>)result.get("token")).get("accessToken") + "");
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(9999999999999999L);
        cookies.add(tokenCookie);
        List<String> v = ServerCookieEncoder.encode(cookies);
        response.headers().set(HttpHeaderNames.SET_COOKIE, v);
        if (!close) {
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
        }

        // Write the response.
        ChannelFuture future = ctx.channel().writeAndFlush(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

}
