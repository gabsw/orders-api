-- Enable UUID generation (pgcrypto), safe to run multiple times
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS orders (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticker     TEXT NOT NULL,
  quantity   INT  NOT NULL,
  price      DOUBLE PRECISION NOT NULL,
  created_at TIMESTAMPTZ
);
