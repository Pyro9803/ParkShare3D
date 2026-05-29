CREATE TABLE reviews (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    reservation_id  UUID            NOT NULL UNIQUE,
    spot_id         UUID            NOT NULL,
    driver_id       UUID            NOT NULL,
    rating          SMALLINT        NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT fk_reviews_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_reviews_spot FOREIGN KEY (spot_id) REFERENCES parking_spots (id),
    CONSTRAINT fk_reviews_driver FOREIGN KEY (driver_id) REFERENCES users (id)
);

CREATE INDEX idx_reviews_spot_id ON reviews (spot_id);
