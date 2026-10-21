CREATE TABLE IF NOT EXISTS live_admin_actions (id VARCHAR(64) PRIMARY KEY, account_id INT NOT NULL, kind VARCHAR(20) NOT NULL, payload LONGTEXT NOT NULL, reason VARCHAR(500) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', details TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, applied_at TIMESTAMP NULL, INDEX pending(account_id,status)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_admin_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, action VARCHAR(40) NOT NULL, reference_id VARCHAR(64) NOT NULL, detail TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_admin_orders (id VARCHAR(64) PRIMARY KEY, account_id INT NOT NULL, amount_vnd BIGINT NOT NULL, reward LONGTEXT NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'TEST_PENDING', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_admin_events (id VARCHAR(64) PRIMARY KEY, title VARCHAR(120) NOT NULL, start_ms BIGINT NOT NULL, end_ms BIGINT NOT NULL, multiplier INT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_admin_codes (code VARCHAR(30) PRIMARY KEY, max_uses INT NOT NULL, used INT NOT NULL DEFAULT 0, expires_ms BIGINT NOT NULL, reward LONGTEXT NOT NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_admin_redemptions (code VARCHAR(30) NOT NULL, account_id INT NOT NULL, action_id VARCHAR(64) NOT NULL, PRIMARY KEY(code,account_id), UNIQUE(action_id)) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS live_admin_packages (id VARCHAR(64) PRIMARY KEY, name VARCHAR(80) NOT NULL, reward LONGTEXT NOT NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_admin_code_accounts (code VARCHAR(30) NOT NULL, account_id INT NOT NULL, PRIMARY KEY(code,account_id)) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS live_companion_welcome (account_id INT PRIMARY KEY, claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_companion_config (id INT PRIMARY KEY, payload TEXT NOT NULL) ENGINE=InnoDB;
INSERT IGNORE INTO live_companion_config(id,payload) VALUES(1,'{"auto_normal":true,"welcome_gold":1000000,"hatch_seconds":3600,"fast_gold":100000000,"mabu_drop":25,"jiren_drop":10,"zeno_drop":5,"bosses":true}');
CREATE TABLE IF NOT EXISTS live_gameplay_config(id INT PRIMARY KEY,payload LONGTEXT NOT NULL) ENGINE=InnoDB;
INSERT IGNORE INTO live_gameplay_config VALUES(1,'{}');
CREATE TABLE IF NOT EXISTS live_player_growth(player_id BIGINT PRIMARY KEY,buffs LONGTEXT NOT NULL,leases LONGTEXT NOT NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS live_training_maps(map_id INT PRIMARY KEY,payload LONGTEXT NOT NULL) ENGINE=InnoDB;
INSERT IGNORE INTO live_training_maps VALUES(176,'{"map_id":176,"name":"Ngũ Hành Sơn — Luyện công","source_map":122,"fee_ruby":0,"lease_seconds":3600,"min_power":0,"mob_hp":200000,"exp_extra":0,"pk":false,"enabled":true,"start_ms":0,"end_ms":4102444800000}');
INSERT IGNORE INTO live_training_maps VALUES(177,'{"map_id":177,"name":"Địa Ngục — Luyện công","source_map":169,"fee_ruby":500,"lease_seconds":3600,"min_power":1500000,"mob_hp":1000000,"exp_extra":0,"pk":false,"enabled":true,"start_ms":0,"end_ms":4102444800000}');
