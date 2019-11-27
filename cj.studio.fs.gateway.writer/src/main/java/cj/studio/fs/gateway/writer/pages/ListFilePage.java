package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.*;
import cj.studio.fs.indexer.util.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ListFilePage implements IPage {
    private final IServerConfig config;


    public ListFilePage(IServerConfig config) {
        this.config=config;
    }

    @Override
    public String path() {
        return "/fs/list/";
    }

    @Override
    public void doService(IPageContext context) {
        Map<String, String> map = context.cookies();
        String appid = map.get("App-ID");
        String token = map.get("Access-Token");
        Map<String, List<String>> params=Utils.parameters(context.request());
        String dirPath = "";
        if(params==null||params.isEmpty()){
            throw new RuntimeException("缺少参数dir");
        }
        dirPath = params.get("dir").get(0);
        FileSystem fileSystem = context.fileSystem();
        String qs = String.format("?App-ID=%s&Access-Token=%s", appid, token);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        String parentDir = "";
        if ("/".equals(dirPath)) {
            parentDir = "/";
        } else {
            parentDir = dirPath;
            while (parentDir.endsWith("/")) {
                parentDir = parentDir.substring(0, parentDir.length() - 1);
            }
            int pos = parentDir.lastIndexOf("/");
            parentDir = parentDir.substring(0, pos);
            if ("".endsWith(parentDir)) {
                parentDir = "/";
            }
        }
        StringBuilder buf = new StringBuilder()
                .append(String.format("<div currentDir='%s'>\r\n", dirPath))
                .append("<h3>Listing of: ")
                .append(dirPath)
                .append("</h3>\r\n")
                .append("<ul>")
                .append(String.format("<li  style='list-style: none;'><a parentDir='%s' style='text-decoration: none; ' href='#'>..</a></li>\r\n", parentDir));

        for (String name : fileSystem.listDir(dirPath)) {
            buf.append("<li type='d' style='list-style: none;'><a dir='" + name + "' style='text-decoration: none; ' href=\"")
                    .append(name)
                    .append(qs)
                    .append("\" path=\""+name+"\" >")
                    .append("+&nbsp;&nbsp;")
                    .append(name)
                    .append("</a><span op='del' class='del'>X</span></li>\r\n");
        }
        for (String name : fileSystem.listFile(dirPath)) {
            String len = getFileLength(fileSystem, name);
            String readerUrl=String.format("%s%s",config.writerReaderServer(),name);
            buf.append("<li type='f' style='list-style: none;'><a file style='text-decoration: none; ' href=\"")
                    .append(readerUrl)
                    .append(qs)
                    .append("\" path=\""+name+"\" >")
                    .append("-&nbsp;&nbsp;")
                    .append(name)
                    .append("</a><span style='color:grey;padding-left:10px;font-size:10px;'>" + len + "</span><span op='del' class='del'>X</span></li>\r\n");
        }

        buf.append("</ul></div>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        context.ctx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendListing(ChannelHandlerContext ctx, FileSystem fileSystem, String dirPath, String appid, String token) {

    }

    private String getFileLength(FileSystem fileSystem, String name) {
        IFileReader reader = null;
        long len = 0;
        try {
            reader = fileSystem.openReader(name);
            len = reader.length();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return Utils.getPrintSize(len);
    }
}
