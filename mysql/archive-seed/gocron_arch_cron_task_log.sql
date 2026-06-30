-- Structured archive seed converted from archiver/gocron/arch_cron_task_log.sh.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'gocron_dwd_cron' LIMIT 1
);

DELETE FROM archive_task WHERE name = 'gocron/arch_cron_task_log';

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'gocron/arch_cron_task_log', 'NORMAL', @source_connection_id, @source_connection_id, 'DISABLED',
  'Converted from archiver/gocron/arch_cron_task_log.sh'
);

SET @task_id := LAST_INSERT_ID();

INSERT INTO archive_task_variable (
  created_at, updated_at, task_id, name, query_sql, connection_id, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'maxid',
  'select max(id) from dwd_cron.cron_task_log where start_time < date_sub(now(),interval 1 week)',
  @source_connection_id, 0, b'1'
);

INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'cron_task_log purge by max id', 'PURGE', 'dwd_cron', 'cron_task_log', NULL,
  NULL, NULL, 'id < ${maxid}', b'1', 'UTF8', 5000, 1000, b'0', b'1', 0, b'1'
);
