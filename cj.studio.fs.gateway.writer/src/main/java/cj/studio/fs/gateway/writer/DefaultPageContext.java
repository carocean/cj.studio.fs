package cj.studio.fs.gateway.writer;

import cj.studio.fs.indexer.FileSystem;
import cj.studio.fs.indexer.IPageContext;
import cj.studio.fs.indexer.IServiceProvider;
import cj.studio.fs.indexer.util.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.netty.buffer.Unpooled.copiedBuffer;

public class DefaultPageContext implements IPageContext {
    IServiceProvider site;
    ChannelHandlerContext ctx;
    HttpRequest request;
    Map<String, String> mimes;
    FileSystem fileSystem;
    String currentDir;
    public DefaultPageContext(IServiceProvider site, FileSystem fileSystem, ChannelHandlerContext ctx,String currentDir, HttpRequest request, Map<String, String> mimes) {
        this.site=site;
        this.ctx=ctx;
        this.request=request;
        this.mimes=mimes;
        this.fileSystem=fileSystem;
        this.currentDir=currentDir;
    }

    @Override
    public void dispose() {
        this.site=null;
        this.ctx=null;
        this.request=null;
        this.mimes=null;
        this.fileSystem=null;
    }
    @Override
    public String currentDir() {
        return currentDir;
    }

    @Override
    public ChannelHandlerContext ctx() {
        return ctx;
    }
    @Override
    public FileSystem fileSystem() {
        return fileSystem;
    }
    @Override
    public HttpRequest request() {
        return request;
    }
    @Override
    public IServiceProvider site() {
        return site;
    }

    @Override
    public Map<String, String> cookies() {
        String cookieSeq = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
        Map<String, String> map = new HashMap<>();
        if (cookieSeq != null) {
            Set<Cookie> cookies = ServerCookieDecoder.decode(cookieSeq);
            for (Cookie cookie : cookies) {
                map.put(cookie.name(), cookie.value());
            }
        }
        return map;
    }
    @Override
    public boolean isDoc() {
        String url=request.uri();
        int pos = url.lastIndexOf("?");
        if (pos > -1) {
            url = url.substring(0, pos);
        }
        return url.lastIndexOf(".")>-1&&url.endsWith(".html");
    }

    @Override
    public String relativePath() {
        String url=request.uri();
        while (url.startsWith("/")) {
            url = url.substring(1, url.length());
        }
        int pos = url.lastIndexOf("/");
        if (pos < 0) {
            return "/";
        }
        return url.substring(pos,url.length());
    }

    @Override
    public boolean isResource() {
        String url=request.uri();
        int pos = url.lastIndexOf("?");
        if (pos > -1) {
            url = url.substring(0, pos);
        }
        return url.lastIndexOf(".")>-1&&!url.endsWith(".html")&&!url.endsWith(".service");
    }


    @Override
    public void writeResponse(Channel channel, StringBuilder responseContent) {
        ByteBuf buf = copiedBuffer(responseContent.toString(), CharsetUtil.UTF_8);
        responseContent.setLength(0);

        // Decide whether to close the connection or not.
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        }

        Set<Cookie> cookies;
        String value = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            cookies = ServerCookieDecoder.decode(value);
        }
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (Cookie cookie : cookies) {
                response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.encode(cookie));
            }
        }
        // Write the response.
        ChannelFuture future = channel.writeAndFlush(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void redirect( String path) {
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().add(HttpHeaderNames.LOCATION, path);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
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
    @Override
    public Document html( String path) throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        path = String.format("site%s", path);
        InputStream in = cl.getResourceAsStream(path);
        try {
            int pos = path.lastIndexOf(".");
            String fn = "";
            if (pos > -1) {
                fn = path.substring(pos + 1, path.length());
            }
            if (!"html".equals(fn)) {
                throw new IOException("不是html：" + path);
            }
            Document document = Jsoup.parse(in, "utf-8", "");
            return document;
        } catch (IOException e) {
            throw e;
        }finally {
            in.close();
        }
    }
    @Override
    public void writeDoc( Document document) {
        ByteBuf buf = copiedBuffer(document.toString(), CharsetUtil.UTF_8);
        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimes.get("html"));
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response);
    }

    @Override
    public String path() {
        return Utils.getPathWithoutQuerystring(request.uri());
    }

    @Override
   public void writeResource( String path, Set<Cookie> cookies) throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        path = Utils.getPathWithoutQuerystring(path);
        path = String.format("site%s", path);
        InputStream in = cl.getResourceAsStream(path);
        if (in == null) {
            throw new FileNotFoundException(path);
        }
        try {

            int pos = path.lastIndexOf(".");
            String fn = "";
            if (pos > -1) {
                fn = path.substring(pos + 1, path.length());
            }
            //下面是资源
            ByteBuf buf = Unpooled.buffer();
            byte[] data = new byte[8192];
            while (true) {
                int len = in.read(data);
                if (len < 0) {
                    break;
                }
                buf.writeBytes(data, 0, len);
            }
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
            if (Utils.isEmpty(fn)) {
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimes.get("html"));
            } else {
                if (mimes.containsKey(fn)) {
                    String mime = mimes.get(fn);
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, mime);
                } else {
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimes.get("html"));
                }
            }
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
            if (cookies != null && !cookies.isEmpty()) {
                for (Cookie cookie : cookies) {
                    response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.encode(cookie));
                }
            }
            ctx.writeAndFlush(response);
        } catch (IOException e) {
            throw e;
        }

    }
}
