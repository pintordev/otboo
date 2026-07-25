-- 옷장을 부탁해(otboo) 초기 스키마
-- 근거: docs/erd.md, docs/dto-spec.md (enum 값), 네이밍 규칙: docs/conventions.md §2-2

CREATE TABLE locations
(
    id             UUID                     NOT NULL,
    latitude       DOUBLE PRECISION         NOT NULL,
    longitude      DOUBLE PRECISION         NOT NULL,
    x              INTEGER                  NOT NULL,
    y              INTEGER                  NOT NULL,
    location_names JSONB,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_LOCATIONS PRIMARY KEY (id)
);
CREATE INDEX IDX_locations_x_y ON locations (x, y);

CREATE TABLE weathers
(
    id                        UUID                     NOT NULL,
    location_id               UUID                     NOT NULL,
    forecasted_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    forecast_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    sky_status                VARCHAR(20)              NOT NULL CHECK (sky_status IN ('CLEAR', 'MOSTLY_CLOUDY', 'CLOUDY')),
    precipitation_type        VARCHAR(20)              NOT NULL CHECK (precipitation_type IN ('NONE', 'RAIN', 'RAIN_SNOW', 'SNOW', 'SHOWER')),
    precipitation_amount      DOUBLE PRECISION         NOT NULL,
    precipitation_probability DOUBLE PRECISION         NOT NULL,
    humidity_current          DOUBLE PRECISION         NOT NULL,
    humidity_compared         DOUBLE PRECISION         NOT NULL,
    temperature_current       DOUBLE PRECISION         NOT NULL,
    temperature_compared      DOUBLE PRECISION         NOT NULL,
    temperature_min           DOUBLE PRECISION         NOT NULL,
    temperature_max           DOUBLE PRECISION         NOT NULL,
    wind_speed                DOUBLE PRECISION         NOT NULL,
    wind_as_word              VARCHAR(20)              NOT NULL CHECK (wind_as_word IN ('WEAK', 'MODERATE', 'STRONG')),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_WEATHERS PRIMARY KEY (id),
    CONSTRAINT FK_locations_TO_weathers_1 FOREIGN KEY (location_id) REFERENCES locations (id)
);
CREATE INDEX IDX_weathers_location_id ON weathers (location_id);

CREATE TABLE users
(
    id         UUID                     NOT NULL,
    email      VARCHAR(255)             NOT NULL,
    password   VARCHAR(255)             NOT NULL,
    name       VARCHAR(50)              NOT NULL,
    role       VARCHAR(20)              NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    locked     BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_USERS PRIMARY KEY (id),
    CONSTRAINT UQ_users_email UNIQUE (email)
);

CREATE TABLE social_accounts
(
    id          UUID                     NOT NULL,
    user_id     UUID                     NOT NULL,
    provider    VARCHAR(20)              NOT NULL CHECK (provider IN ('GOOGLE', 'KAKAO')),
    provider_id VARCHAR(255)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_SOCIAL_ACCOUNTS PRIMARY KEY (id),
    CONSTRAINT FK_users_TO_social_accounts_1 FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT UQ_social_accounts_provider_provider_id UNIQUE (provider, provider_id)
);
CREATE INDEX IDX_social_accounts_user_id ON social_accounts (user_id);

