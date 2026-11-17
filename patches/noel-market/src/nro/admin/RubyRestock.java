package nro.admin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import nro.jdbc.DBHika;

final class RubyRestock {
    static final long DELAY_MS = 5 * 60_000L;
    private static boolean started;

    private RubyRestock() {}

    static synchronized void start() throws SQLException {
        if (started) return;
        ConsignmentStore.init();
        try (Connection c = DBHika.getConnection(); var statement = c.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_ruby_restock (
                    listing_id BIGINT PRIMARY KEY, due_ms BIGINT NOT NULL, category INT NOT NULL DEFAULT -1, INDEX(due_ms)
                ) ENGINE=InnoDB
                """);
            statement.executeUpdate("ALTER TABLE nero_ruby_restock ADD COLUMN IF NOT EXISTS category INT NOT NULL DEFAULT -1");
        }
        initializeStock();
        var timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "nero-ruby-restock");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(() -> {
            try {
                process();
            } catch (SQLException | RuntimeException error) {
                System.err.println("Ruby restock retry: " + error.getClass().getSimpleName());
            }
        }, 5, 5, TimeUnit.SECONDS);
        started = true;
    }

    static void schedule(Connection c, long listing, long purchasedAt, int category) throws SQLException {
        try (var insert = c.prepareStatement("INSERT INTO nero_ruby_restock(listing_id,due_ms,category) VALUES(?,?,?)")) {
            insert.setLong(1, listing);
            insert.setLong(2, Math.addExact(purchasedAt, DELAY_MS));
            insert.setInt(3, category);
            insert.executeUpdate();
        }
    }

    private static void initializeStock() throws SQLException {
        try (Connection c = DBHika.getConnection(); var statement = c.createStatement()) {
            c.setAutoCommit(false);
            try (var lock = statement.executeQuery("SELECT GET_LOCK('nero_ruby_restock',0)")) {
                if (!lock.next() || lock.getInt(1) != 1) return;
            }
            try {
                for (int category = 0; category < 12; category++) {
                    int target = category < RubyStock.CATEGORIES ? RubyStock.COUNT : 1;
                    try (var count = c.prepareStatement("""
                        SELECT (SELECT COUNT(*) FROM nero_consignment WHERE owner_id=-1 AND currency=2
                            AND sold=0 AND JSON_EXTRACT(item_json,'$.stock_category')=?)
                             + (SELECT COUNT(*) FROM nero_ruby_restock WHERE category=?)
                        """)) {
                        count.setInt(1, category);
                        count.setInt(2, category);
                        try (var rows = count.executeQuery()) {
                            rows.next();
                            for (int index = rows.getInt(1); index < target; index++) insert(c, category);
                        }
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            } finally {
                statement.executeQuery("SELECT RELEASE_LOCK('nero_ruby_restock')").close();
            }
        }
    }

    static void process() throws SQLException {
        try (Connection c = DBHika.getConnection(); var statement = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                var due = new ArrayList<Pending>();
                try (var select = c.prepareStatement("""
                    SELECT listing_id,category FROM nero_ruby_restock WHERE due_ms<=?
                    ORDER BY due_ms,listing_id LIMIT 64 FOR UPDATE
                    """)) {
                    select.setLong(1, System.currentTimeMillis());
                    try (var rows = select.executeQuery()) {
                        while (rows.next()) due.add(new Pending(rows.getLong(1),rows.getInt(2)));
                    }
                }
                for (Pending pending : due) {
                    insert(c, pending.category()<0 ? ThreadLocalRandom.current().nextInt(RubyStock.CATEGORIES) : pending.category());
                    try (var delete = c.prepareStatement("DELETE FROM nero_ruby_restock WHERE listing_id=?")) {
                        delete.setLong(1, pending.id());
                        delete.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            }
        }
    }

    private record Pending(long id, int category) {}

    static void insert(Connection c, int category) throws SQLException {
        var stock = RubyStock.roll(ThreadLocalRandom.current(),category);
        try (var insert = c.prepareStatement("""
            INSERT INTO nero_consignment(owner_id,currency,price,item_json,seller,created_ms)
            VALUES(-1,2,?,?,'Cửa hàng',?)
            """)) {
            insert.setInt(1, stock.price());
            insert.setString(2, ConsignmentStore.encode(stock.item()).put("stock_category",category).toString());
            insert.setLong(3, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }
}
