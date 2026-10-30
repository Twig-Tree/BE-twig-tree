CREATE TABLE IF NOT EXISTS members (
    member_id       BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(255),
    profile_image   VARCHAR(512),
    provider        VARCHAR(20)     NOT NULL,
    provider_id     VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),
    CONSTRAINT uk_members_provider UNIQUE (provider, provider_id)
);
