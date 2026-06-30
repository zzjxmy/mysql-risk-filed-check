-- Migration: structured archive task and batch archive configuration
-- Apply to existing fieldcheck databases that were initialized before
-- archive task batch mode was introduced.

USE fieldcheck;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table_name varchar(64),
    IN p_column_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS add_index_if_missing $$
CREATE PROCEDURE add_index_if_missing(
    IN p_table_name varchar(64),
    IN p_index_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS add_constraint_if_missing $$
CREATE PROCEDURE add_constraint_if_missing(
    IN p_table_name varchar(64),
    IN p_constraint_name varchar(64),
    IN p_ddl text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = p_table_name
          AND constraint_name = p_constraint_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL add_column_if_missing(
    'task_alert_config',
    'updated_at',
    'ALTER TABLE `task_alert_config` ADD COLUMN `updated_at` datetime(6) DEFAULT NULL AFTER `created_at`'
);

CALL add_column_if_missing(
    'archive_task',
    'task_mode',
    'ALTER TABLE `archive_task` ADD COLUMN `task_mode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''NORMAL'' AFTER `status`'
);

CALL add_column_if_missing(
    'archive_task_variable',
    'connection_id',
    'ALTER TABLE `archive_task_variable` ADD COLUMN `connection_id` bigint(20) DEFAULT NULL AFTER `query_sql`'
);

CALL add_index_if_missing(
    'archive_task_variable',
    'idx_archive_variable_connection',
    'ALTER TABLE `archive_task_variable` ADD KEY `idx_archive_variable_connection` (`connection_id`)'
);

CALL add_constraint_if_missing(
    'archive_task_variable',
    'FK_archive_variable_connection',
    'ALTER TABLE `archive_task_variable` ADD CONSTRAINT `FK_archive_variable_connection` FOREIGN KEY (`connection_id`) REFERENCES `db_connection` (`id`)'
);

CREATE TABLE IF NOT EXISTS `archive_batch_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `batch_query` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_size` int(11) NOT NULL DEFAULT '2000',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `load_sql` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_rounds` int(11) DEFAULT NULL,
  `target_database` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_table` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `truncate_sql` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_id` bigint(20) NOT NULL,
  `query_connection_id` bigint(20) NOT NULL,
  `target_connection_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_archive_batch_task` (`task_id`),
  KEY `idx_archive_batch_query_connection` (`query_connection_id`),
  KEY `idx_archive_batch_target_connection` (`target_connection_id`),
  CONSTRAINT `FK_archive_batch_task` FOREIGN KEY (`task_id`) REFERENCES `archive_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FK_archive_batch_query_connection` FOREIGN KEY (`query_connection_id`) REFERENCES `db_connection` (`id`),
  CONSTRAINT `FK_archive_batch_target_connection` FOREIGN KEY (`target_connection_id`) REFERENCES `db_connection` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `archive_task_step`
  MODIFY COLUMN `dest_database` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY COLUMN `dest_table` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

CALL add_column_if_missing(
    'archive_task_step',
    'index_name',
    'ALTER TABLE `archive_task_step` ADD COLUMN `index_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `extra_options`'
);

CALL add_column_if_missing(
    'archive_task_step',
    'step_mode',
    'ALTER TABLE `archive_task_step` ADD COLUMN `step_mode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''ARCHIVE'' AFTER `source_table`'
);

DROP PROCEDURE IF EXISTS add_constraint_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS add_column_if_missing;
