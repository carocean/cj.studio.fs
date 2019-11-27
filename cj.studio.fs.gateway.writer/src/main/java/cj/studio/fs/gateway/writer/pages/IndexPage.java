package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.IPage;
import cj.studio.fs.indexer.IPageContext;

import java.io.IOException;

public class IndexPage implements IPage {
    @Override
    public String path() {
        return "/mg/index.html";
    }

    @Override
    public void doService(IPageContext context) throws IOException {
        context.writeResource(context.path(),null);
    }
}
