package cj.studio.fs.indexer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.HttpRequest;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface IPageContext {
    void dispose();

    void writeDoc(Document document);

    Document html(String path) throws IOException;


    void writeResource(String path, Set<Cookie> cookies) throws IOException;

    String currentDir();

    ChannelHandlerContext ctx();

    FileSystem fileSystem();

    HttpRequest request();

    IServiceProvider site();

    Map<String, String> cookies();

    void redirect( String path);

    boolean isDoc();

    boolean isResource();

    String relativePath();

    void writeResponse(Channel channel, StringBuilder sb);

    String path();

}
