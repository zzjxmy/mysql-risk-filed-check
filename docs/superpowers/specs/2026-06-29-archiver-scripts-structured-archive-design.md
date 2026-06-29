# Archiver Scripts Structured Archive Design

## Background

The repository contains historical shell scripts under `archiver/` that run `pt-archiver` directly. The current system already has archive tasks, archive steps, variable queries, Quartz scheduling, execution logs, and alert integration, but the model only represents normal archive steps that always include a destination database.

The new direction is to stop running or importing shell scripts at runtime. Instead, the system will be enhanced so every script can be represented as structured archive configuration, then the historical scripts will be converted into SQL seed files that insert standard archive tasks, variables, batch settings, and steps.

## Goals

- Represent every existing `archiver/**/*.sh` script as system-managed archive tasks.
- Avoid a generic bash importer and avoid shell script execution as the long-term integration path.
- Support pure delete operations that use `pt-archiver --purge` without `--dest`.
- Support step-level source index names such as `i=idx_c_userid`.
- Support single-value variable queries such as `maxid=$(mysql ... "select max(id) ...")`.
- Support complex order-style scripts by decomposing them into batch archive tasks instead of preserving while loops in bash.
- Produce reviewable SQL seed files that can be applied after the required database connections exist.
- Keep database passwords out of the SQL seed files.

## Non-Goals

- No runtime bash parser or import UI.
- No generic shell interpreter inside the backend.
- No automatic encryption or insertion of database connection passwords from old scripts.
- No behavior change to field-risk scan tasks outside the archive module.

## Archive Modes

Archive steps will support three modes:

- `ARCHIVE`: run `pt-archiver` with both `--source` and `--dest`.
- `PURGE`: run `pt-archiver` with `--source` and `--purge`, without `--dest`.
- `BATCH_ARCHIVE`: run a configured batch query, load the batch keys into a configured helper table, then run a sequence of `ARCHIVE` or `PURGE` steps that reference that helper table.

`ARCHIVE` and `PURGE` are step modes. `BATCH_ARCHIVE` is a task execution mode because one batch is shared by many steps.

## Data Model Changes

### `archive_task`

Add:

- `task_mode varchar(30) not null default 'NORMAL'`

Values:

- `NORMAL`: existing archive task behavior.
- `BATCH_ARCHIVE`: repeat a batch query until no rows remain.

### `archive_task_step`

Add:

- `step_mode varchar(30) not null default 'ARCHIVE'`
- `index_name varchar(100) null`

Rules:

- `ARCHIVE` requires `dest_database` and `dest_table`.
- `PURGE` does not require `dest_database` or `dest_table`.
- `index_name` is rendered into the source DSN as `i=<index_name>`.
- Existing fields such as `where_template`, `charset`, `limit_size`, `progress_size`, `bulk_insert`, `commit_each`, and `extra_options` stay in use.

### `archive_task_variable`

Add:

- `connection_id bigint null`

Rules:

- If `connection_id` is set, query the variable from that connection.
- If it is empty, keep current behavior and query from the task source connection.

### `archive_batch_config`

Create:

- `id bigint primary key auto_increment`
- `task_id bigint not null`
- `connection_id bigint not null`
- `batch_query text not null`
- `target_database varchar(100) not null`
- `target_table varchar(100) not null`
- `truncate_sql text not null`
- `load_sql text not null`
- `batch_size int not null default 2000`
- `max_rounds int null`
- `enabled bit not null default b'1'`

Purpose:

- `batch_query` returns one batch of keys, for example `order_id,pay_id`.
- The executor writes the returned rows into a temporary/helper table such as `tmp_arch_order_id`.
- Steps can reference the helper table in `where_template`.

The first implementation will support the current script pattern where the helper table is a normal table in the source database and data is loaded before each batch round.

## Command Generation

`ArchiveCommandBuilder` will be extended as follows:

- For `ARCHIVE`, keep the current source and destination DSN behavior.
- For `PURGE`, omit `--dest`, append `--purge`, and do not append `--no-delete`.
- For `index_name`, append `,i=<index_name>` to the source DSN.
- For `bulk_insert`, keep existing `--bulk-insert` and DSN `L=1` behavior.
- Continue redacting passwords in logged command lines.

## Execution Flow

### Normal Tasks

