package nro;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class LocalBootstrap {
    public static final String SERVER_LIST = ("[{\"nameServer\":\"NgocRongLive\",\"address\":\"127.0.0.1\",\"port\":14445,"
                + "\"language\":0,\"typeSv\":0,\"homeUrl\":\"\",\"logoUrl\":\"\","
                + "\"isUnLockOptionTemplate\":0,\"listPorata\":\"921\",\"typeReadHpMod\":0,"
                + "\"typeGetParam\":0,\"isPaintRuby\":1}]");

    public static void start() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 14446), 0);
            byte[] body = SERVER_LIST.getBytes(StandardCharsets.UTF_8);
            http.createContext("/IPLOCAL", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
            });
            http.start();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot start local client bootstrap on port 14446", e);
        }
    }
}
