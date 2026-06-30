-- Structured archive seed converted from archiver/haoshiqi/arch_coupon_code.sh.
-- Split rule: each source table is an independent archive_task.
-- Each task uses a dedicated helper table to avoid cross-task overwrite when
-- tasks are scheduled independently.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_hsq_online' LIMIT 1
);
SET @dest_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_legacy_hsq_online' LIMIT 1
);

DELETE FROM archive_task WHERE name IN (
  'haoshiqi/arch_coupon_code',
  'haoshiqi/arch_coupon_code/user_coupon/archive_expired',
  'haoshiqi/arch_coupon_code/coupon_oprcd/archive_expired',
  'haoshiqi/arch_coupon_code/trade_order_coupon/archive_expired'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_coupon_code/user_coupon/archive_expired', 'BATCH_ARCHIVE', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_coupon_code.sh; original table: hsq_online.user_coupon; helper table: hsq_online.tmp_arch_user_coupon'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_batch_config (
  created_at, updated_at, task_id, query_connection_id, target_connection_id, batch_query, target_database, target_table,
  truncate_sql, load_sql, batch_size, max_rounds, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, @source_connection_id, @source_connection_id,
  'select id,coupon_code from hsq_online.user_coupon where is_expired = 1 and status = 0 and end_at < unix_timestamp(date_sub(now(),interval 24 month)) limit 1000',
  'hsq_online',
  'tmp_arch_user_coupon',
  'truncate table hsq_online.tmp_arch_user_coupon',
  'insert into hsq_online.tmp_arch_user_coupon (id,coupon_code) values (?,?)',
  1000,
  NULL,
  b'1'
);
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'user_coupon archive by helper ids', 'ARCHIVE', 'hsq_online', 'user_coupon', NULL,
  'legacy_hsq_online', 'user_coupon', 'id in (select id from tmp_arch_user_coupon)', b'1', 'UTF8', 1000, 1000, b'1', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_coupon_code/coupon_oprcd/archive_expired', 'BATCH_ARCHIVE', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_coupon_code.sh; original table: hsq_online.coupon_oprcd; helper table: hsq_online.tmp_arch_coupon_oprcd'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_batch_config (
  created_at, updated_at, task_id, query_connection_id, target_connection_id, batch_query, target_database, target_table,
  truncate_sql, load_sql, batch_size, max_rounds, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, @source_connection_id, @source_connection_id,
  'select coupon_code from hsq_online.user_coupon where is_expired = 1 and status = 0 and end_at < unix_timestamp(date_sub(now(),interval 24 month)) limit 1000',
  'hsq_online',
  'tmp_arch_coupon_oprcd',
  'truncate table hsq_online.tmp_arch_coupon_oprcd',
  'insert into hsq_online.tmp_arch_coupon_oprcd (coupon_code) values (?)',
  1000,
  NULL,
  b'1'
);
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'coupon_oprcd archive by helper coupon codes', 'ARCHIVE', 'hsq_online', 'coupon_oprcd', 'idx_co_code',
  'legacy_hsq_online', 'coupon_oprcd', 'coupon_code in (select coupon_code from tmp_arch_coupon_oprcd)', b'1', 'UTF8', 5000, 5000, b'1', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_coupon_code/trade_order_coupon/archive_expired', 'BATCH_ARCHIVE', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_coupon_code.sh; original table: hsq_online.trade_order_coupon; helper table: hsq_online.tmp_arch_trade_order_coupon'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_batch_config (
  created_at, updated_at, task_id, query_connection_id, target_connection_id, batch_query, target_database, target_table,
  truncate_sql, load_sql, batch_size, max_rounds, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, @source_connection_id, @source_connection_id,
  'select coupon_code from hsq_online.user_coupon where is_expired = 1 and status = 0 and end_at < unix_timestamp(date_sub(now(),interval 24 month)) limit 1000',
  'hsq_online',
  'tmp_arch_trade_order_coupon',
  'truncate table hsq_online.tmp_arch_trade_order_coupon',
  'insert into hsq_online.tmp_arch_trade_order_coupon (coupon_code) values (?)',
  1000,
  NULL,
  b'1'
);
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'trade_order_coupon archive by helper coupon codes', 'ARCHIVE', 'hsq_online', 'trade_order_coupon', 'idx_toc_co',
  'legacy_hsq_online', 'trade_order_coupon', 'coupon_code in (select coupon_code from tmp_arch_trade_order_coupon)', b'1', 'UTF8', 5000, 5000, b'1', b'1', 0, b'1'
);
