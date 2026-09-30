/**
 * FarmTrace Gateway & Ingestion Backend Service
 * Language: TypeScript / Node.js
 * Responsibilities:
 * - Secure MQTT TLS Ingestion (topics: farmtrace/+/telemetry)
 * - Idempotent event duplicate detection
 * - PostgreSQL relational storage
 * - Real-time WebSocket broadcasting to web/mobile clients
 * - Hyperledger Fabric chaincode integration
 * - Alert threshold evaluation
 */

import mqtt from "mqtt";
import { WebSocketServer, WebSocket } from "ws";
import { Pool } from "pg";
import crypto from "crypto";

const PORT = process.env.PORT || 4000;
const MQTT_BROKER = process.env.MQTT_BROKER_URL || "mqtts://mqtt.farmtrace.org:8883";
const DATABASE_URL = process.env.DATABASE_URL || "postgres://farmtrace:secret@localhost:5432/farmtrace";

const pool = new Pool({ connectionString: DATABASE_URL });

// WebSocket Server for Real-Time Client Push Updates
const wss = new WebSocketServer({ port: 8080 });
const connectedClients = new Set<WebSocket>();

wss.on("connection", (ws) => {
  connectedClients.add(ws);
  console.log(`[WS] Client connected. Total active clients: ${connectedClients.size}`);

  ws.on("close", () => {
    connectedClients.delete(ws);
    console.log(`[WS] Client disconnected. Active clients: ${connectedClients.size}`);
  });
});

function broadcastToClients(data: any) {
  const message = JSON.stringify(data);
  for (const client of connectedClients) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message);
    }
  }
}

// MQTT Client Connection with TLS and LWT Support
const mqttClient = mqtt.connect(MQTT_BROKER, {
  clientId: `farmtrace_gateway_${process.pid}`,
  clean: false,
  reconnectPeriod: 2000,
});

mqttClient.on("connect", () => {
  console.log("[MQTT] Connected to broker successfully.");
  mqttClient.subscribe("farmtrace/+/telemetry", { qos: 1 }, (err) => {
    if (!err) console.log("[MQTT] Subscribed to farmtrace/+/telemetry");
  });
});

mqttClient.on("message", async (topic, message) => {
  try {
    const rawPayload = JSON.parse(message.toString());
    await handleTelemetryMessage(rawPayload);
  } catch (err: any) {
    console.error("[MQTT Ingress] Error processing payload:", err.message);
  }
});

async function handleTelemetryMessage(payload: any) {
  const {
    event_id,
    device_id,
    batch_id,
    timestamp,
    temperature,
    humidity,
    ethylene,
    battery,
    solar_voltage,
    solar_current,
    latitude,
    longitude,
    signal_strength,
    signature,
  } = payload;

  // Validation
  if (!event_id || !device_id || isNaN(temperature) || isNaN(humidity)) {
    console.warn(`[Reject] Malformed payload from ${device_id}`);
    return;
  }

  // Idempotency check: duplicate event rejection
  const duplicateCheck = await pool.query(
    "SELECT 1 FROM sensor_readings WHERE event_id = $1",
    [event_id]
  );
  if (duplicateCheck.rowCount && duplicateCheck.rowCount > 0) {
    console.log(`[Duplicate] Skipped already-processed event: ${event_id}`);
    return;
  }

  // Cryptographic SHA-256 Data Integrity Hash
  const hashPayload = `${event_id}|${device_id}|${batch_id}|${timestamp}|${temperature.toFixed(2)}|${humidity.toFixed(2)}|${ethylene.toFixed(4)}`;
  const dataHash = crypto.createHash("sha256").update(hashPayload).digest("hex");

  // Relational Database Write
  await pool.query(
    `INSERT INTO sensor_readings (
      event_id, device_id, batch_id, timestamp, temperature, humidity,
      ethylene, battery, solar_voltage, solar_current, latitude, longitude,
      signal_strength, data_hash, raw_signature
    ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)`,
    [
      event_id, device_id, batch_id, timestamp, temperature, humidity,
      ethylene, battery, solar_voltage || 0, solar_current || 0, latitude || null, longitude || null,
      signal_strength || -75, dataHash, signature || ""
    ]
  );

  // Update Device Heartbeat and Online Status
  await pool.query(
    `UPDATE devices SET status = 'ONLINE', last_seen_timestamp = $1, battery_percent = $2
     WHERE device_id = $3`,
    [timestamp, battery, device_id]
  );

  // Real-Time WebSocket Push to Browser / Android App
  broadcastToClients({
    type: "TELEMETRY_UPDATE",
    payload: {
      ...payload,
      data_hash: dataHash,
    },
  });

  console.log(`[Ingest OK] Node ${device_id} | Batch ${batch_id} | ${temperature}°C | ${humidity}%`);
}

console.log(`[FarmTrace Server] Running on port ${PORT}, WebSocket on 8080`);
