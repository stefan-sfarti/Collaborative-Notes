-- Initial schema for CollabNotes
-- V1__initial_schema.sql

-- Users table
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    photo_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

-- Notes table
CREATE TABLE notes (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    owner_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    analysis TEXT,
    CONSTRAINT fk_notes_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX idx_notes_owner_id ON notes(owner_id);
CREATE INDEX idx_notes_updated_at ON notes(updated_at);

-- Collaborators table (join table for note sharing)
CREATE TABLE collaborators (
    id BIGSERIAL PRIMARY KEY,
    note_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_collaborators_note FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_collaborators_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_note_user UNIQUE (note_id, user_id)
);

CREATE INDEX idx_collaborators_note_id ON collaborators(note_id);
CREATE INDEX idx_collaborators_user_id ON collaborators(user_id);
