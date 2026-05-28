CREATE TABLE spot_availability (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    spot_id     UUID        NOT NULL,
    day_of_week VARCHAR(3)  NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_spot_availability PRIMARY KEY (id),
    CONSTRAINT fk_spot_availability_spot FOREIGN KEY (spot_id) REFERENCES parking_spots(id)
);
CREATE INDEX idx_spot_availability_spot ON spot_availability (spot_id) WHERE active = TRUE;
