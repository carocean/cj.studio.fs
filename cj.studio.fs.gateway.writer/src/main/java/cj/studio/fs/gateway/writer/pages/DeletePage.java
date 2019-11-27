package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.IPage;
import cj.studio.fs.indexer.IPageContext;
import cj.studio.fs.indexer.util.Utils;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DeletePage implements IPage {
    @Override
    public String path() {
        return "/del/file/";
    }

    @Override
    public void doService(IPageContext context) throws IOException {
        Map<String, List<String>> params = Utils.parameters(context.request());
        if (params == null) {
            throw new IOException("缺少参数");
        }
        if (params.get("type") == null || params.get("type").isEmpty()) {
            throw new IOException("缺少参数type");
        }
        String type = params.get("type").get(0);
        if (params.get("path") == null || params.get("path").isEmpty()) {
            throw new IOException("缺少参数path");
        }
        String path = params.get("path").get(0);
        path = Utils.getPathWithoutQuerystring(path);
        try {
            switch (type) {
                case "d":
                    context.fileSystem().deleteDir(path);
                    break;
                case "f":
                    context.fileSystem().deleteFile(path);
                    break;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{ \"state\": 200, \"message\": \"%s\" }", context.currentDir()));
            context.writeResponse(context.ctx().channel(), sb);
        } catch (Exception e) {
            e.printStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{ \"state\": 500, \"message\": \"%s\" }", e));
            context.writeResponse(context.ctx().channel(), sb);
        }

    }
}
