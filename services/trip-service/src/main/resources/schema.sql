CREATE TABLE IF NOT EXISTS trips (
    id          VARCHAR(36)  PRIMARY KEY,
    rider_id    VARCHAR(36)  NOT NULL,
    driver_id   VARCHAR(36)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    pickup_lat  DOUBLE PRECISION NOT NULL,
    pickup_lng  DOUBLE PRECISION NOT NULL,
    dropoff_lat DOUBLE PRECISION NOT NULL,
    dropoff_lng DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
