CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    room_code VARCHAR(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    host_token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_rooms_room_code UNIQUE (room_code),
    CONSTRAINT uq_rooms_host_token_hash UNIQUE (host_token_hash),
    CONSTRAINT ck_rooms_room_code CHECK (room_code ~ '^[A-Z0-9]{6}$'),
    CONSTRAINT ck_rooms_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_rooms_status CHECK (status IN ('WAITING', 'PLAYING', 'FINISHED', 'CLOSED'))
);

CREATE TABLE participants (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    participant_token_hash VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    game_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_participants_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT uq_participants_token_hash UNIQUE (participant_token_hash),
    CONSTRAINT ck_participants_nickname CHECK (btrim(nickname) <> ''),
    CONSTRAINT ck_participants_type CHECK (type IN ('HOST', 'PLAYER')),
    CONSTRAINT ck_participants_game_role CHECK (game_role IN ('NONE', 'HIDER', 'SEEKER')),
    CONSTRAINT ck_participants_status CHECK (status IN ('WAITING', 'ACTIVE', 'ELIMINATED', 'SURVIVED', 'LEFT'))
);

CREATE UNIQUE INDEX uq_participants_room_nickname_ci
    ON participants (room_id, lower(nickname));

CREATE INDEX idx_participants_room_id ON participants (room_id);

CREATE TABLE games (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    design_duration_seconds INTEGER NOT NULL,
    hide_duration_seconds INTEGER NOT NULL,
    seek_duration_seconds INTEGER NOT NULL,
    seeker_count INTEGER NOT NULL,
    design_started_at TIMESTAMP WITH TIME ZONE,
    hide_started_at TIMESTAMP WITH TIME ZONE,
    seek_started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    winner VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_games_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_games_status CHECK (status IN ('WAITING', 'ROLE_ASSIGNED', 'DESIGNING', 'PRINTING', 'HIDING', 'SEEKING', 'FINISHED')),
    CONSTRAINT ck_games_winner CHECK (winner IN ('HIDER', 'SEEKER', 'NONE')),
    CONSTRAINT ck_games_design_duration CHECK (design_duration_seconds > 0),
    CONSTRAINT ck_games_hide_duration CHECK (hide_duration_seconds > 0),
    CONSTRAINT ck_games_seek_duration CHECK (seek_duration_seconds > 0),
    CONSTRAINT ck_games_seeker_count CHECK (seeker_count > 0)
);

CREATE INDEX idx_games_room_id ON games (room_id);

CREATE TABLE characters (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    original_photo_url TEXT NOT NULL,
    character_image_url TEXT NOT NULL,
    preview_image_url TEXT NOT NULL,
    position_x DOUBLE PRECISION,
    position_y DOUBLE PRECISION,
    scale DOUBLE PRECISION,
    rotation DOUBLE PRECISION,
    qr_token VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    printed_at TIMESTAMP WITH TIME ZONE,
    found_at TIMESTAMP WITH TIME ZONE,
    found_by_participant_id BIGINT,
    CONSTRAINT fk_characters_game FOREIGN KEY (game_id) REFERENCES games (id),
    CONSTRAINT fk_characters_participant FOREIGN KEY (participant_id) REFERENCES participants (id),
    CONSTRAINT fk_characters_found_by FOREIGN KEY (found_by_participant_id) REFERENCES participants (id),
    CONSTRAINT uq_characters_game_participant UNIQUE (game_id, participant_id),
    CONSTRAINT uq_characters_qr_token UNIQUE (qr_token),
    CONSTRAINT ck_characters_template_type CHECK (btrim(template_type) <> ''),
    CONSTRAINT ck_characters_status CHECK (status IN ('SUBMITTED', 'PRINTED', 'HIDDEN', 'FOUND', 'SURVIVED')),
    CONSTRAINT ck_characters_position_x CHECK (position_x IS NULL OR position_x BETWEEN 0.0 AND 1.0),
    CONSTRAINT ck_characters_position_y CHECK (position_y IS NULL OR position_y BETWEEN 0.0 AND 1.0),
    CONSTRAINT ck_characters_scale CHECK (scale IS NULL OR scale > 0.0)
);

CREATE INDEX idx_characters_game_id ON characters (game_id);
CREATE INDEX idx_characters_participant_id ON characters (participant_id);
