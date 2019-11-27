package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.IPage;
import cj.studio.fs.indexer.IPageContext;
import cj.studio.fs.indexer.IServiceProvider;
import cj.studio.fs.indexer.IUCPorts;
import cj.studio.fs.indexer.util.Utils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.io.IOException;
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
        String pwd = params.get("pwd");
        String appid = params.get("appid");
        IUCPorts iucPorts = (IUCPorts) site.getService("$.uc.ports");
        Map<String, Object> result = iucPorts.auth(appid, user, pwd);
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().add(HttpHeaderNames.LOCATION, "/mg/index.html");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        List<Cookie> cookies = new ArrayList<>();
        Cookie appidCookie = new DefaultCookie("App-ID", appid);
        appidCookie.setPath("/");
        appidCookie.setMaxAge(9999999999999999L);
        Cookie tokenCookie = new DefaultCookie("Access-Token", result.get("accessToken") + "");
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(9999999999999999L);
        cookies.add(appidCookie);
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
