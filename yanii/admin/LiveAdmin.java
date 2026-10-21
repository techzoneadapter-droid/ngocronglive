package nro.admin;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.player.Player;
import nro.server.Client;

/** Local owner console. Grants are committed before a character is loaded, never over a live save. */
public final class LiveAdmin {
    private static final int PORT=14447;
    private static final Object LOGIN_LOCK=new Object();
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<String,Auth> sessions=new ConcurrentHashMap<>();
    private static final Deque<Long> failures=new ArrayDeque<>();
    private static volatile JSONArray events=new JSONArray();
    private static String password;
    private record Auth(String csrf,long expires) {}
    private static String random(){byte[] b=new byte[24];RANDOM.nextBytes(b);return HexFormat.of().formatHex(b);}
    public static void start() throws Exception {
        password=System.getenv("NRO_ADMIN_PASSWORD");
        if(password==null || password.length()<16) throw new IllegalStateException("NRO_ADMIN_PASSWORD must contain at least 16 characters");
        try(Connection c=DBHika.getConnection();InputStream in=LiveAdmin.class.getResourceAsStream("/live-admin/schema.sql")){
            if(in==null)throw new IOException("Admin schema missing");
            for(String sql:new String(in.readAllBytes(),StandardCharsets.UTF_8).split(";")) if(!sql.isBlank())try(Statement s=c.createStatement()){s.execute(sql);}
        }
        reloadEvents();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",PORT),16);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/",LiveAdmin::handle);server.start();
        Thread stopWatcher=new Thread(()->{
            java.nio.file.Path stop=java.nio.file.Path.of("../local-data/stop.request");
            try { while(!java.nio.file.Files.exists(stop))Thread.sleep(500); nro.server.ServerManager.gI().close(0); }
            catch(Exception e){System.err.println("Owner stop failed: "+e.getClass().getSimpleName());}
        },"owner-stop");stopWatcher.setDaemon(true);stopWatcher.start();
        System.out.println("Owner console: http://127.0.0.1:"+PORT);
    }
    private static void handle(HttpExchange x) throws IOException {
        try {
            String path=x.getRequestURI().getPath();
            x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            x.getResponseHeaders().set("X-Frame-Options","DENY");
            x.getResponseHeaders().set("Cache-Control","no-store");
            x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; form-action 'self'");
            String host=x.getRequestHeaders().getFirst("Host");
            if(!("127.0.0.1:"+PORT).equals(host) && !("localhost:"+PORT).equals(host)) {send(x,403,new JSONObject().put("error","Host không hợp lệ"));return;}
            if(!path.startsWith("/api/")){staticFile(x,path);return;}
            if(!x.getRequestMethod().equals("GET")&&!x.getRequestMethod().equals("POST"))throw new IllegalArgumentException("Phương thức không được hỗ trợ");
            JSONObject body=new JSONObject();
            if(x.getRequestMethod().equals("POST")){
                String origin=x.getRequestHeaders().getFirst("Origin");
                if(origin!=null&&!origin.equals("http://"+host))throw new SecurityException("Origin không hợp lệ");
                String type=x.getRequestHeaders().getFirst("Content-Type");
                if(type==null||!type.startsWith("application/json"))throw new SecurityException("Yêu cầu JSON");
                byte[] bytes=x.getRequestBody().readNBytes(65537);
                if(bytes.length>65536)throw new IllegalArgumentException("Yêu cầu quá lớn");
                body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            }
            if(path.equals("/api/login")&&x.getRequestMethod().equals("POST")){login(x,body);return;}
            Auth auth=authenticate(x);
            if(auth==null){send(x,401,new JSONObject().put("error","Vui lòng đăng nhập quản trị"));return;}
            if(x.getRequestMethod().equals("POST")&&!auth.csrf.equals(x.getRequestHeaders().getFirst("X-CSRF")))throw new SecurityException("CSRF không hợp lệ");
            if(path.equals("/api/session")){send(x,200,new JSONObject().put("csrf",auth.csrf));return;}
            if(path.equals("/api/logout")){requirePost(x);String cookie=x.getRequestHeaders().getFirst("Cookie");if(cookie!=null)for(String s:cookie.split(";"))if(s.trim().startsWith("nro_admin="))sessions.remove(s.trim().substring(10));x.getResponseHeaders().add("Set-Cookie","nro_admin=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");send(x,200,new JSONObject().put("ok",true));return;}
            if(path.equals("/api/state")&&x.getRequestMethod().equals("GET")){send(x,200,state());return;}
            requirePost(x);
            JSONObject result;
            try(Connection c=DBHika.getConnection()){
                c.setAutoCommit(false);
                try {result=mutate(c,path,body);c.commit();}catch(Exception e){c.rollback();throw e;}
            }
            if(path.equals("/api/event"))reloadEvents();
            if(path.equals("/api/account/ban")&&body.getBoolean("ban")){
                int id=body.getInt("account_id");
                for(Player p:Client.gI().getPlayers())if(p.getSession().userId==id)Client.gI().kickSession(p.getSession());
            }
            send(x,200,result);
        }catch(SecurityException e){send(x,403,new JSONObject().put("error",e.getMessage()));}
        catch(IllegalArgumentException|JSONException e){send(x,400,new JSONObject().put("error",e.getMessage()));}
        catch(SQLException e){System.err.println("Admin database operation failed: SQLState="+e.getSQLState());send(x,409,new JSONObject().put("error","Thao tác chưa được thực hiện. Kiểm tra dữ liệu trùng, tài khoản và thử lại với cùng mã yêu cầu."));}
        catch(Exception e){System.err.println("Admin operation failed: "+e.getClass().getSimpleName());send(x,500,new JSONObject().put("error","Không hoàn thành thao tác; xem log server."));}
        finally{x.close();}
    }
    private static void requirePost(HttpExchange x){if(!x.getRequestMethod().equals("POST"))throw new IllegalArgumentException("Yêu cầu POST");}
    private static void staticFile(HttpExchange x,String path)throws IOException{
        if(!x.getRequestMethod().equals("GET")){x.sendResponseHeaders(405,-1);return;}
        String file=switch(path){case "/"->"index.html";case "/admin.js"->"admin.js";case "/admin.css"->"admin.css";default->null;};
        if(file==null){x.sendResponseHeaders(404,-1);return;}
        try(InputStream in=LiveAdmin.class.getResourceAsStream("/live-admin/"+file)){
            if(in==null){x.sendResponseHeaders(404,-1);return;}
            byte[] b=in.readAllBytes();x.getResponseHeaders().set("Content-Type",file.endsWith("html")?"text/html; charset=utf-8":file.endsWith("js")?"text/javascript; charset=utf-8":"text/css; charset=utf-8");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);
        }
    }
    private static void send(HttpExchange x,int status,JSONObject data)throws IOException{byte[] b=data.toString().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);}
    private static void login(HttpExchange x,JSONObject b)throws IOException{
        synchronized(failures){long cutoff=System.currentTimeMillis()-60000;while(!failures.isEmpty()&&failures.peek()<cutoff)failures.remove();if(failures.size()>=5){send(x,429,new JSONObject().put("error","Thử lại sau 1 phút"));return;}}
        if(!MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),b.optString("password").getBytes(StandardCharsets.UTF_8))){synchronized(failures){failures.add(System.currentTimeMillis());}send(x,401,new JSONObject().put("error","Mật khẩu quản trị không đúng"));return;}
        sessions.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());
        String token=random(),csrf=random();sessions.put(token,new Auth(csrf,System.currentTimeMillis()+8*3600000L));
        x.getResponseHeaders().add("Set-Cookie","nro_admin="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");send(x,200,new JSONObject().put("csrf",csrf));
    }
    private static Auth authenticate(HttpExchange x){String cookies=x.getRequestHeaders().getFirst("Cookie");if(cookies!=null)for(String s:cookies.split(";"))if(s.trim().startsWith("nro_admin=")){Auth a=sessions.get(s.trim().substring(10));if(a!=null&&a.expires>System.currentTimeMillis())return a;}return null;}
    private static PreparedStatement stmt(Connection c,String sql,Object... values)throws SQLException{PreparedStatement p=c.prepareStatement(sql);for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;}
    private static int exec(Connection c,String sql,Object... values)throws SQLException{try(PreparedStatement p=stmt(c,sql,values)){return p.executeUpdate();}}
    private static JSONArray rows(Connection c,String sql,Object... values)throws SQLException{JSONArray a=new JSONArray();try(PreparedStatement p=stmt(c,sql,values);ResultSet r=p.executeQuery()){ResultSetMetaData m=r.getMetaData();while(r.next()){JSONObject o=new JSONObject();for(int i=1;i<=m.getColumnCount();i++)o.put(m.getColumnLabel(i),r.getObject(i)==null?JSONObject.NULL:(Set.of("enabled","ban").contains(m.getColumnLabel(i))?r.getBoolean(i):r.getObject(i)));a.put(o);}}return a;}
    private static void audit(Connection c,String action,String ref,String detail)throws SQLException{exec(c,"INSERT INTO live_admin_audit(action,reference_id,detail) VALUES(?,?,?)",action,ref,detail);}
    private static String text(JSONObject b,String key,int max){String s=b.getString(key).trim();if(s.isEmpty()||s.length()>max)throw new IllegalArgumentException("Trường "+key+" không hợp lệ");return s;}
    private static long number(JSONObject b,String key,long min,long max){String s=b.get(key).toString();if(!s.matches("-?[0-9]{1,16}"))throw new IllegalArgumentException(key+" phải là số nguyên");long n=Long.parseLong(s);if(n<min||n>max)throw new IllegalArgumentException(key+" ngoài giới hạn");return n;}
    private static String requestId(JSONObject b){String s=text(b,"request_id",64);if(!s.matches("[a-zA-Z0-9_-]{8,64}"))throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");return s;}
    private static int account(Connection c,JSONObject b)throws SQLException{int id=(int)number(b,"account_id",1,Integer.MAX_VALUE);if(rows(c,"SELECT id FROM account WHERE id=?",id).isEmpty())throw new IllegalArgumentException("Không tìm thấy tài khoản");return id;}
    private static JSONObject reward(Connection c,JSONObject input)throws SQLException{
        JSONObject b=new JSONObject(input.toString());String kind=b.getString("kind");
        if(kind.equals("CURRENCY")){String currency=b.getString("currency");if(!Set.of("gold","gem","ruby").contains(currency))throw new IllegalArgumentException("Loại tiền không hợp lệ");long n=number(b,"amount",currency.equals("gold")?-nro.models.player.Inventory.LIMIT_GOLD:-Integer.MAX_VALUE,currency.equals("gold")?nro.models.player.Inventory.LIMIT_GOLD:Integer.MAX_VALUE);if(n==0)throw new IllegalArgumentException("Số lượng phải khác 0");String mode=b.optString("mode","ADD");if(!Set.of("ADD","AT_LEAST").contains(mode)||(mode.equals("AT_LEAST")&&n<1))throw new IllegalArgumentException("Chế độ số dư không hợp lệ");return new JSONObject().put("kind",kind).put("currency",currency).put("amount",n).put("mode",mode);}
        if(kind.equals("ITEM")){
            int id=(int)number(b,"item_id",0,32767);if(rows(c,"SELECT id FROM item_template WHERE id=?",id).isEmpty())throw new IllegalArgumentException("ID vật phẩm không tồn tại");
            long quantity=number(b,"quantity",1,9999);JSONArray opts=b.optJSONArray("options");if(opts==null)opts=new JSONArray();if(opts.length()>32)throw new IllegalArgumentException("Tối đa 32 chỉ số");
            for(int i=0;i<opts.length();i++){JSONArray o=opts.getJSONArray(i);if(o.length()!=2)throw new IllegalArgumentException("Chỉ số dạng [id,giá trị]");int oid=o.getInt(0),param=o.getInt(1);if(param<0||param>1000000||rows(c,"SELECT id FROM item_option_template WHERE id=?",oid).isEmpty())throw new IllegalArgumentException("Chỉ số vật phẩm không hợp lệ");}
            return new JSONObject().put("kind",kind).put("item_id",id).put("quantity",quantity).put("options",opts);
        }
        if(kind.equals("POINTS")) {
            int limit=(int)number(b,"limit_index",0,nro.power.PowerLimitManager.getInstance().getPowers().size()-1);
            long max=nro.power.PowerLimitManager.getInstance().get(limit).getPower();
            return new JSONObject().put("kind",kind).put("limit_index",limit).put("power",number(b,"power",0,max))
                .put("potential",number(b,"potential",0,9000000000000L)).put("max_base",b.optBoolean("max_base"));
        }
        if(kind.equals("BUNDLE")) {
            if(b.has("package_id")) {
                JSONArray saved=rows(c,"SELECT reward FROM live_admin_packages WHERE id=?",text(b,"package_id",64));
                if(saved.isEmpty())throw new IllegalArgumentException("Không tìm thấy gói đã lưu");
                return reward(c,new JSONObject(saved.getJSONObject(0).getString("reward")));
            }
            JSONArray parts=RewardCatalog.expand(b),checked=new JSONArray();
            for(int i=0;i<parts.length();i++){JSONObject part=reward(c,parts.getJSONObject(i));if(part.optLong("amount",1)<=0)throw new IllegalArgumentException("Gói quà không được trừ tài nguyên");checked.put(part);}
            return new JSONObject().put("kind",kind).put("rewards",checked);
        }
        throw new IllegalArgumentException("Loại phần thưởng không hợp lệ");
    }
    private static JSONObject queue(Connection c,String id,int account,JSONObject reward,String reason)throws SQLException{
        JSONArray existing=rows(c,"SELECT account_id,kind,payload,reason,status FROM live_admin_actions WHERE id=?",id);
        if(!existing.isEmpty()){
            JSONObject old=existing.getJSONObject(0);
            if(old.getInt("account_id")!=account||!old.getString("reason").equals(reason)||!new JSONObject(old.getString("payload")).similar(reward))throw new IllegalArgumentException("Mã yêu cầu đã dùng cho thao tác khác");
            return new JSONObject().put("id",id).put("status",old.getString("status")).put("duplicate",true);
        }
        if(reward.getString("kind").equals("BUNDLE")) {
            JSONArray parts=reward.getJSONArray("rewards");
            if(id.length()>56)throw new IllegalArgumentException("Mã yêu cầu gói quà quá dài");
            exec(c,"INSERT INTO live_admin_actions(id,account_id,kind,payload,reason,status) VALUES(?,?,?,?,?,'EXPANDED')",id,account,"BUNDLE",reward.toString(),reason);
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO live_admin_actions(id,account_id,kind,payload,reason) VALUES(?,?,?,?,?)")) {
                for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);ps.setString(1,id+"_"+String.format(java.util.Locale.ROOT,"%04d",i));ps.setInt(2,account);ps.setString(3,part.getString("kind"));ps.setString(4,part.toString());ps.setString(5,reason);ps.addBatch();}ps.executeBatch();
            }
            audit(c,"QUEUE_BUNDLE",id,"account="+account+"; parts="+parts.length()+"; "+reason);
            return new JSONObject().put("id",id).put("status","EXPANDED").put("count",parts.length());
        }
        exec(c,"INSERT INTO live_admin_actions(id,account_id,kind,payload,reason) VALUES(?,?,?,?,?)",id,account,reward.getString("kind"),reward.toString(),reason);
        audit(c,"QUEUE",id,"account="+account+"; "+reward+"; "+reason);
        return new JSONObject().put("id",id).put("status","PENDING");
    }
    private static JSONObject mutate(Connection c,String path,JSONObject b)throws Exception{
        switch(path){
        case "/api/account/create":{
            String user=text(b,"username",20),pass=text(b,"password",64);if(!user.matches("[a-zA-Z0-9_]{4,20}")||pass.length()<8)throw new IllegalArgumentException("Tên tài khoản 4–20 ký tự, mật khẩu từ 8 ký tự");
            exec(c,"INSERT INTO account(username,password,active,server_login,is_admin,ban) VALUES(?,?,1,1,0,0)",user,pass);audit(c,"CREATE_ACCOUNT",user,"Tạo tài khoản người chơi");return new JSONObject().put("ok",true);}
        case "/api/account/ban":{
            int id=account(c,b);boolean ban=b.getBoolean("ban");String reason=text(b,"reason",500);exec(c,"UPDATE account SET ban=? WHERE id=?",ban,id);audit(c,ban?"BAN":"UNBAN",String.valueOf(id),reason);return new JSONObject().put("ok",true);}
        case "/api/account/password":{
            int id=account(c,b);String pass=text(b,"password",64);if(pass.length()<8)throw new IllegalArgumentException("Mật khẩu từ 8 ký tự");exec(c,"UPDATE account SET password=? WHERE id=?",pass,id);audit(c,"RESET_PASSWORD",String.valueOf(id),text(b,"reason",500));return new JSONObject().put("ok",true);}
        case "/api/reward/preview": {
            JSONObject r=reward(c,b.getJSONObject("reward"));JSONArray parts=r.optJSONArray("rewards");
            return new JSONObject().put("reward",r).put("count",parts==null?1:parts.length());
        }
        case "/api/package/save": {
            String id=requestId(b),name=text(b,"name",80);JSONObject r=reward(c,b.getJSONObject("reward"));
            if(!r.optString("kind").equals("BUNDLE"))throw new IllegalArgumentException("Cần một gói quà");
            exec(c,"INSERT INTO live_admin_packages(id,name,reward) VALUES(?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),reward=VALUES(reward)",id,name,r.toString());
            audit(c,"SAVE_PACKAGE",id,name);return new JSONObject().put("id",id);
        }
        case "/api/grant":return queue(c,requestId(b),account(c,b),reward(c,b.getJSONObject("reward")),text(b,"reason",500));
        case "/api/order/create":{
            String id=requestId(b);int user=account(c,b);long vnd=number(b,"amount_vnd",1000,100000000);JSONObject reward=reward(c,b.getJSONObject("reward"));if(!reward.getString("kind").equals("CURRENCY")||reward.getLong("amount")<=0)throw new IllegalArgumentException("Nạp thử cần phần thưởng tiền tệ dương");
            JSONArray old=rows(c,"SELECT * FROM live_admin_orders WHERE id=?",id);if(!old.isEmpty()){JSONObject o=old.getJSONObject(0);if(o.getInt("account_id")!=user||o.getLong("amount_vnd")!=vnd||!new JSONObject(o.getString("reward")).similar(reward))throw new IllegalArgumentException("Mã đơn đã dùng");return o;}
            exec(c,"INSERT INTO live_admin_orders(id,account_id,amount_vnd,reward) VALUES(?,?,?,?)",id,user,vnd,reward.toString());audit(c,"TEST_ORDER",id,"Mô phỏng, không nhận tiền thật");return new JSONObject().put("id",id).put("status","TEST_PENDING");}
        case "/api/order/confirm":{
            String id=text(b,"id",64);JSONArray a=rows(c,"SELECT * FROM live_admin_orders WHERE id=? FOR UPDATE",id);if(a.isEmpty())throw new IllegalArgumentException("Không tìm thấy đơn thử");JSONObject o=a.getJSONObject(0);
            if(o.getString("status").equals("TEST_CONFIRMED"))return new JSONObject().put("duplicate",true).put("status","TEST_CONFIRMED");
            queue(c,"order_"+id,o.getInt("account_id"),new JSONObject(o.getString("reward")),"Nạp thử "+id);
            exec(c,"UPDATE live_admin_orders SET status='TEST_CONFIRMED' WHERE id=?",id);audit(c,"TEST_CONFIRM",id,"Đã xếp hàng thưởng, không ghi doanh thu thật");return new JSONObject().put("status","TEST_CONFIRMED");}
        case "/api/event":{
            String id=requestId(b),title=text(b,"title",120);long start=number(b,"start_ms",0,9999999999999L),end=number(b,"end_ms",1,9999999999999L);int multiplier=(int)number(b,"multiplier",1,10);if(end<=start)throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu");
            exec(c,"INSERT INTO live_admin_events(id,title,start_ms,end_ms,multiplier,enabled) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),start_ms=VALUES(start_ms),end_ms=VALUES(end_ms),multiplier=VALUES(multiplier),enabled=VALUES(enabled)",id,title,start,end,multiplier,b.getBoolean("enabled"));audit(c,"EVENT",id,b.toString());return new JSONObject().put("id",id);}
        case "/api/code/create":{
            String code=text(b,"code",30).toUpperCase(Locale.ROOT);if(!code.matches("LIVE[A-Z0-9]{2,26}"))throw new IllegalArgumentException("Mã bắt đầu LIVE, chỉ gồm chữ và số, dài 6–30 ký tự");int max=(int)number(b,"max_uses",1,100000);long expires=number(b,"expires_ms",System.currentTimeMillis()+1000,9999999999999L);JSONObject r=reward(c,b.getJSONObject("reward"));if(r.optLong("amount",1)<1)throw new IllegalArgumentException("Giftcode không được trừ tiền");JSONArray allowed=b.optJSONArray("allowed_accounts");
            if(allowed!=null&&allowed.length()>500)throw new IllegalArgumentException("Tối đa 500 tài khoản nhận mã riêng");
            Set<Integer> recipients=new HashSet<>();
            if(allowed!=null)for(int i=0;i<allowed.length();i++)recipients.add(account(c,new JSONObject().put("account_id",allowed.getInt(i))));
            if(b.optBoolean("restricted")&&recipients.isEmpty())throw new IllegalArgumentException("Mã riêng cần chọn ít nhất một tài khoản");
            exec(c,"INSERT INTO live_admin_codes(code,max_uses,expires_ms,reward) VALUES(?,?,?,?)",code,max,expires,r.toString());
            for(int recipient:recipients)exec(c,"INSERT INTO live_admin_code_accounts(code,account_id) VALUES(?,?)",code,recipient);audit(c,"CREATE_CODE",code,"max="+max);return new JSONObject().put("code",code);}
        case "/api/code/redeem":return redeem(c,account(c,b),text(b,"code",30));
        case "/api/server/stop":{
            audit(c,"STOP","server","Owner requested save and shutdown");
            java.nio.file.Files.writeString(java.nio.file.Path.of("../local-data/stop.request"),"stop");
            return new JSONObject().put("ok",true);}
        case "/api/announcement":{
            String message=text(b,"message",250);for(Player p:Client.gI().getPlayers())nro.services.Service.getInstance().sendThongBaoOK(p,message);audit(c,"ANNOUNCE","all",message);return new JSONObject().put("ok",true);}
        default:throw new IllegalArgumentException("Chức năng không tồn tại");
        }
    }
    public static String redeem(int account,String code){
        try(Connection c=DBHika.getConnection()){c.setAutoCommit(false);try{redeem(c,account,code);c.commit();return "Đã nhận mã. Hãy đăng nhập lại để nhận chỉ số, tiền và quà. Rương đầy: lấy đồ ra rồi đăng nhập lại để nhận tiếp.";}catch(Exception e){c.rollback();return e instanceof IllegalArgumentException?e.getMessage():"Chưa nhận được mã; vui lòng thử lại.";}}
        catch(Exception e){return "Không kết nối được kho quà.";}
    }
    private static JSONObject redeem(Connection c,int account,String input)throws SQLException{
        String code=input.trim().toUpperCase(Locale.ROOT);JSONArray a=rows(c,"SELECT * FROM live_admin_codes WHERE code=? FOR UPDATE",code);if(a.isEmpty())throw new IllegalArgumentException("Mã không tồn tại");JSONObject b=a.getJSONObject(0);
        if(!rows(c,"SELECT account_id FROM live_admin_code_accounts WHERE code=? LIMIT 1",code).isEmpty()
            &&rows(c,"SELECT account_id FROM live_admin_code_accounts WHERE code=? AND account_id=?",code,account).isEmpty())throw new IllegalArgumentException("Mã này không dành cho tài khoản của bạn");
        if(!rows(c,"SELECT action_id FROM live_admin_redemptions WHERE code=? AND account_id=? FOR UPDATE",code,account).isEmpty())throw new IllegalArgumentException("Tài khoản đã dùng mã này");
        if(b.getLong("expires_ms")<=System.currentTimeMillis()||b.getInt("used")>=b.getInt("max_uses"))throw new IllegalArgumentException("Mã hết hạn hoặc hết lượt");
        String id="code_"+random();queue(c,id,account,new JSONObject(b.getString("reward")),"Giftcode "+code);exec(c,"INSERT INTO live_admin_redemptions(code,account_id,action_id) VALUES(?,?,?)",code,account,id);exec(c,"UPDATE live_admin_codes SET used=used+1 WHERE code=?",code);return new JSONObject().put("id",id).put("status","PENDING");
    }
    private static JSONObject state()throws SQLException{
        try(Connection c=DBHika.getConnection()){
            JSONArray accounts=rows(c,"SELECT a.id,a.username,a.ban,a.active,p.name,p.data_inventory FROM account a LEFT JOIN player p ON p.account_id=a.id ORDER BY a.id DESC LIMIT 500");
            Set<Integer> online=new HashSet<>();for(Player p:Client.gI().getPlayers())online.add(p.getSession().userId);
            for(int i=0;i<accounts.length();i++)accounts.getJSONObject(i).put("online",online.contains(accounts.getJSONObject(i).getInt("id")));
            return new JSONObject().put("accounts",accounts).put("online",online.size()).put("exp_multiplier",expMultiplier()).put("events",events)
                .put("actions",rows(c,"SELECT * FROM live_admin_actions ORDER BY created_at DESC LIMIT 100"))
                .put("orders",rows(c,"SELECT * FROM live_admin_orders ORDER BY created_at DESC LIMIT 100"))
                .put("codes",rows(c,"SELECT * FROM live_admin_codes ORDER BY expires_ms DESC LIMIT 100"))
                .put("audit",rows(c,"SELECT * FROM live_admin_audit ORDER BY id DESC LIMIT 100"))
                .put("items",RewardCatalog.items()).put("power_limits",RewardCatalog.limits())
                .put("packages",rows(c,"SELECT id,name,reward FROM live_admin_packages ORDER BY name"))
                .put("code_accounts",rows(c,"SELECT code,account_id FROM live_admin_code_accounts"))
                .put("pending_total",rows(c,"SELECT COUNT(*) AS total FROM live_admin_actions WHERE status='PENDING'").getJSONObject(0).getLong("total"))
                .put("reward_caps",new JSONObject().put("gold",nro.models.player.Inventory.LIMIT_GOLD).put("gem",Integer.MAX_VALUE).put("ruby",Integer.MAX_VALUE).put("potential",9000000000000L).put("upgrade",7).put("stars",8))
                .put("options",rows(c,"SELECT id,name FROM item_option_template ORDER BY id"));
        }
    }
    private static void reloadEvents()throws SQLException{try(Connection c=DBHika.getConnection()){events=rows(c,"SELECT * FROM live_admin_events ORDER BY start_ms DESC LIMIT 500");}}
    public static int expMultiplier(){int max=1;long now=System.currentTimeMillis();JSONArray list=events;for(int i=0;i<list.length();i++){JSONObject e=list.getJSONObject(i);if(e.getBoolean("enabled")&&e.getLong("start_ms")<=now&&now<e.getLong("end_ms"))max=Math.max(max,e.getInt("multiplier"));}return max;}
    public static void beforeLogin(int user)throws Exception{
        synchronized(LOGIN_LOCK){
            // Duplicate live character must never be overwritten by queued grants.
            if(Client.gI().getPlayerByUser(user)!=null)throw new IllegalStateException("Character still online");
            try(Connection c=DBHika.getConnection()){
                JSONArray account=rows(c,"SELECT ban FROM account WHERE id=?",user);if(account.isEmpty()||account.getJSONObject(0).getBoolean("ban"))throw new IllegalStateException("Account blocked");
                JSONArray pending=rows(c,"SELECT id FROM live_admin_actions WHERE account_id=? AND status='PENDING' ORDER BY CASE WHEN kind='ITEM' THEN 1 ELSE 0 END,created_at,id LIMIT 50",user);
                for(int i=0;i<pending.length();i++)apply(c,pending.getJSONObject(i).getString("id"),user);
            }
        }
    }
    private static void apply(Connection c,String id,int user)throws SQLException{
        c.setAutoCommit(false);
        try{
            JSONArray queue=rows(c,"SELECT * FROM live_admin_actions WHERE id=? FOR UPDATE",id);JSONObject action=queue.getJSONObject(0);
            if(!action.getString("status").equals("PENDING")){c.rollback();return;}
            JSONArray players=rows(c,"SELECT id,data_inventory,data_point,items_box FROM player WHERE account_id=? LIMIT 1 FOR UPDATE",user);
            if(players.isEmpty()){c.rollback();return;}
            JSONObject player=players.getJSONObject(0),reward=new JSONObject(action.getString("payload"));String before,after;
            if(reward.getString("kind").equals("CURRENCY")){
                before=player.getString("data_inventory");JSONArray money=new JSONArray(before);int slot=switch(reward.getString("currency")){case "gold"->0;case "gem"->1;case "ruby"->2;default->throw new IllegalArgumentException("Unknown currency");};
                long value=reward.optString("mode","ADD").equals("AT_LEAST")?Math.max(money.getLong(slot),reward.getLong("amount")):Math.addExact(money.getLong(slot),reward.getLong("amount"));long max=slot==0?nro.models.player.Inventory.LIMIT_GOLD:Integer.MAX_VALUE;
                if(value<0||value>max)throw new IllegalArgumentException("Số dư sau thay đổi vượt giới hạn; chưa cộng/trừ");money.put(slot,value);after=money.toString();exec(c,"UPDATE player SET data_inventory=? WHERE id=?",after,player.getLong("id"));
            }else if(reward.getString("kind").equals("POINTS")) {
                before=player.getString("data_point");JSONArray points=new JSONArray(before);
                int requested=reward.getInt("limit_index"),current=points.getInt(4),index=Math.max(requested,current);
                nro.power.PowerLimit limit=nro.power.PowerLimitManager.getInstance().get(index);
                if(limit==null)throw new IllegalArgumentException("Giới hạn sức mạnh không tồn tại");
                points.put(4,index);points.put(11,Math.max(points.getLong(11),reward.getLong("power")));
                points.put(8,Math.max(points.getLong(8),reward.getLong("potential")));
                if(reward.getBoolean("max_base")) {
                    points.put(12,Math.max(points.getInt(12),limit.getHp()));points.put(2,Math.max(points.getInt(2),limit.getMp()));
                    points.put(10,Math.max(points.getInt(10),limit.getDamage()));points.put(7,Math.max(points.getInt(7),limit.getDefense()));
                    points.put(3,Math.max(points.getInt(3),limit.getCritical()));points.put(6,points.getInt(12));points.put(1,points.getInt(2));
                }
                after=points.toString();exec(c,"UPDATE player SET data_point=?,power=? WHERE id=?",after,points.getLong(11),player.getLong("id"));
            }else{
                before=player.getString("items_box");JSONArray box=new JSONArray(before);int slot=-1;for(int i=0;i<box.length();i++)if(box.getJSONObject(i).getInt("temp_id")==-1){slot=i;break;}
                if(slot<0){c.rollback();return;}// Leave pending until the owner frees a box slot.
                JSONObject item=new JSONObject().put("temp_id",reward.getInt("item_id")).put("quantity",reward.getInt("quantity")).put("option",reward.getJSONArray("options")).put("create_time",System.currentTimeMillis());box.put(slot,item);after=box.toString();exec(c,"UPDATE player SET items_box=? WHERE id=?",after,player.getLong("id"));
            }
            exec(c,"UPDATE live_admin_actions SET status='APPLIED',details=?,applied_at=CURRENT_TIMESTAMP WHERE id=?",new JSONObject().put("before",before).put("after",after).toString(),id);audit(c,"APPLY",id,"account="+user);c.commit();
        }catch(IllegalArgumentException|ArithmeticException e){c.rollback();exec(c,"UPDATE live_admin_actions SET status='REJECTED',details=? WHERE id=? AND status='PENDING'",e.getMessage(),id);audit(c,"REJECT",id,e.getMessage());c.commit();}
        catch(SQLException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
}
