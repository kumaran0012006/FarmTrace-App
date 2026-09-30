-- FarmTrace Relational Schema for PostgreSQL

CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(50) NOT NULL,
    assigned_batch_id VARCHAR(64),
    status VARCHAR(20) DEFAULT 'OFFLINE',
    last_seen_timestamp BIGINT DEFAULT 0,
    battery_percent INT DEFAULT 100,
    signal_dbm INT DEFAULT -75,
    firmware_version VARCHAR(30) DEFAULT 'v1.4.2',
    registered_at BIGINT
);

CREATE TABLE IF NOT EXISTS batches (
    batch_id VARCHAR(64) PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    variety VARCHAR(100) NOT NULL,
    farm_origin VARCHAR(255) NOT NULL,
    harvest_date DATE NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    unit VARCHAR(20) DEFAULT 'kg',
    destination VARCHAR(255) NOT NULL,
    current_stage VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    assigned_device_id VARCHAR(64) REFERENCES devices(device_id),
    temp_min NUMERIC(5,2) NOT NULL,
    temp_max NUMERIC(5,2) NOT NULL,
    humidity_min NUMERIC(5,2) NOT NULL,
    humidity_max NUMERIC(5,2) NOT NULL,
    max_ethylene_ppm NUMERIC(6,4) NOT NULL,
    created_at BIGINT
);

CREATE TABLE IF NOT EXISTS sensor_readings (
    event_id VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL REFERENCES devices(device_id),
    batch_id VARCHAR(64),
    timestamp BIGINT NOT NULL,
    temperature NUMERIC(6,2) NOT NULL,
    humidity NUMERIC(6,2) NOT NULL,
    ethylene NUMERIC(8,4) NOT NULL,
    battery INT NOT NULL,
    solar_voltage NUMERIC(6,2) DEFAULT 0.0,
    solar_current NUMERIC(8,2) DEFAULT 0.0,
    solar_energy NUMERIC(10,2) DEFAULT 0.0,
    power_consumption NUMERIC(8,2) DEFAULT 0.0,
    latitude NUMERIC(10,6),
    longitude NUMERIC(10,6),
    signal_strength INT DEFAULT -75,
    sync_status VARCHAR(20) DEFAULT 'SYNCED',
    data_hash VARCHAR(64) NOT NULL,
    raw_signature TEXT
);

CREATE INDEX idx_readings_device ON sensor_readings(device_id);
CREATE INDEX idx_readings_batch ON sensor_readings(batch_id);
CREATE INDEX idx_readings_time ON sensor_readings(timestamp);

CREATE TABLE IF NOT EXISTS traceability_events (
    event_id VARCHAR(64) PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL REFERENCES batches(batch_id),
    event_type VARCHAR(50) NOT NULL,
    stage VARCHAR(50) NOT NULL,
    location VARCHAR(255) NOT NULL,
    operator VARCHAR(150) NOT NULL,
    timestamp BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    environmental_conditions TEXT NOT NULL,
    blockchain_tx_id VARCHAR(100) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    current_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) DEFAULT 'CONFIRMED'
);

CREATE INDEX idx_events_batch ON traceability_events(batch_id);
CREATE INDEX idx_events_time ON traceability_events(timestamp);

CREATE TABLE IF NOT EXISTS alerts (
    alert_id VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    measurement VARCHAR(100) NOT NULL,
    threshold VARCHAR(100) NOT NULL,
    actual_value VARCHAR(100) NOT NULL,
    timestamp BIGINT NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(100),
    acknowledged_at BIGINT
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(50) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    ip_or_metadata VARCHAR(255)
);
