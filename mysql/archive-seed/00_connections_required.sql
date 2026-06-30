-- Required db_connection records for archive seed files.
-- Create these connections through the application UI before running the seeds.
-- Credentials are intentionally managed outside seed SQL.

SELECT
  'haoshiqi_hsq_online' AS connection_name,
  'source connection for hsq_online archive scripts' AS purpose
UNION ALL
SELECT
  'haoshiqi_legacy_hsq_online' AS connection_name,
  'destination connection for legacy_hsq_online archive scripts' AS purpose
UNION ALL
SELECT
  'gocron_dwd_cron' AS connection_name,
  'source connection for dwd_cron purge scripts' AS purpose
UNION ALL
SELECT
  'piwik_dwd_analystic' AS connection_name,
  'source connection for piwik analystic archive scripts' AS purpose
UNION ALL
SELECT
  'piwik_legacy_dwd_analystic' AS connection_name,
  'destination connection for legacy_dwd_analystic archive scripts' AS purpose
UNION ALL
SELECT
  'bzbc_dwd_activity' AS connection_name,
  'source connection for bzbc activity purge scripts' AS purpose;
