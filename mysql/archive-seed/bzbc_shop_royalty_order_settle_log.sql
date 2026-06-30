-- Structured archive seed converted from archiver/bzbc/shop_royalty_order_settle_log.sh.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'bzbc_dwd_activity' LIMIT 1
);

DELETE FROM archive_task WHERE name = 'bzbc/shop_royalty_order_settle_log';

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'bzbc/shop_royalty_order_settle_log', 'NORMAL', @source_connection_id, @source_connection_id, 'DISABLED',
  'Converted from archiver/bzbc/shop_royalty_order_settle_log.sh'
);

SET @task_id := LAST_INSERT_ID();

INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'shop_royalty_order_settle_log purge by id', 'PURGE', 'dwd_activity', 'shop_royalty_order_settle_log', NULL,
  NULL, NULL, 'id < 23000000', b'1', 'UTF8', 5000, 5000, b'0', b'1', 0, b'1'
);
