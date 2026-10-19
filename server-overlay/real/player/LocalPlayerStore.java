package real.player;

import java.sql.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import real.item.Item;
import real.item.ItemOption;
import real.skill.Skill;
import server.DBService;

/** Persistence for the JSON columns actually read by Player.setup in the pinned source. */
public final class LocalPlayerStore {
    private LocalPlayerStore() {}

    @SuppressWarnings("unchecked")
    static String items(Item[] slots) {
        JSONArray result = new JSONArray();
        if (slots != null) for (int index = 0; index < slots.length; index++) {
            Item item = slots[index];
            if (item == null || item.id < 0) continue;
            JSONObject row = new JSONObject();
            row.put("index", index);
            row.put("id", item.template == null ? item.id : item.template.id);
            row.put("quantity", item.quantity);
            JSONArray options = new JSONArray();
            for (ItemOption option : item.itemOptions) {
                if (option.optionTemplate == null) throw new IllegalStateException("Item option has no template");
                JSONObject value = new JSONObject();
                value.put("id", option.optionTemplate.id);
                value.put("param", option.param);
                options.add(value);
            }
            row.put("option", options);
            result.add(row);
        }
        return result.toJSONString();
    }

    @SuppressWarnings("unchecked")
    public static void save(Player player) throws SQLException {
        if (player == null || player.id <= 0) throw new IllegalArgumentException("Invalid player");
        JSONArray skills = new JSONArray();
        for (Skill skill : player.skill) {
            JSONObject value = new JSONObject();
            value.put("id", skill.skillId);
            value.put("point", skill.point);
            skills.add(value);
        }
        String sql = "UPDATE player SET power=?,vang=?,luong=?,luong_khoa=?,task_id=?,head=?,"
            + "hp_goc=?,mp_goc=?,dame_goc=?,def_goc=?,crit_goc=?,tiem_nang=?,limit_power=?,"
            + "maxluggage=?,levelBag=?,itembag=?,itembox=?,itembody=?,skill=?,last_logout_time=CURRENT_TIMESTAMP WHERE id=?";
        try (Connection conn = DBService.gI().getConnection()) {
            if (conn == null) throw new SQLException("Cannot open save connection");
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int i = 1;
                    ps.setLong(i++, player.power); ps.setInt(i++, player.vang);
                    ps.setInt(i++, player.ngoc); ps.setInt(i++, player.ngocKhoa);
                    ps.setInt(i++, player.taskId); ps.setInt(i++, player.head);
                    ps.setInt(i++, player.hpGoc); ps.setInt(i++, player.mpGoc);
                    ps.setInt(i++, player.damGoc); ps.setInt(i++, player.defGoc);
                    ps.setInt(i++, player.critGoc); ps.setLong(i++, player.tiemNang);
                    ps.setInt(i++, player.limitPower); ps.setInt(i++, player.maxluggage);
                    ps.setInt(i++, player.levelBag); ps.setString(i++, items(player.ItemBag));
                    ps.setString(i++, items(player.ItemBox)); ps.setString(i++, items(player.ItemBody));
                    ps.setString(i++, skills.toJSONString()); ps.setInt(i++, player.id);
                    if (ps.executeUpdate() != 1) throw new SQLException("Save target does not exist");
                }
                if (player.map != null) try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player SET where_id=?,where_x=?,where_y=? WHERE id=?")) {
                    ps.setInt(1, player.map.id); ps.setInt(2, player.x); ps.setInt(3, player.y);
                    ps.setInt(4, player.id); ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException)e;
                throw new SQLException("Save rolled back", e);
            }
        }
    }

    /** Run only against the isolated test database; validates values survive a new connection. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--test-isolated-db".equals(args[0]))
            throw new IllegalArgumentException("Explicit isolated-database test flag required");
        Player player = new Player();
        player.timer.cancel();
        try (Connection conn = DBService.gI().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player(name,head,gender,account_id) VALUES ('__save_probe',64,0,-999)", Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); player.id = rs.getInt(1); }
            }
            try {
                player.power = 2345; player.vang = 8765; player.ngoc = 19;
                player.hpGoc = 200; player.mpGoc = 100; player.maxluggage = 30;
                player.ItemBag = new Item[30]; player.ItemBag[7] = new Item();
                player.ItemBag[7].id = 1; player.ItemBag[7].quantity = 3;
                save(player);
                try (Connection verify = DBService.gI().getConnection(); PreparedStatement ps = verify.prepareStatement(
                        "SELECT power,vang,luong,itembag FROM player WHERE id=?")) {
                    ps.setInt(1, player.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getLong(1) != 2345 || rs.getInt(2) != 8765 || rs.getInt(3) != 19
                                || !rs.getString(4).equals(items(player.ItemBag))) throw new AssertionError("Persistence mismatch");
                    }
                }
                System.out.println("PASS: player values and sparse inventory persisted across independent database connections");
            } finally {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player WHERE id=?")) {
                    ps.setInt(1, player.id); ps.executeUpdate();
                }
            }
        }
    }
}
