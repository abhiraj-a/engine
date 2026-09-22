-- Schema for Engine application
-- Run this against your PostgreSQL database

-- "user" table (quoted because "user" is a reserved keyword in PostgreSQL)
CREATE TABLE IF NOT EXISTS "user" (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255)
);

-- api_clients table
CREATE TABLE IF NOT EXISTS api_clients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name         VARCHAR(255) NOT NULL,
    client_id           VARCHAR(255) NOT NULL UNIQUE,
    jwks_url            VARCHAR(512),
    rate_limit_capacity INTEGER NOT NULL DEFAULT 100,
    rate_limit_refill   INTEGER NOT NULL DEFAULT 5,
    is_suspended        BOOLEAN NOT NULL DEFAULT FALSE,
    current_tokens      DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    last_refill_time    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index on client_id for fast lookups (used by rate limiting and admin endpoints)
CREATE INDEX IF NOT EXISTS idx_api_clients_client_id ON api_clients (client_id);

-- gateway_routes table
CREATE TABLE IF NOT EXISTS gateway_routes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id            VARCHAR(255) NOT NULL,
    uri                 VARCHAR(512) NOT NULL,
    predicates_json     TEXT,
    filters_json        TEXT,
    route_order         INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    owner_id            VARCHAR(255)
);

-- Index on route_id for fast lookups
CREATE INDEX IF NOT EXISTS idx_gateway_routes_route_id ON gateway_routes (route_id);

-- Index on owner_id for filtering routes by owner
CREATE INDEX IF NOT EXISTS idx_gateway_routes_owner_id ON gateway_routes (owner_id);
