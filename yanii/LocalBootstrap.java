package nro;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class LocalBootstrap {
    private static final String PUBLIC_HOST = publicHost();
    public static final String SERVER_LIST = ("[{\"nameServer\":\"NgocRongLive\",\"address\":\"" + PUBLIC_HOST + "\",\"port\":14445,"
                + "\"language\":0,\"typeSv\":0,\"homeUrl\":\"\",\"logoUrl\":\"\","
                + "\"isUnLockOptionTemplate\":0,\"listPorata\":\"921\",\"typeReadHpMod\":0,"
                + "\"typeGetParam\":0,\"isPaintRuby\":1}]");

    private static String publicHost() {
        String host = System.getenv("NRO_PUBLIC_HOST");
        if (host == null || host.isBlank()) return "127.0.0.1";
        if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")) throw new IllegalArgumentException("Invalid NRO_PUBLIC_HOST");
        return host;
    }
    public static InetAddress gameBindAddress() {
        try {
            String bind = System.getenv("NRO_GAME_BIND");
            if (bind == null || bind.isBlank()) return InetAddress.getLoopbackAddress();
            if (!bind.equals("0.0.0.0") && !bind.equals("127.0.0.1")) throw new IllegalArgumentException("Invalid NRO_GAME_BIND");
            return InetAddress.getByName(bind);
        } catch (java.net.UnknownHostException e) { throw new IllegalArgumentException(e); }
    }
    public static void start() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(gameBindAddress(), 14446), 0);
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
