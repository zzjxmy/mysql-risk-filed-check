-- Structured archive seed converted from archiver/piwik/arch_log_visit.sh.

SET @source_connection_id := (
  SELECT id FROM db_connection WHERE name = 'piwik_dwd_analystic' LIMIT 1
);
SET @dest_connection_id := (
  SELECT id FROM db_connection WHERE name = 'piwik_legacy_dwd_analystic' LIMIT 1
);

DELETE FROM archive_task WHERE name = 'piwik/arch_log_visit';

INSERT INTO archive_task (
  created_at, updated_at, name, task_mode, source_connection_id, dest_connection_id, status, remark
) VALUES (
  NOW(6), NOW(6), 'piwik/arch_log_visit', 'NORMAL', @source_connection_id, @dest_connection_id, 'DISABLED',
  'Converted from archiver/piwik/arch_log_visit.sh'
);

SET @task_id := LAST_INSERT_ID();

INSERT INTO archive_task_step (
  created_at, updated_at, task_id, name, step_mode, source_database, source_table, index_name,
  dest_database, dest_table, where_template, delete_source, charset, limit_size, progress_size,
  bulk_insert, commit_each, sort_order, enabled
) VALUES (
  NOW(6), NOW(6), @task_id, 'log_visit archive old visits', 'ARCHIVE', 'dwd_analystic', 'log_visit', NULL,
  'legacy_dwd_analystic', 'log_visit', 'visit_first_action_time < date_sub(now(),interval 1 month)',
  b'1', 'UTF8', 5000, 5000, b'1', b'1', 0, b'1'
);
