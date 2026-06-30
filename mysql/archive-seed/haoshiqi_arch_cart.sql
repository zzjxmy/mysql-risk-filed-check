-- Structured archive seed converted from archiver/haoshiqi/arch_cart.sh.
-- Split rule: each source table operation is an independent archive_task.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_hsq_online' LIMIT 1
);
SET @dest_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_legacy_hsq_online' LIMIT 1
);

DELETE FROM archive_task WHERE name IN (
  'haoshiqi/arch_cart',
  'haoshiqi/arch_cart/cart/purge_by_user_id',
  'haoshiqi/arch_cart/cart_sku/purge_by_user_id',
  'haoshiqi/arch_cart/cart_sku/archive_deleted_rows'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_cart/cart/purge_by_user_id', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_cart.sh; original table: hsq_online.cart'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'cart purge by user_id', 'PURGE', 'hsq_online', 'cart', 'idx_c_userid',
  NULL, NULL, 'user_id = 11182869', b'1', 'UTF8', 5000, 5000, b'0', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_cart/cart_sku/purge_by_user_id', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_cart.sh; original table: hsq_online.cart_sku'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'cart_sku purge by user_id', 'PURGE', 'hsq_online', 'cart_sku', 'idx_cs_user_sku_del',
  NULL, NULL, 'user_id = 11182869', b'1', 'UTF8', 5000, 5000, b'0', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_cart/cart_sku/archive_deleted_rows', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_cart.sh; original table: hsq_online.cart_sku'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'cart_sku archive deleted rows', 'ARCHIVE', 'hsq_online', 'cart_sku', 'deleteFlag',
  'legacy_hsq_online', 'cart_sku', 'delete_flag = 1', b'1', 'UTF8', 5000, 5000, b'1', b'1', 0, b'1'
);
