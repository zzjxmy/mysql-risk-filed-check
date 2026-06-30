-- Structured archive seed converted from archiver/haoshiqi/arch_notify.sh.
-- Split rule: each source table is an independent archive_task.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_hsq_online' LIMIT 1
);
SET @dest_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_legacy_hsq_online' LIMIT 1
);

DELETE FROM archive_task WHERE name IN (
  'haoshiqi/arch_notify',
  'haoshiqi/arch_notify/linked_mall_notify/purge_old_rows',
  'haoshiqi/arch_notify/yipanhuo_notify/purge_old_rows',
  'haoshiqi/arch_notify/taobaofactory_notify/purge_old_rows'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_notify/linked_mall_notify/purge_old_rows', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_notify.sh; original table: hsq_online.linked_mall_notify'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'linked_mall_notify purge old rows', 'PURGE', 'hsq_online', 'linked_mall_notify', NULL,
  NULL, NULL, 'created_at < DATE_SUB( CURDATE(), INTERVAL 3 MONTH )', b'1', 'UTF8', 5000, 5000, b'0', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_notify/yipanhuo_notify/purge_old_rows', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_notify.sh; original table: hsq_online.yipanhuo_notify'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'yipanhuo_notify purge old rows', 'PURGE', 'hsq_online', 'yipanhuo_notify', NULL,
  NULL, NULL, 'created_at < DATE_SUB( CURDATE(), INTERVAL 3 MONTH )', b'1', 'UTF8', 5000, 5000, b'0', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/arch_notify/taobaofactory_notify/purge_old_rows', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/arch_notify.sh; original table: hsq_online.taobaofactory_notify'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'taobaofactory_notify purge old rows', 'PURGE', 'hsq_online', 'taobaofactory_notify', NULL,
  NULL, NULL, 'created_at < DATE_SUB( CURDATE(), INTERVAL 3 MONTH )', b'1', 'UTF8', 5000, 5000, b'0', b'1', 0, b'1'
);
