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

import cj.studio.fs.indexer.IServerConfig;
import cj.studio.fs.indexer.IServiceProvider;
import cj.studio.fs.indexer.util.Utils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;
import org.jsoup.internal.StringUtil;

public final class HttpStaticFileServer {

    static Logger logger = Logger.getLogger(HttpStaticFileServer.class);

    public void start(IServiceProvider site) throws Exception {
        IServerConfig config=(IServerConfig) site.getService("$.config");
        String ip=config.readerServerIP();
        int port=config.readerServerPort();
        boolean SSL=config.readerServerSSL();
        int workThreadCount=config.readerServerWorkThreadCount();
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContext.newServerContext(SslProvider.JDK, ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workThreadCount);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new HttpStaticFileServerInitializer(sslCtx,site));

            Channel ch = null;
            if (Utils.isEmpty(ip)) {
                ch = b.bind(port).sync().channel();
            } else {
                ch = b.bind(ip, port).sync().channel();
            }
            logger.debug("Open your web browser and navigate to " +
                    (SSL ? "https" : "http") + "://" + ip + ":" + port + '/');

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
