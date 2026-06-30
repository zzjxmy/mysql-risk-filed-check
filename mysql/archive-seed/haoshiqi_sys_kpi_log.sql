-- Structured archive seed converted from archiver/haoshiqi/sys_kpi_log.sh.
-- Split rule: each source table is an independent archive_task.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_hsq_online' LIMIT 1
);
SET @dest_connection_id := (
  SELECT id FROM db_connection WHERE name = 'haoshiqi_legacy_hsq_online' LIMIT 1
);

DELETE FROM archive_task WHERE name IN (
  'haoshiqi/sys_kpi_log',
  'haoshiqi/sys_kpi_log/sys_kpi_log/archive_by_max_id',
  'haoshiqi/sys_kpi_log/sys_kpi_params/archive_by_log_id'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/sys_kpi_log/sys_kpi_log/archive_by_max_id', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/sys_kpi_log.sh; original table: hsq_online.sys_kpi_log'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_variable (
  created_at, updated_at, task_id, name, query_sql, connection_id, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'maxid',
  'select max(id) from hsq_online.sys_kpi_log where log_time < from_unixtime( unix_timestamp() - 86400 * 180 )',
  @source_connection_id, 0, b'1'
);
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'sys_kpi_log archive by max id', 'ARCHIVE', 'hsq_online', 'sys_kpi_log', NULL,
  'legacy_hsq_online', 'sys_kpi_log', 'id < ${maxid}', b'1', 'UTF8', 5000, 5000, b'1', b'1', 0, b'1'
);

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'haoshiqi/sys_kpi_log/sys_kpi_params/archive_by_log_id', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/haoshiqi/sys_kpi_log.sh; original table: hsq_online.sys_kpi_params'
);
SET @task_id := LAST_INSERT_ID();
INSERT INTO archive_task_variable (
  created_at, updated_at, task_id, name, query_sql, connection_id, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'maxid',
  'select max(id) from hsq_online.sys_kpi_log where log_time < from_unixtime( unix_timestamp() - 86400 * 180 )',
  @source_connection_id, 0, b'1'
);
INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'sys_kpi_params archive by log id', 'ARCHIVE', 'hsq_online', 'sys_kpi_params', NULL,
  'legacy_hsq_online', 'sys_kpi_params', 'log_id < ${maxid}', b'1', 'UTF8', 5000, 5000, b'1', b'1', 0, b'1'
);
