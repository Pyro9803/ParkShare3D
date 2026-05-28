CREATE TABLE parking_spots (
    id             UUID             NOT NULL DEFAULT gen_random_uuid(),
    lot_id         UUID             NOT NULL,
    code           VARCHAR(50)      NOT NULL,
    x              DOUBLE PRECISION NOT NULL,
    y              DOUBLE PRECISION NOT NULL,
    z              DOUBLE PRECISION NOT NULL,
    width          DOUBLE PRECISION NOT NULL,
    length         DOUBLE PRECISION NOT NULL,
    vehicle_type   VARCHAR(20)      NOT NULL,
    price_per_hour DECIMAL(10,2)    NOT NULL,
    active         BOOLEAN          NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_parking_spots      PRIMARY KEY (id),
    CONSTRAINT fk_parking_spots_lot  FOREIGN KEY (lot_id) REFERENCES parking_lots(id)
);

CREATE UNIQUE INDEX uq_parking_spots_active_code
    ON parking_spots (lot_id, code)
    WHERE active = TRUE;
