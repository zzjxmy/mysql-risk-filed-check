-- MySQL 字段容量风险检查平台 - 数据库初始化脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS fieldcheck 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE fieldcheck;

-- Create syntax for TABLE 'alert_config'
CREATE TABLE `alert_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `alert_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config` json DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'audit_log'
CREATE TABLE `audit_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail` text COLLATE utf8mb4_unicode_ci,
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `success` bit(1) NOT NULL,
  `target_id` bigint(20) DEFAULT NULL,
  `target_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'check_task'
CREATE TABLE `check_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `cron_expression` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `custom_whitelist` text COLLATE utf8mb4_unicode_ci,
  `db_pattern` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `full_scan` bit(1) DEFAULT NULL,
  `max_table_rows` bigint(20) DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sample_size` int(11) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `table_pattern` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `threshold_pct` int(11) DEFAULT NULL,
  `whitelist_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `y2038_warning_year` int(11) DEFAULT NULL,
  `connection_id` bigint(20) NOT NULL,
  `created_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK727fkha23y8hxldc1j88plr3y` (`connection_id`),
  KEY `FK1qa5mh5ctajx8pj4me4b46i56` (`created_by`),
  CONSTRAINT `FK1qa5mh5ctajx8pj4me4b46i56` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `FK727fkha23y8hxldc1j88plr3y` FOREIGN KEY (`connection_id`) REFERENCES `db_connection` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'db_connection'
CREATE TABLE `db_connection` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `host` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `port` int(11) NOT NULL,
  `remark` text COLLATE utf8mb4_unicode_ci,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9u2g9g4u2a18bytvr5uqvrk1f` (`created_by`),
  CONSTRAINT `FK9u2g9g4u2a18bytvr5uqvrk1f` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'risk_result'
CREATE TABLE `risk_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `column_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `column_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `current_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `database_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail` text COLLATE utf8mb4_unicode_ci,
  `remark` text COLLATE utf8mb4_unicode_ci,
  `risk_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `suggestion` text COLLATE utf8mb4_unicode_ci,
  `table_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `threshold_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `usage_percent` decimal(5,2) DEFAULT NULL,
  `execution_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_risk_type` (`risk_type`),
  KEY `idx_status` (`status`),
  CONSTRAINT `FKmhgul5ruq2trsyy7y5vu19yvi` FOREIGN KEY (`execution_id`) REFERENCES `task_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'sys_user'
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_51bvuyvihefoh4kp5syh2jpi4` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'task_alert_relation'
CREATE TABLE `task_alert_relation` (
  `task_id` bigint(20) NOT NULL,
  `alert_id` bigint(20) NOT NULL,
  PRIMARY KEY (`task_id`,`alert_id`),
  KEY `FK3aura3pmj3547wlos1e7i837v` (`alert_id`),
  CONSTRAINT `FK3aura3pmj3547wlos1e7i837v` FOREIGN KEY (`alert_id`) REFERENCES `alert_config` (`id`),
  CONSTRAINT `FK4gqwme24pc7ff0hsgh12y6jsk` FOREIGN KEY (`task_id`) REFERENCES `check_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'task_execution'
CREATE TABLE `task_execution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `log_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `processed_tables` int(11) DEFAULT NULL,
  `risk_count` int(11) DEFAULT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_tables` int(11) DEFAULT NULL,
  `trigger_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK38vsmv8w996if4sdk6m5rff9e` (`task_id`),
  CONSTRAINT `FK38vsmv8w996if4sdk6m5rff9e` FOREIGN KEY (`task_id`) REFERENCES `check_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'whitelist_rule'
CREATE TABLE `whitelist_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `remark` text COLLATE utf8mb4_unicode_ci,
  `rule` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create syntax for TABLE 'task_alert_config' (任务与告警配置关联表)
CREATE TABLE `task_alert_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL,
  `alert_config_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_alert` (`task_id`, `alert_config_id`),
  KEY `idx_alert_config_id` (`alert_config_id`),
  CONSTRAINT `FK_task_alert_task` FOREIGN KEY (`task_id`) REFERENCES `check_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FK_task_alert_config` FOREIGN KEY (`alert_config_id`) REFERENCES `alert_config` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入默认管理员用户（密码：admin123，BCrypt加密）
INSERT INTO `sys_user` (`created_at`, `updated_at`, `email`, `enabled`, `nickname`, `password`, `role`, `username`)
VALUES
	('2026-03-13 19:19:17.013000', '2026-03-13 19:19:17.013000', NULL, 1, 'Administrator', '$2a$10$HYQTTwNa/P6sRycdVHdZEum8beCVdTVPMYLOy4sjGzIChYdvIb0ja', 'ADMIN', 'admin');