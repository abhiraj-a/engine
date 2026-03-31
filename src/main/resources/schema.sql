CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS gateway_routes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_id VARCHAR(255) UNIQUE NOT NULL,
    uri VARCHAR(255) NOT NULL,
    predicates_json TEXT,
    filters_json TEXT,
    route_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS api_clients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_name VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) UNIQUE NOT NULL,
    jwks_url VARCHAR(512),
    rate_limit_capacity INT NOT NULL,
    rate_limit_refill INT NOT NULL,
    is_suspended BOOLEAN DEFAULT FALSE,
    current_tokens DOUBLE PRECISION,
    last_refill_time TIMESTAMP
);