1. Load enabled variables.
2. Render variables into each enabled step's `where_template`.
3. Build and run one `pt-archiver` command per step.
4. Stop on the first non-zero exit code.
5. Persist logs, processed step count, skipped step count, and exit code.

### Batch Archive Tasks

1. Run enabled single-value variables once before batch execution.
2. Start batch round `1`.
3. Execute `batch_query`.
4. If it returns no rows, finish the task.
5. Truncate the configured helper table.
6. Load batch rows into the helper table.
7. Run every enabled step in sort order.
8. If a step fails, stop the task and keep logs for the failed round.
9. Continue with the next round until no rows remain or `max_rounds` is reached.

Batch logs must include the round number, batch row count, helper table name, and each step name.

## SQL Seed Strategy

Historical scripts will be converted into SQL files under:

`mysql/archive-seed/`

Recommended layout:

- `00_connections_required.sql`: documents required connection names and lookup variables.
- `haoshiqi_arch_cart.sql`
- `haoshiqi_table_arch.sql`
- `bzbc_table_arch.sql`
- one SQL file per source bash script or coherent script group.

Seed SQL must:

- Use connection names to look up IDs from `db_connection`.
- Insert tasks with deterministic names such as `haoshiqi/arch_cart`.
- Insert steps with deterministic names such as `cart purge by user_id`.
- Store the original script path in `remark`.
- Avoid storing database passwords.
- Use idempotent patterns where practical, such as deleting and reinserting by task name or guarding with existing-name checks.

## Script Mapping Rules

### Fixed `pt-archiver` Commands

Each command becomes one step.

Example from `arch_cart.sh`:

- `cart` with `--purge` becomes `PURGE`.
- `cart_sku` with `--purge` becomes `PURGE`.
- `cart_sku` with `--dest legacy_hsq_online` becomes `ARCHIVE`.

### Table Loop Scripts

One script becomes one task. Each table in the loop becomes one step. The `case` branch supplies the step's `where_template`, `index_name`, and extra options.

### Single-Value Variable Scripts

Shell variables from `mysql -N -e "select ..."` become `archive_task_variable` rows. The step where clause uses `${variable_name}`.

Example:

- `maxid=$(mysql ... "select max(id) ...")`
- `where "id < ${maxid}"`

### Complex Batch Scripts

Scripts such as `pt_arch_hsq_order.sh` become `BATCH_ARCHIVE` tasks.

The batch query captures the original ID selection:

- `select id,pay_id from trade_order where created_at < unix_timestamp('2023-01-01 00:00:00') order by id limit 2000`

The helper table is:

- source database: `hsq_online`
- helper table: `tmp_arch_order_id`

Each `order_archiver` call becomes one step that references `tmp_arch_order_id`.

## Frontend Changes

The archive task form will expose:

- Task mode: normal or batch archive.
- Step mode: archive or purge.
- Source index name.
- Optional variable connection.
- Batch config section when task mode is `BATCH_ARCHIVE`.

The form must hide destination database/table inputs for `PURGE` steps.

## Testing Strategy

Backend tests:

- `ArchiveCommandBuilder` builds `PURGE` commands without `--dest`.
- `ArchiveCommandBuilder` includes source DSN `i=<index_name>`.
- `ArchiveCommandBuilder` preserves existing `ARCHIVE` behavior.
- `ArchiveTaskService` stores and returns step mode, index name, variable connection, and batch config.
- `ArchiveTaskExecutor` runs batch rounds until the batch query returns no rows.

Frontend tests or build checks:

- Archive task form can submit `PURGE` steps.
- Archive task form can submit source index names.
- Archive task form can show and submit batch config.

Seed SQL checks:

- SQL files reference existing connection names instead of passwords.
- Each historical script has a mapped task or documented structured batch task.
- `arch_cart.sh` is covered by one task with three steps.

## Rollout Plan

1. Extend database schema and Java entities.
2. Extend command generation for `PURGE` and source indexes.
3. Extend normal executor behavior.
4. Add batch archive model and executor flow.
5. Extend API DTOs and frontend form.
6. Add SQL seed files for a small representative set, starting with `arch_cart.sh`.
7. Convert remaining scripts into seed SQL in batches by directory.
8. Run backend tests and frontend build.

## Open Decisions Resolved

- All scripts will be represented structurally.
- No runtime bash importer will be built.
- No complex script will remain as a hosted bash workflow.
- SQL seed files are the migration artifact.
