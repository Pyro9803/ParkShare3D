ALTER TABLE reservations ADD COLUMN checked_in_at TIMESTAMP;

CREATE TABLE checkin_logs (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    reservation_id          UUID        NOT NULL,
    check_in_time           TIMESTAMP   NOT NULL,
    check_out_time          TIMESTAMP   NOT NULL,
    actual_duration_minutes INT         NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_checkin_logs PRIMARY KEY (id),
    CONSTRAINT fk_checkin_logs_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT uq_checkin_logs_reservation UNIQUE (reservation_id)
);
