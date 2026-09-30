# FarmTrace (Farm to Fork — Every Journey Verified)

Low-Cost IoT Blockchain Nodes for Farm-to-Fork Traceability in Agricultural Cold Chains.

## Overview
FarmTrace is an agricultural supply-chain traceability platform. It pairs low-cost, rugged ESP32-based multi-sensor nodes with cryptographic hash chaining and decentralized distributed ledger anchoring. It guarantees data integrity across farm harvest, refrigerated transit, storage, packaging, distribution, and retail.

### Problem Addressed
- **Cold-Chain Failure & Food Fraud**: Fresh produce (avocados, berries, seafood, table grapes) requires tight environmental control. Excursions in temperature and ethylene gas trigger accelerated spoilage.
- **Cellular Dropouts**: Standard loggers lose data during transit in remote rural agricultural zones. FarmTrace nodes feature **local AES-256 encrypted flash storage** that continuously logs environmental conditions and automatically synchronizes when connectivity is restored.
- **Data Tampering**: Rather than trusting centralized databases, every event is chained via `SHA256(prev_hash + event_id + device_id + batch_id + timestamp + sensor_data)` and anchored with verifiable cryptographic proofs.

---

## Architecture

```
[ ESP32 IoT Node ] (SHT31, MQ-138, INA219 Solar, NEO-6M GPS)
        │
        ├─ [Network Available] ──► MQTT over TLS (Port 8883) ──┐
        └─ [Network Dropout]   ──► AES-256 SPIFFS Flash Queue   │
                                         ▲                      │
                                         └── Reconnect Auto-Sync│
                                                                ▼
                                                    [ MQTT Broker (Mosquitto/EMQX) ]
                                                                │
                                                                ▼
                                                    [ FarmTrace Gateway Backend ]
                                                                │
                                        ┌───────────────────────┴───────────────────────┐
                                        ▼                                               ▼
                              [ PostgreSQL Database ]                         [ Hyperledger Fabric ]
                              (Sensor Time-Series & SLA)                     (Tamper-Evident Chain)
                                        │                                               │
                                        ▼                                               ▼
                              [ WebSocket Broadcaster ]                       [ Cryptographic Hash Engine ]
                                        │                                               │
                                        └───────────────────────┬───────────────────────┘
                                                                ▼
                                                [ FarmTrace Android & Web App ]
                                                (Real-time charts, verification, alerts)
```

---

## Supported Telemetry Metrics
- **Temperature (°C)**: SHT31 high-precision I2C digital sensor
- **Relative Humidity (% RH)**: SHT31
- **Ethylene Gas (C₂H₄ ppm)**: MQ-138 / Winsen ZE03 electrochemical gas sensor
- **Battery Level (%)**: Lithium Iron Phosphate (LiFePO4) battery state
- **Solar Energy Harvesting (V, mA, mWh)**: INA219 bidirectional voltage/current monitor
- **GPS Coordinates**: NEO-6M latitude and longitude coordinates
- **Network Quality**: Cellular / WiFi RSSI (dBm)

---

## MQTT Telemetry Payload Specification

Nodes publish telemetry to topic `farmtrace/{deviceId}/telemetry`:

```json
{
  "event_id": "EVT-ESP32-NODE-01-1727500120000",
  "device_id": "ESP32-NODE-01",
  "batch_id": "FT-BATCH-2026-0891",
  "timestamp": 1727500120000,
  "temperature": 4.8,
  "humidity": 89.2,
  "ethylene": 0.032,
  "battery": 94,
  "solar_voltage": 5.12,
  "solar_current": 180.5,
  "solar_energy": 924.1,
  "power_consumption": 45.0,
  "latitude": 34.0522,
  "longitude": -118.2437,
  "signal_strength": -68,
  "firmware_version": "v1.4.2",
  "signature": "MEQCIFz...cryptographic_signature"
}
```

---

## Cryptographic Hash Chaining

For every event:
```
current_hash = SHA256(
    previous_hash + "|" +
    event_id + "|" +
    device_id + "|" +
    batch_id + "|" +
    timestamp + "|" +
    sensor_data
)
```
The Android application includes a **Verify Integrity** engine that recalculates the SHA-256 digest byte-by-byte for every block in the sequence, comparing against the blockchain proof root to detect any unauthorized data modifications.

---

## Getting Started

### 1. Android Application
1. Configure `GEMINI_API_KEY` in the AI Studio Secrets panel.
2. Build and run the app.
3. Access Dashboard, Batches, Nodes, Ledger, Alerts, and Settings.

### 2. Docker Deployment
```bash
docker-compose up -d
```
Starts PostgreSQL (port 5432), Mosquitto MQTT Broker (ports 1883, 8083), and Backend API (port 4000/8080).

### 3. ESP32 Firmware
Flash `iot/esp32_farmtrace_node.ino` to any ESP32 development board using Arduino IDE or PlatformIO.
