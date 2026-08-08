-- Database migration script to alter oid (Large Object) columns to standard TEXT columns
-- Preserves existing data using lo_get and convert_from.

-- 1. Table: work
ALTER TABLE work ADD COLUMN temp_payload TEXT;
UPDATE work SET temp_payload = convert_from(lo_get(payload), 'UTF8') WHERE payload IS NOT NULL AND payload::oid > 0;
ALTER TABLE work DROP COLUMN payload;
ALTER TABLE work RENAME COLUMN temp_payload TO payload;

ALTER TABLE work ADD COLUMN temp_last_error TEXT;
UPDATE work SET temp_last_error = convert_from(lo_get(last_error), 'UTF8') WHERE last_error IS NOT NULL AND last_error::oid > 0;
ALTER TABLE work DROP COLUMN last_error;
ALTER TABLE work RENAME COLUMN temp_last_error TO last_error;

-- 2. Table: event_log
ALTER TABLE event_log ADD COLUMN temp_message TEXT;
UPDATE event_log SET temp_message = convert_from(lo_get(message), 'UTF8') WHERE message IS NOT NULL AND message::oid > 0;
ALTER TABLE event_log DROP COLUMN message;
ALTER TABLE event_log RENAME COLUMN temp_message TO message;

ALTER TABLE event_log ADD COLUMN temp_reason TEXT;
UPDATE event_log SET temp_reason = convert_from(lo_get(reason), 'UTF8') WHERE reason IS NOT NULL AND reason::oid > 0;
ALTER TABLE event_log DROP COLUMN reason;
ALTER TABLE event_log RENAME COLUMN temp_reason TO reason;

ALTER TABLE event_log ADD COLUMN temp_metadata TEXT;
UPDATE event_log SET temp_metadata = convert_from(lo_get(metadata), 'UTF8') WHERE metadata IS NOT NULL AND metadata::oid > 0;
ALTER TABLE event_log DROP COLUMN metadata;
ALTER TABLE event_log RENAME COLUMN temp_metadata TO metadata;

-- 3. Table: worker
ALTER TABLE worker ADD COLUMN temp_last_error TEXT;
UPDATE worker SET temp_last_error = convert_from(lo_get(last_error), 'UTF8') WHERE last_error IS NOT NULL AND last_error::oid > 0;
ALTER TABLE worker DROP COLUMN last_error;
ALTER TABLE worker RENAME COLUMN temp_last_error TO last_error;

-- 4. Table: release_history
ALTER TABLE release_history ADD COLUMN temp_reason TEXT;
UPDATE release_history SET temp_reason = convert_from(lo_get(reason), 'UTF8') WHERE reason IS NOT NULL AND reason::oid > 0;
ALTER TABLE release_history DROP COLUMN reason;
ALTER TABLE release_history RENAME COLUMN temp_reason TO reason;

-- 5. Table: work_attempt
ALTER TABLE work_attempt ADD COLUMN temp_error TEXT;
UPDATE work_attempt SET temp_error = convert_from(lo_get(error), 'UTF8') WHERE error IS NOT NULL AND error::oid > 0;
ALTER TABLE work_attempt DROP COLUMN error;
ALTER TABLE work_attempt RENAME COLUMN temp_error TO error;
