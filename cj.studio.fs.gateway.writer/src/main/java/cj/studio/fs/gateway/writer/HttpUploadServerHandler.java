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

import cj.studio.fs.gateway.writer.pages.UploadPage;
import cj.studio.fs.indexer.*;
import cj.studio.fs.indexer.IPage;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
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

    String dir = "/";
    IServiceProvider site;
    FileSystem fileSystem;
    Map<String, IPage> pages;

    public HttpUploadServerHandler(IServiceProvider site) {
        this.site = site;
        fileSystem = (FileSystem) site.getService("$.fileSystem");
        pages = (Map<String, IPage>) site.getService("$.pages");
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
            IPageContext context = new DefaultPageContext(site, fileSystem, ctx,dir, request, mimes);
            if (context.isResource()) {
                try {
                    context.writeResource(request.uri(), null);
                } catch (Exception e) {
                    sendError(ctx, HttpResponseStatus.parseLine(String.format("500 %s", e.getMessage())));
                    e.printStackTrace();
                } finally {
                    if (context != null) {
                        context.dispose();
                    }
                }
                return;
            }
            boolean isLoginService=request.uri().startsWith("/public/login.service");
            if (isLoginService||!isLogin(request)) {
                IPage page = Utils.getPage("/public/login.html", pages);
                if(request.uri().startsWith("/public/login.html")){
                    try {
                        context.writeResource(request.uri(), null);
                    } catch (Exception e) {
                        sendError(ctx, HttpResponseStatus.parseLine(String.format("500 %s", e.getMessage())));
                        e.printStackTrace();
                    } finally {
                        if (context != null) {
                            context.dispose();
                        }
                    }
                    return;
                }else{
                    if(!isLoginService){
                        context.redirect(page.path());
                        return;
                    }
                }

                try {
                    page.doService(context);
                } catch (Exception e) {
                    sendError(ctx, HttpResponseStatus.parseLine(String.format("500 %s", e.getMessage())));
                    e.printStackTrace();
                } finally {
                    if (context != null) {
                        context.dispose();
                    }
                }
                return;
            }
            if ("/".equals(request.uri())) {
                context.redirect("/mg/index.html");
                return;
            }

            IPage page = Utils.getPage(request.uri(), pages);
            if(!(page instanceof UploadPage)) {
                if (page != null) {
                    try {
                        page.doService(context);
                        return;
                    } catch (Exception e) {
                        sendError(ctx, HttpResponseStatus.parseLine(String.format("500 %s", e.getMessage())));
                        e.printStackTrace();
                        return;
                    } finally {
                        if (context != null) {
                            context.dispose();
                        }
                    }
                } else {//上传放过去
                    sendError(ctx, NOT_FOUND);
                    return;
                }
            }
            //其下处理上传
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
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            dir = decoder.parameters().get("dir").get(0);
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
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("{ \"code\": 200, \"data\": \"%s\" }", dir));
                    writeResponse(ctx.channel(), sb);
                    readingChunks = false;
                    reset();
                }
            }
        }
    }

    private boolean isLogin(HttpRequest request) {
        String cookieSeq = request.headers().getAndConvert(HttpHeaderNames.COOKIE);
        String name = Utils.getFileName(request.uri());
        if ((cookieSeq == null || cookieSeq.length() == 0) && (name.lastIndexOf(".") < 0 || name.endsWith(".html"))) {
            return false;
        }
        return true;
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
        if (data.getHttpDataType() != HttpDataType.FileUpload) {
            return;
        }
        FileUpload fileUpload = (FileUpload) data;
        if (fileUpload.isCompleted()) {
            String fileName = fileUpload.getFilename();
            String currDir = dir;
            if (!currDir.endsWith("/")) {
                currDir = currDir + "/";
            }
            String path = String.format("%s%s", currDir, fileName);
            IFileWriter writer = null;
            try {
                writer = fileSystem.openWriter(path);
                if (fileUpload.isInMemory()) {
                    byte[] buf = fileUpload.get();
                    writer.write(buf);
                } else {
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
        }

    }

    private void writeResponse(Channel channel, StringBuilder responseContent) {
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


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }
}
