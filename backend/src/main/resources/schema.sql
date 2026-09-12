CREATE TABLE IF NOT EXISTS operation_records (
  id BIGSERIAL PRIMARY KEY,
  module_name VARCHAR(120) NOT NULL,
  owner_name VARCHAR(80) NOT NULL,
  status VARCHAR(40) NOT NULL,
  metric VARCHAR(40) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scripts (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  genre VARCHAR(40) NOT NULL,
  difficulty VARCHAR(20) NOT NULL,
  duration_minutes INT NOT NULL,
  min_players INT NOT NULL,
  max_players INT NOT NULL,
  dm_requirement VARCHAR(120),
  description VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS game_sessions (
  id BIGSERIAL PRIMARY KEY,
  script_id BIGINT NOT NULL REFERENCES scripts(id),
  start_time TIMESTAMP NOT NULL,
  host_name VARCHAR(80) NOT NULL,
  capacity INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS session_registrations (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
  player_name VARCHAR(80) NOT NULL,
  contact VARCHAR(120),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_session_player UNIQUE (session_id, player_name)
);

CREATE INDEX IF NOT EXISTS idx_registrations_session ON session_registrations(session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_start_time ON game_sessions(start_time);

-- 种子数据：剧本
INSERT INTO operation_records (module_name, owner_name, status, metric)
SELECT '剧本库与DM管理', '运营组', 'ready', '100%'
WHERE NOT EXISTS (SELECT 1 FROM operation_records LIMIT 1);

-- 种子数据：剧本
INSERT INTO scripts (name, genre, difficulty, duration_minutes, min_players, max_players, dm_requirement, description, active)
SELECT '年轮', '推理本', '困难', 300, 4, 6, '资深DM（控场/计时）', '时间循环题材的硬核推理本，适合进阶玩家。', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE name = '年轮');

INSERT INTO scripts (name, genre, difficulty, duration_minutes, min_players, max_players, dm_requirement, description, active)
SELECT '拆迁', '欢乐本', '简单', 240, 5, 8, '新手DM即可', '机制轻松、互动性强的欢乐本，适合拼车熟络气氛。', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE name = '拆迁');

INSERT INTO scripts (name, genre, difficulty, duration_minutes, min_players, max_players, dm_requirement, description, active)
SELECT '古木吟', '情感本', '中等', 280, 3, 6, '情感向DM（沉浸演绎）', '校园情感沉浸本，重演绎与情绪推进。', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE name = '古木吟');

INSERT INTO scripts (name, genre, difficulty, duration_minutes, min_players, max_players, dm_requirement, description, active)
SELECT '病娇男孩的精分日记', '恐怖本', '中等', 300, 4, 7, '恐怖向DM（灯光/音效）', '微恐演绎本，需要门店配合氛围布置。', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scripts WHERE name = '病娇男孩的精分日记');

-- 种子数据：场次（使用未来固定时间，便于页面直接演示报名）
INSERT INTO game_sessions (script_id, start_time, host_name, capacity)
SELECT s.id, TIMESTAMP '2026-09-20 14:00:00', 'DM-小鹿', 6
FROM scripts s
WHERE s.name = '年轮'
  AND NOT EXISTS (SELECT 1 FROM game_sessions WHERE host_name = 'DM-小鹿');

INSERT INTO game_sessions (script_id, start_time, host_name, capacity)
SELECT s.id, TIMESTAMP '2026-09-20 19:00:00', 'DM-阿杰', 8
FROM scripts s
WHERE s.name = '拆迁'
  AND NOT EXISTS (SELECT 1 FROM game_sessions WHERE host_name = 'DM-阿杰');
