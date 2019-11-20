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

import cj.studio.fs.indexer.IServiceProvider;
import cj.studio.fs.indexer.IUCPorts;
import cj.studio.fs.indexer.util.Utils;
import com.sun.org.apache.xalan.internal.xsltc.dom.CurrentNodeListFilter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import okhttp3.OkHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(HttpUploadServerHandler.class.getName());

    private HttpRequest request;

    private boolean readingChunks;

    private final StringBuilder responseContent = new StringBuilder();

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
    public HttpUploadServerHandler(IServiceProvider site) {
        this.site=site;
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
            if (uri.getPath().startsWith("/mg")) {
                // Write Menu
                String url = uri.toString();
                url = url.substring("/mg".length(), url.length());
                writeMenu(ctx, url);
                return;
            } else if (uri.getPath().startsWith("/fs")) {
            } else if (uri.getPath().startsWith("/bs")) {//后台服务
                String url = uri.toString();
                url = url.substring("/bs".length(), url.length());
                doService(ctx, request, url);
            } else {
                sendError(ctx, FORBIDDEN);
                return;
            }
            responseContent.setLength(0);
            responseContent.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
            responseContent.append("===================================\r\n");

            responseContent.append("VERSION: " + request.protocolVersion().text() + "\r\n");

            responseContent.append("REQUEST_URI: " + request.uri() + "\r\n\r\n");
            responseContent.append("\r\n\r\n");

            // new getMethod
            for (Entry<CharSequence, CharSequence> entry : request.headers()) {
                responseContent.append("HEADER: " + entry.getKey() + '=' + entry.getValue() + "\r\n");
            }
            responseContent.append("\r\n\r\n");

            // new getMethod
            Set<Cookie> cookies;
            String value = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
            if (value == null) {
                cookies = Collections.emptySet();
            } else {
                cookies = ServerCookieDecoder.decode(value);
            }
            for (Cookie cookie : cookies) {
                responseContent.append("COOKIE: " + cookie + "\r\n");
            }
            responseContent.append("\r\n\r\n");

            QueryStringDecoder decoderQuery = new QueryStringDecoder(request.uri());
            Map<String, List<String>> uriAttributes = decoderQuery.parameters();
            for (Entry<String, List<String>> attr : uriAttributes.entrySet()) {
                for (String attrVal : attr.getValue()) {
                    responseContent.append("URI: " + attr.getKey() + '=' + attrVal + "\r\n");
                }
            }
            responseContent.append("\r\n\r\n");

            // if GET Method: should not try to create a HttpPostRequestDecoder
            if (request.method().equals(HttpMethod.GET)) {
                // GET Method: should not try to create a HttpPostRequestDecoder
                // So stop here
                responseContent.append("\r\n\r\nEND OF GET CONTENT\r\n");
                // Not now: LastHttpContent will be sent writeResponse(ctx.channel());
                return;
            }
            try {
                decoder = new HttpPostRequestDecoder(factory, request);
            } catch (ErrorDataDecoderException e1) {
                e1.printStackTrace();
                responseContent.append(e1.getMessage());
                writeResponse(ctx.channel());
                ctx.channel().close();
                return;
            }

            readingChunks = HttpHeaderUtil.isTransferEncodingChunked(request);
            responseContent.append("Is Chunked: " + readingChunks + "\r\n");
            responseContent.append("IsMultipart: " + decoder.isMultipart() + "\r\n");
            if (readingChunks) {
                // Chunk version
                responseContent.append("Chunks: ");
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
                    responseContent.append(e1.getMessage());
                    writeResponse(ctx.channel());
                    ctx.channel().close();
                    return;
                }
                responseContent.append('o');
                // example of reading chunk by chunk (minimize memory usage due to
                // Factory)
                readHttpDataChunkByChunk();
                // example of reading only if at the end
                if (chunk instanceof LastHttpContent) {
                    writeResponse(ctx.channel());
                    readingChunks = false;

                    reset();
                }
            }
        } else {
            writeResponse(ctx.channel());
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
        Map<String,Object> result=iucPorts.auth(appid,user,pwd);
        System.out.println(result);
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().add(HttpHeaderNames.LOCATION,"mg/index.html");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        List<Cookie> cookies=new ArrayList<>();
        Cookie appidCookie = new DefaultCookie("App-ID",appid);
        Cookie tokenCookie = new DefaultCookie("Access-Token",result.get("accessToken")+"");
        cookies.add(appidCookie);
        cookies.add(tokenCookie);
        List<String> v=ServerCookieEncoder.encode(cookies);
        response.headers().set(HttpHeaderNames.SET_COOKIE,v);
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
            responseContent.append("\r\n\r\nEND OF CONTENT CHUNK BY CHUNK\r\n\r\n");
        }
    }

    private void writeHttpData(InterfaceHttpData data) {
        if (data.getHttpDataType() == HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            String value;
            try {
                value = attribute.getValue();
            } catch (IOException e1) {
                // Error while reading data from File, only print name and error
                e1.printStackTrace();
                responseContent.append("\r\nBODY Attribute: " + attribute.getHttpDataType().name() + ": "
                        + attribute.getName() + " Error while reading value: " + e1.getMessage() + "\r\n");
                return;
            }
            if (value.length() > 100) {
                responseContent.append("\r\nBODY Attribute: " + attribute.getHttpDataType().name() + ": "
                        + attribute.getName() + " data too long\r\n");
            } else {
                responseContent.append("\r\nBODY Attribute: " + attribute.getHttpDataType().name() + ": "
                        + attribute + "\r\n");
            }
        } else {
            responseContent.append("\r\nBODY FileUpload: " + data.getHttpDataType().name() + ": " + data
                    + "\r\n");
            if (data.getHttpDataType() == HttpDataType.FileUpload) {
                FileUpload fileUpload = (FileUpload) data;
                if (fileUpload.isCompleted()) {
                    if (fileUpload.length() < 10000) {
                        responseContent.append("\tContent of file\r\n");
                        try {
                            responseContent.append(fileUpload.getString(fileUpload.getCharset()));
                        } catch (IOException e1) {
                            // do nothing for the example
                            e1.printStackTrace();
                        }
                        responseContent.append("\r\n");
                    } else {
                        responseContent.append("\tFile too long to be printed out:" + fileUpload.length() + "\r\n");
                    }
                    // fileUpload.isInMemory();// tells if the file is in Memory
                    // or on File
                    // fileUpload.renameTo(dest); // enable to move into another
                    // File dest
                    // decoder.removeFileUploadFromClean(fileUpload); //remove
                    // the File of to delete file
                } else {
                    responseContent.append("\tFile to be continued but should not!\r\n");
                }
            }
        }
    }

    private void writeResponse(Channel channel) {
        // Convert the response content to a ChannelBuffer.
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

    void writeMenu(ChannelHandlerContext ctx, String url) {
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
            ctx.writeAndFlush(response);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.WARNING, responseContent.toString(), cause);
        ctx.channel().close();
    }
}