CREATE TABLE profiles
(
    user_id                 UUID                     NOT NULL,
    name                    VARCHAR(50)              NOT NULL,
    gender                  VARCHAR(10) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    birth_date              DATE,
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    x                       INTEGER,
    y                       INTEGER,
    location_names          JSONB,
    temperature_sensitivity INTEGER                  NOT NULL DEFAULT 3 CHECK (temperature_sensitivity BETWEEN 1 AND 5),
    profile_image_url       VARCHAR(255),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_PROFILES PRIMARY KEY (user_id),
    CONSTRAINT FK_users_TO_profiles_1 FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE clothes_attribute_defs
(
    id         UUID                     NOT NULL,
    name       VARCHAR(50)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_CLOTHES_ATTRIBUTE_DEFS PRIMARY KEY (id),
    CONSTRAINT UQ_clothes_attribute_defs_name UNIQUE (name)
);

CREATE TABLE clothes_attribute_def_values
(
    id            UUID                     NOT NULL,
    definition_id UUID                     NOT NULL,
    value         VARCHAR(50)              NOT NULL,
    sort_order    INTEGER                  NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_CLOTHES_ATTRIBUTE_DEF_VALUES PRIMARY KEY (id),
    CONSTRAINT FK_clothes_attribute_defs_TO_clothes_attribute_def_values_1 FOREIGN KEY (definition_id) REFERENCES clothes_attribute_defs (id)
);
CREATE INDEX IDX_clothes_attribute_def_values_definition_id ON clothes_attribute_def_values (definition_id);

CREATE TABLE clothes
(
    id           UUID                     NOT NULL,
    owner_id     UUID                     NOT NULL,
    name         VARCHAR(50)              NOT NULL,
    image_url    VARCHAR(255),
    purchase_url VARCHAR(500),
    type         VARCHAR(20)              NOT NULL CHECK (type IN
                                                            ('TOP', 'BOTTOM', 'DRESS', 'OUTER', 'UNDERWEAR',
                                                             'ACCESSORY', 'SHOES', 'SOCKS', 'HAT', 'BAG', 'SCARF',
                                                             'ETC')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT PK_CLOTHES PRIMARY KEY (id),
    CONSTRAINT FK_users_TO_clothes_1 FOREIGN KEY (owner_id) REFERENCES users (id)
);
CREATE INDEX IDX_clothes_owner_id ON clothes (owner_id);

CREATE TABLE clothes_attributes
(
    id            UUID                     NOT NULL,
    clothes_id    UUID                     NOT NULL,
    definition_id UUID                     NOT NULL,
    value         VARCHAR(50)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_CLOTHES_ATTRIBUTES PRIMARY KEY (id),
    CONSTRAINT FK_clothes_TO_clothes_attributes_1 FOREIGN KEY (clothes_id) REFERENCES clothes (id),
    CONSTRAINT FK_clothes_attribute_defs_TO_clothes_attributes_1 FOREIGN KEY (definition_id) REFERENCES clothes_attribute_defs (id),
    CONSTRAINT UQ_clothes_attributes_clothes_id_definition_id UNIQUE (clothes_id, definition_id)
);

CREATE TABLE feeds
(
    id                        UUID                     NOT NULL,
    author_id                 UUID                     NOT NULL,
    weather_id                UUID,
    sky_status                VARCHAR(20) CHECK (sky_status IN ('CLEAR', 'MOSTLY_CLOUDY', 'CLOUDY')),
    precipitation_type        VARCHAR(20) CHECK (precipitation_type IN ('NONE', 'RAIN', 'RAIN_SNOW', 'SNOW', 'SHOWER')),
    precipitation_amount      DOUBLE PRECISION,
    precipitation_probability DOUBLE PRECISION,
    temperature_current       DOUBLE PRECISION,
    temperature_compared      DOUBLE PRECISION,
    ootds                     JSONB,
    content                   TEXT                     NOT NULL,
    like_count                BIGINT                   NOT NULL DEFAULT 0,
    comment_count             INTEGER                  NOT NULL DEFAULT 0,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at                TIMESTAMP WITH TIME ZONE,
    CONSTRAINT PK_FEEDS PRIMARY KEY (id),
    CONSTRAINT FK_users_TO_feeds_1 FOREIGN KEY (author_id) REFERENCES users (id)
);
CREATE INDEX IDX_feeds_author_id ON feeds (author_id);
CREATE INDEX IDX_feeds_created_at ON feeds (created_at);

CREATE TABLE feed_likes
(
    id         UUID                     NOT NULL,
    feed_id    UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_FEED_LIKES PRIMARY KEY (id),
    CONSTRAINT FK_feeds_TO_feed_likes_1 FOREIGN KEY (feed_id) REFERENCES feeds (id),
    CONSTRAINT FK_users_TO_feed_likes_1 FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT UQ_feed_likes_feed_id_user_id UNIQUE (feed_id, user_id)
);

CREATE TABLE comments
(
    id         UUID                     NOT NULL,
    feed_id    UUID                     NOT NULL,
    author_id  UUID                     NOT NULL,
    content    TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_COMMENTS PRIMARY KEY (id),
    CONSTRAINT FK_feeds_TO_comments_1 FOREIGN KEY (feed_id) REFERENCES feeds (id),
    CONSTRAINT FK_users_TO_comments_1 FOREIGN KEY (author_id) REFERENCES users (id)
);
CREATE INDEX IDX_comments_feed_id ON comments (feed_id);

CREATE TABLE follows
(
    id          UUID                     NOT NULL,
    follower_id UUID                     NOT NULL,
    followee_id UUID                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_FOLLOWS PRIMARY KEY (id),
    CONSTRAINT FK_users_TO_follows_1 FOREIGN KEY (follower_id) REFERENCES users (id),
    CONSTRAINT FK_users_TO_follows_2 FOREIGN KEY (followee_id) REFERENCES users (id),
    CONSTRAINT UQ_follows_follower_id_followee_id UNIQUE (follower_id, followee_id)
);

CREATE TABLE direct_messages
(
    id          UUID                     NOT NULL,
    sender_id   UUID                     NOT NULL,
    receiver_id UUID                     NOT NULL,
    content     TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_DIRECT_MESSAGES PRIMARY KEY (id),
    CONSTRAINT FK_users_TO_direct_messages_1 FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT FK_users_TO_direct_messages_2 FOREIGN KEY (receiver_id) REFERENCES users (id)
);
CREATE INDEX IDX_direct_messages_sender_id_receiver_id ON direct_messages (sender_id, receiver_id);

CREATE TABLE notifications
(
    id          UUID                     NOT NULL,
    receiver_id UUID                     NOT NULL,
    title       VARCHAR(255)             NOT NULL,
    content     TEXT                     NOT NULL,
    level       VARCHAR(20)              NOT NULL CHECK (level IN ('INFO', 'WARNING', 'ERROR')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_NOTIFICATIONS PRIMARY KEY (id),
    CONSTRAINT FK_users_TO_notifications_1 FOREIGN KEY (receiver_id) REFERENCES users (id)
);
CREATE INDEX IDX_notifications_receiver_id ON notifications (receiver_id);