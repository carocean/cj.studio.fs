package cj.studio.fs.indexer;

import java.io.IOException;

public interface IPage {
    String path();
    void doService(IPageContext context) throws IOException;
}
