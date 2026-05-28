CREATE TABLE vehicles (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    license_plate   VARCHAR(20)  NOT NULL,
    vehicle_type    VARCHAR(20)  NOT NULL,
    brand           VARCHAR(100),
    model           VARCHAR(100),
    color           VARCHAR(50),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_vehicles PRIMARY KEY (id),
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE UNIQUE INDEX uq_vehicles_active_plate
    ON vehicles (user_id, license_plate) WHERE active = TRUE;
