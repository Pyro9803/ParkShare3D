CREATE TABLE reservations (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    spot_id         UUID            NOT NULL,
    vehicle_id      UUID            NOT NULL,
    driver_id       UUID            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'RESERVED',
    start_time      TIMESTAMP       NOT NULL,
    end_time        TIMESTAMP       NOT NULL,
    total_price     DECIMAL(12,2)   NOT NULL,
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT fk_reservations_spot    FOREIGN KEY (spot_id)    REFERENCES parking_spots(id),
    CONSTRAINT fk_reservations_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    CONSTRAINT fk_reservations_driver  FOREIGN KEY (driver_id)  REFERENCES users(id),
    CONSTRAINT uq_reservations_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_reservations_driver_id ON reservations (driver_id);
CREATE INDEX idx_reservations_spot_time ON reservations (spot_id, start_time, end_time);
CREATE INDEX idx_reservations_status    ON reservations (status);
