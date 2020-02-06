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
package cj.studio.fs.gateway;

import cj.studio.fs.indexer.*;
import cj.studio.fs.indexer.util.Utils;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;

import javax.activation.MimetypesFileTypeMap;
import java.io.IOException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * A simple handler that serves incoming HTTP requests to send their respective
 * HTTP responses.  It also implements {@code 'If-Modified-Since'} header to
 * take advantage of browser cache, as described in
 * <a href="http://tools.ietf.org/html/rfc2616#section-14.25">RFC 2616</a>.
 *
 * <h3>How Browser Caching Works</h3>
 * <p>
 * Web browser caching works with HTTP headers as illustrated by the following
 * sample:
 * <ol>
 * <li>Request #1 returns the content of {@code /file1.txt}.</li>
 * <li>Contents of {@code /file1.txt} is cached by the browser.</li>
 * <li>Request #2 for {@code /file1.txt} does return the contents of the
 *     file again. Rather, a 304 Not Modified is returned. This tells the
 *     browser to use the contents stored in its cache.</li>
 * <li>The server knows the file has not been modified because the
 *     {@code If-Modified-Since} date is the same as the file's last
 *     modified date.</li>
 * </ol>
 *
 * <pre>
 * Request #1 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 *
 * Response #1 Headers
 * ===================
 * HTTP/1.1 200 OK
 * Date:               Tue, 01 Mar 2011 22:44:26 GMT
 * Last-Modified:      Wed, 30 Jun 2010 21:36:48 GMT
 * Expires:            Tue, 01 Mar 2012 22:44:26 GMT
 * Cache-Control:      private, max-age=31536000
 *
 * Request #2 Headers
 * ===================
 * GET /file1.txt HTTP/1.1
 * If-Modified-Since:  Wed, 30 Jun 2010 21:36:48 GMT
 *
 * Response #2 Headers
 * ===================
 * HTTP/1.1 304 Not Modified
 * Date:               Tue, 01 Mar 2011 22:44:28 GMT
 *
 * </pre>
 */
public class HttpStaticFileServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final Logger logger = Logger.getLogger(HttpStaticFileServerHandler.class.getName());
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;
    FileSystem fileSystem;
    IServerConfig config;
    IAccessController controller;
    IFileReader reader = null;
    HttpRequest request;


    public HttpStaticFileServerHandler(IServiceProvider site) {
        this.config = (IServerConfig) site.getService("$.config");
        fileSystem = (FileSystem) site.getService("$.fileSystem");
        controller = (IAccessController) site.getService("$.accessController");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, HttpObject obj) throws Exception {
        if (obj instanceof HttpRequest) {
            this.request=(HttpRequest)obj;
            doHttpRequest(ctx, request);
            return;
        }
        if (obj instanceof LastHttpContent) {
            ChannelFuture lastContentFuture;
            if(reader!=null) {
                byte[] buf = new byte[config.chunkedSize()];
                int readlen = 0;
                while (true) {
                    readlen = reader.read(buf, 0, buf.length);
                    if (readlen < 0) {
                        break;
                    }
                    ByteBuf bb = ctx.alloc().buffer(readlen);
                    bb.writeBytes(buf, 0, readlen);
                    HttpContent content = new DefaultHttpContent(bb);
                    ctx.writeAndFlush(content);
                }
            }
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!HttpHeaderUtil.isKeepAlive(request)) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
            this.request=null;
        }

    }

    private void doHttpRequest(ChannelHandlerContext ctx, HttpRequest request) throws IOException, ParseException {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        if (request.method() != GET) {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        String uri = request.uri();
        uri = URLDecoder.decode(uri, "utf-8");
        final String path = Utils.getPathWithoutQuerystring(uri);
        if (path == null) {
            sendError(ctx, FORBIDDEN);
            return;
        }
        Map<String, String> params = null;
        String accessToken = "";
        if (config.rbacForceToken()) {
            params = Utils.parseQueryString(uri);
            String cookieSeq = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
            Map<String, String> map = new HashMap<>();
            if (cookieSeq != null) {
                Set<Cookie> cookies = ServerCookieDecoder.decode(cookieSeq);
                for (Cookie cookie : cookies) {
                    map.put(cookie.name(), cookie.value());
                }
            }
            accessToken =map.get("accessToken");

        }

        if (!fileSystem.isFile(path)) {
            String list = params == null ? null : params.get("list");
            if("/".equals(path)&&!Utils.isEmpty(list)){
                listDir(ctx, list,accessToken);
                return;
            }
            sendError(ctx, FORBIDDEN);
            return;
        }
        try {
            if (config.rbacForceToken() && !controller.hasReadRights(uri, accessToken)) {
                sendError(ctx, FORBIDDEN);
                return;
            }
        } catch (AccessTokenExpiredException e) {
            logger.error(e);
            sendError(ctx, HttpResponseStatus.parseLine("1002 AccessToken is Expired"));
            return;
        } catch (Throwable e) {
            logger.error(e);
            sendError(ctx, EXPECTATION_FAILED);
        }

        try {
            reader = fileSystem.openReader(path);
        } catch (Exception e) {
            logger.error(e);
            sendError(ctx, NOT_FOUND);
            return;
        }
//        // Cache Validation
        String ifModifiedSince = request.headers().getAndConvert(IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = fileSystem.lastModified(path) / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified(ctx);
                return;
            }
        }

        long fileLength = 0;
        try {
            fileLength = reader.length();
        } catch (Exception e) {
            logger.error(e);
            HttpResponseStatus status = HttpResponseStatus.parseLine("500 Fail to fetch file length.");
            sendError(ctx, status);
            return;
        }
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpHeaderUtil.setContentLength(response, fileLength);
        setContentTypeHeader(response, Utils.getFileName(path));
        setDateAndCacheHeaders(response, path);
        if (HttpHeaderUtil.isKeepAlive(request)) {
            response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.writeAndFlush(response);


    }

    private void listDir(ChannelHandlerContext ctx, String path,String accessToken) {
        if (!fileSystem.existsDir(path)) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        if(config.rbacForceToken()&&!controller.hasListRights(path,accessToken)){
            sendError(ctx, FORBIDDEN);
            return;
        }

        Map<String, String> data = new HashMap<>();
        List<String> dirs=fileSystem.listDir(path);
        List<String> files=fileSystem.listFile(path);
        for (String d : dirs) {
            data.put(d, "d");
        }
        for (String f : files) {
            data.put(f, "f");
        }
        ResponseClient rc = new ResponseClient(200,"ok",new Gson().toJson(data));
        IPageContext context= new DefaultPageContext(fileSystem,ctx,request,null);
        StringBuilder builder = new StringBuilder();
        builder.append(new Gson().toJson(rc));
        context.writeResponse(ctx.channel(),builder);
        context.dispose();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
        logger.error(cause);
    }


    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     *
     * @param ctx Context
     */
    private void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param response HTTP response
     */
    private void setDateHeader(FullHttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response HTTP response
     * @param file     file to extract content type
     */
    private void setDateAndCacheHeaders(HttpResponse response, String file) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);

        response.headers().set(
                LAST_MODIFIED, dateFormatter.format(new Date(fileSystem.lastModified(file))));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response HTTP response
     * @param fileName file to extract content type
     */
    private void setContentTypeHeader(HttpResponse response, String fileName) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(fileName));
    }
}
