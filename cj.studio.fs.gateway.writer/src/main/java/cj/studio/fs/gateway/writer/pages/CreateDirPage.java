package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.IPage;
import cj.studio.fs.indexer.IPageContext;
import cj.studio.fs.indexer.util.Utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CreateDirPage implements IPage {
    @Override
    public String path() {
        return "/dir/create.service";
    }

    @Override
    public void doService(IPageContext context) throws IOException {
        Map<String, List<String>> params= Utils.parameters(context.request());
        String dir = "";
        if(params==null||params.isEmpty()){
            throw new RuntimeException("缺少参数dir");
        }
        dir = params.get("dir").get(0);
        try {
            context.fileSystem().mkdirs(dir);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{ \"state\": 200, \"message\": \"%s\" }", dir));
            context.writeResponse(context.ctx().channel(), sb);
        } catch (Exception e) {
            e.printStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{ \"state\": 500, \"message\": \"%s\" }", e));
            context.writeResponse(context.ctx().channel(), sb);
        }
    }

}
