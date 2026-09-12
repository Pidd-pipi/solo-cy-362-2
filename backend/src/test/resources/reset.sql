-- 每个测试方法执行前清空业务表，保证用例互不影响、整套测试可连续重复运行。
-- H2 不允许 TRUNCATE 被外键引用的表，故按子表到父表顺序 DELETE，再重置自增序列。
DELETE FROM session_registrations;
DELETE FROM game_sessions;
DELETE FROM scripts;
DELETE FROM operation_records;
ALTER TABLE session_registrations ALTER COLUMN id RESTART WITH 1;
ALTER TABLE game_sessions ALTER COLUMN id RESTART WITH 1;
ALTER TABLE scripts ALTER COLUMN id RESTART WITH 1;
ALTER TABLE operation_records ALTER COLUMN id RESTART WITH 1;
