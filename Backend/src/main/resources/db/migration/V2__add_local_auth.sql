-- Add password and auth_type columns for local authentication
-- V2__add_local_auth.sql

ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL;
ALTER TABLE users ADD COLUMN auth_type VARCHAR(50) NOT NULL DEFAULT 'local';

CREATE INDEX idx_users_auth_type ON users(auth_type);
