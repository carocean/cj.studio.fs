/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package cj.studio.fs.gateway.writer;

import cj.studio.fs.indexer.FileSystem;
import cj.studio.fs.indexer.IFileWriter;
import cj.studio.fs.indexer.IServiceProvider;
import cj.studio.fs.indexer.IUCPorts;
import cj.studio.fs.indexer.util.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(HttpUploadServerHandler.class.getName());

    private HttpRequest request;

    private boolean readingChunks;


    private static final HttpDataFactory factory =
            new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

    private HttpPostRequestDecoder decoder;
    static Map<String, String> mimes;

    static {
        mimes = new HashMap<>();
        mimes.put("js", "application/javascript; charset=UTF-8");
        mimes.put("css", "text/css; charset=UTF-8");
        mimes.put("html", "text/html; charset=UTF-8");
        mimes.put("htm", "text/html; charset=UTF-8");
        mimes.put("jpg", "image/jpeg; charset=UTF-8");
        mimes.put("svg", "image/svg+xml; charset=UTF-8");
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file
        // on exit (in normal
        // exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on
        // exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    }

    IServiceProvider site;
    FileSystem fileSystem;

    public HttpUploadServerHandler(IServiceProvider site) {
        this.site = site;
        fileSystem = (FileSystem) site.getService("$.fileSystem");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;
            URI uri = new URI(request.uri());
            String url = uri.toString();
            String cookieSeq = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
            String name = Utils.getFileName(url);
            if ((cookieSeq == null || cookieSeq.length() == 0) && (name.lastIndexOf(".") < 0 || name.endsWith(".html"))) {
                writeResource(ctx, "/login.html", null);
                return;
            }
            Set<Cookie> cookies = ServerCookieDecoder.decode(cookieSeq);
            Map<String, String> map = new HashMap<>();
            for (Cookie cookie : cookies) {
                map.put(cookie.name(), cookie.value());
            }
            if (uri.getPath().startsWith("/mg")) {
                // Write Menu
                url = url.substring("/mg".length(), url.length());
                writeResource(ctx, url, null);
                return;
            } else if (uri.getPath().startsWith("/upload")) {
                //放过去，是文件
            } else if (uri.getPath().startsWith("/fs")) {
                QueryStringDecoder decoder = new QueryStringDecoder(url);
                String dir=decoder.parameters().get("dir").get(0);
                sendListing(ctx, dir, map.get("App-ID"), map.get("Access-Token"));
                return;
            } else if (uri.getPath().startsWith("/bs")) {//后台服务
                url = url.substring("/bs".length(), url.length());
                doService(ctx, request, url);
                return;
            } else {
                sendError(ctx, FORBIDDEN);
                return;
            }
            try {
                decoder = new HttpPostRequestDecoder(factory, request);
            } catch (ErrorDataDecoderException e1) {
                e1.printStackTrace();
                ctx.channel().close();
                return;
            }

            readingChunks = HttpHeaderUtil.isTransferEncodingChunked(request);
            if (readingChunks) {
                readingChunks = true;
            }
        }

        // check if the decoder was constructed before
        // if not it handles the form get
        if (decoder != null) {
            if (msg instanceof HttpContent) {
                // New chunk is received
                HttpContent chunk = (HttpContent) msg;
                try {
                    decoder.offer(chunk);
                } catch (ErrorDataDecoderException e1) {
                    e1.printStackTrace();
                    ctx.channel().close();
                    return;
                }
                // example of reading chunk by chunk (minimize memory usage due to
                // Factory)
                readHttpDataChunkByChunk();
                // example of reading only if at the end
                if (chunk instanceof LastHttpContent) {
                    readingChunks = false;

                    reset();
                }
            }
        }
    }


    private void doService(ChannelHandlerContext ctx, HttpRequest request, String url) {
        if (url.startsWith("/login.service")) {
            login(ctx, url);
            return;
        }
    }

    private void login(ChannelHandlerContext ctx, String url) {
        Map<String, String> params = Utils.parseQueryString(url);
        String user = params.get("user");
        String pwd = params.get("pwd");
        String appid = params.get("appid");
        IUCPorts iucPorts = (IUCPorts) site.getService("$.uc.ports");
        Map<String, Object> result = iucPorts.auth(appid, user, pwd);
        System.out.println(result);
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

    private void reset() {
        request = null;

        // destroy the decoder to release all resources
        decoder.destroy();
        decoder = null;
    }

    /**
     * Example of reading request by chunk and getting values from chunk to chunk
     */
    private void readHttpDataChunkByChunk() {
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data != null) {
                    try {
                        // new value
                        writeHttpData(data);
                    } finally {
                        data.release();
                    }
                }
            }
        } catch (EndOfDataDecoderException e1) {
            // end
            e1.printStackTrace();
        }
    }

    private void writeHttpData(InterfaceHttpData data) {
        if (data.getHttpDataType() == HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            String value;
            try {
                value = attribute.getValue();
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }
        } else {
            if (data.getHttpDataType() == HttpDataType.FileUpload) {
                FileUpload fileUpload = (FileUpload) data;
                if (fileUpload.isCompleted()) {
                    String fileName = fileUpload.getFilename();
                    String path = String.format("/%s", fileName);
                    IFileWriter writer = null;
                    try {
                        writer = fileSystem.openWriter(path);
                        if (fileUpload.isInMemory()) {
                            byte[] buf = fileUpload.get();
                            writer.write(buf);
                        } else {
                            System.out.println(fileUpload.isInMemory());
                            File file = fileUpload.getFile();
                            FileInputStream in = new FileInputStream(file);
                            byte[] buf = new byte[8192];
                            while (true) {
                                int reads = in.read(buf, 0, buf.length);
                                if (reads < 0) {
                                    break;
                                }
                                writer.write(buf, 0, reads);
                            }
                            System.out.println(file);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (writer != null) {
                            try {
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // fileUpload.isInMemory();// tells if the file is in Memory
                    // or on File
                    // fileUpload.renameTo(dest); // enable to move into another
                    // File dest
                    // decoder.removeFileUploadFromClean(fileUpload); //remove
                    // the File of to delete file
                } else {
                }
            }
        }
    }

    private void sendListing(ChannelHandlerContext ctx, String dirPath, String appid, String token) {
        String qs = String.format("?App-ID=%s&Access-Token=%s", appid, token);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        String parentDir="";
        if ("/".equals(dirPath)) {
            parentDir = "/";
        }else{
            parentDir=dirPath;
            while (parentDir.endsWith("/")){
                parentDir = parentDir.substring(0, parentDir.length()-1);
            }
            int pos = parentDir.lastIndexOf("/");
            parentDir = parentDir.substring(0, pos);
            if ("".endsWith(parentDir)) {
                parentDir = "/";
            }
        }
        StringBuilder buf = new StringBuilder()
                .append(String.format("<div currentDir='%s'>\r\n",parentDir))
                .append("<h3>Listing of: ")
                .append(dirPath)
                .append("</h3>\r\n")
                .append("<ul>")
                .append(String.format("<li  style='list-style: none;'><a parentDir='%s' style='text-decoration: none; ' href='#'>..</a></li>\r\n",parentDir));

        for (String name : fileSystem.listDir(dirPath)) {
            buf.append("<li style='list-style: none;'><a dir='"+name+"' style='text-decoration: none; ' href=\"")
                    .append(name)
                    .append(qs)
                    .append("\">")
                    .append("+&nbsp;&nbsp;")
                    .append(name)
                    .append("</a></li>\r\n");
        }
        for (String name : fileSystem.listFile(dirPath)) {
            buf.append("<li  style='list-style: none;'><a style='text-decoration: none; ' href=\"")
                    .append(name)
                    .append(qs)
                    .append("\">")
                    .append("-&nbsp;&nbsp;")
                    .append(name)
                    .append("</a></li>\r\n");
        }

        buf.append("</ul></div>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    void writeResource(ChannelHandlerContext ctx, String url, Set<Cookie> cookies) {
        ClassLoader cl = getClass().getClassLoader();
        url = String.format("site%s", url);

        InputStream in = cl.getResourceAsStream(url);
        try {

            int pos = url.lastIndexOf(".");
            String fn = "";
            if (pos > -1) {
                fn = url.substring(pos + 1, url.length());
            }
            if ("html".equals(fn)) {
                Document document = Jsoup.parse(in, "utf-8", "");
                ByteBuf buf = copiedBuffer(document.toString(), CharsetUtil.UTF_8);
                // Build the response object.
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimes.get("html"));
                response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
                ctx.writeAndFlush(response);
                return;
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
            e.printStackTrace();
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }
}
