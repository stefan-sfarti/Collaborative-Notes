-- Add optimistic locking version column to notes table.
-- IF NOT EXISTS makes this safe on the dev profile where ddl-auto:create already
-- creates the column from the @Version entity field before Flyway runs.
-- Initialized to 0 for all existing rows; JPA @Version manages it from here.
ALTER TABLE notes ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
