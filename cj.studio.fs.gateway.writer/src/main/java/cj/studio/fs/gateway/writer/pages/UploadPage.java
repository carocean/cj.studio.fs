package cj.studio.fs.gateway.writer.pages;

import cj.studio.fs.indexer.IPage;
import cj.studio.fs.indexer.IPageContext;

import java.io.IOException;

public class UploadPage implements IPage {
    @Override
    public String path() {
        return "/upload/uploader.service";
    }

    @Override
    public void doService(IPageContext context) throws IOException {

    }
}
