CREATE TABLE parking_lots (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500) NOT NULL,
    description TEXT,
    floor       INT          NOT NULL,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_parking_lots PRIMARY KEY (id),
    CONSTRAINT fk_parking_lots_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);
