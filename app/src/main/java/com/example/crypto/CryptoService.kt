package com.example.crypto

import com.example.data.model.TraceabilityEvent
import java.security.MessageDigest

data class ChainNodeVerification(
    val eventId: String,
    val stage: String,
    val storedHash: String,
    val computedHash: String,
    val isMatch: Boolean,
    val previousHash: String
)

data class ChainVerificationResult(
    val isValid: Boolean,
    val totalNodesChecked: Int,
    val verifiedNodesCount: Int,
    val brokenNodeId: String? = null,
    val failureReason: String? = null,
    val nodeAuditTrail: List<ChainNodeVerification> = emptyList()
)

object CryptoService {
    const val GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Tamper-evident event chaining:
     * current_hash = SHA256(previous_hash + event_id + device_id + batch_id + timestamp + sensor_data)
     */
    fun computeEventHash(
        previousHash: String,
        eventId: String,
        deviceId: String,
        batchId: String,
        timestamp: Long,
        sensorData: String
    ): String {
        val payload = "$previousHash|$eventId|$deviceId|$batchId|$timestamp|$sensorData"
        return sha256(payload)
    }

    /**
     * Computes reading payload hash for data integrity
     */
    fun computeReadingHash(
        eventId: String,
        deviceId: String,
        batchId: String,
        timestamp: Long,
        temp: Double,
        humidity: Double,
        ethylene: Double
    ): String {
        val data = "$eventId|$deviceId|$batchId|$timestamp|%.2f|%.2f|%.3f".format(temp, humidity, ethylene)
        return sha256(data)
    }

    /**
     * Deterministic pseudo-transaction ID based on hash and timestamp
     */
    fun computeBlockchainTxId(currentHash: String, timestamp: Long): String {
        return "0x" + sha256("$currentHash:$timestamp").take(32)
    }

    /**
     * Real-time verification of the entire cryptographic event chain for a batch.
     * Recomputes hashes link by link and compares with recorded values.
     */
    fun verifyChain(events: List<TraceabilityEvent>): ChainVerificationResult {
        if (events.isEmpty()) {
            return ChainVerificationResult(
                isValid = true,
                totalNodesChecked = 0,
                verifiedNodesCount = 0,
                failureReason = "No events logged for batch yet."
            )
        }

        // Sort events chronologically
        val sorted = events.sortedBy { it.timestamp }
        val auditTrail = mutableListOf<ChainNodeVerification>()
        var previousExpectedHash = GENESIS_PREV_HASH

        for (i in sorted.indices) {
            val event = sorted[i]

            // If not genesis, the previousHash must match previous node's currentHash
            if (i > 0) {
                if (event.previousHash != previousExpectedHash) {
                    auditTrail.add(
                        ChainNodeVerification(
                            eventId = event.eventId,
                            stage = event.stage.displayName,
                            storedHash = event.currentHash,
                            computedHash = "BROKEN_LINK_EXPECTED_$previousExpectedHash",
                            isMatch = false,
                            previousHash = event.previousHash
                        )
                    )
                    return ChainVerificationResult(
                        isValid = false,
                        totalNodesChecked = sorted.size,
                        verifiedNodesCount = i,
                        brokenNodeId = event.eventId,
                        failureReason = "Chain link broken at event ${event.eventId}: previous_hash does not match parent event hash.",
                        nodeAuditTrail = auditTrail
                    )
                }
            }

            val computed = computeEventHash(
                previousHash = event.previousHash,
                eventId = event.eventId,
                deviceId = event.deviceId,
                batchId = event.batchId,
                timestamp = event.timestamp,
                sensorData = event.environmentalConditions
            )

            val isMatch = computed.equals(event.currentHash, ignoreCase = true)
            auditTrail.add(
                ChainNodeVerification(
                    eventId = event.eventId,
                    stage = event.stage.displayName,
                    storedHash = event.currentHash,
                    computedHash = computed,
                    isMatch = isMatch,
                    previousHash = event.previousHash
                )
            )

            if (!isMatch) {
                return ChainVerificationResult(
                    isValid = false,
                    totalNodesChecked = sorted.size,
                    verifiedNodesCount = i,
                    brokenNodeId = event.eventId,
                    failureReason = "Integrity violation at ${event.stage.displayName} (${event.eventId}): recomputed SHA-256 does not match recorded block hash.",
                    nodeAuditTrail = auditTrail
                )
            }

            previousExpectedHash = event.currentHash
        }

        return ChainVerificationResult(
            isValid = true,
            totalNodesChecked = sorted.size,
            verifiedNodesCount = sorted.size,
            nodeAuditTrail = auditTrail
        )
    }

    // ==========================================
    // Asymmetric Cryptographic Signatures (ECDSA)
    // ==========================================

    private val ecKeyFactory by lazy { java.security.KeyFactory.getInstance("EC") }
    private val defaultAuthorityKeyPair by lazy { generateEcdsaKeyPair() }

    fun generateEcdsaKeyPair(): java.security.KeyPair {
        val keyGen = java.security.KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        return keyGen.generateKeyPair()
    }

    fun getDefaultAuthorityPublicKey(): String {
        return android.util.Base64.encodeToString(
            defaultAuthorityKeyPair.public.encoded,
            android.util.Base64.NO_WRAP
        )
    }

    fun signData(privateKey: java.security.PrivateKey, data: String): String {
        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(data.toByteArray(Charsets.UTF_8))
        val signatureBytes = signer.sign()
        return android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP)
    }

    fun signWithDefaultAuthority(data: String): String {
        return signData(defaultAuthorityKeyPair.private, data)
    }

    fun verifyEcdsaSignature(publicKeyBase64: String, data: String, signatureBase64: String): Boolean {
        return try {
            val keyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.DEFAULT)
            val spec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val publicKey = ecKeyFactory.generatePublic(spec)

            val verifier = java.security.Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(data.toByteArray(Charsets.UTF_8))
            val sigBytes = android.util.Base64.decode(signatureBase64, android.util.Base64.DEFAULT)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    fun computeCanonicalNodeData(
        previousHash: String,
        eventId: String,
        deviceId: String,
        batchId: String,
        timestamp: Long,
        stage: String,
        operator: String,
        environmentalSummary: String
    ): String {
        return "$previousHash|$eventId|$deviceId|$batchId|$timestamp|$stage|$operator|$environmentalSummary"
    }

    /**
     * Comprehensive cryptographic verification of a supply chain node.
     * Verifies:
     * 1. Hash integrity (recomputing canonical SHA-256)
     * 2. Cryptographic signature authenticity via ECDSA P-256
     */
    fun verifySupplyChainNode(payload: SupplyChainNodePayload): NodeVerificationReport {
        val canonical = computeCanonicalNodeData(
            previousHash = payload.previousHash,
            eventId = payload.eventId,
            deviceId = payload.deviceId,
            batchId = payload.batchId,
            timestamp = payload.timestamp,
            stage = payload.stage,
            operator = payload.operator,
            environmentalSummary = payload.environmentalConditions
        )
        val computedHash = sha256(canonical)
        val hashValid = computedHash.equals(payload.currentHash, ignoreCase = true)

        val signatureValid = if (payload.algorithm.contains("ECDSA", ignoreCase = true)) {
            verifyEcdsaSignature(payload.publicKey, canonical, payload.signature)
        } else {
            // HMAC fallback verification
            val hmacExpected = sha256("${payload.publicKey}:$canonical")
            hmacExpected.equals(payload.signature, ignoreCase = true)
        }

        val keyFingerprint = if (payload.publicKey.isNotEmpty()) {
            sha256(payload.publicKey).take(16).chunked(4).joinToString(":")
        } else {
            "UNKNOWN"
        }

        val isValid = hashValid && signatureValid
        val failureReason = when {
            !hashValid && !signatureValid -> "CRITICAL: Hash mismatch and invalid digital signature. Node payload was tampered."
            !hashValid -> "INTEGRITY ALERT: Recomputed SHA-256 hash does not match payload hash. Environmental or stage data modified."
            !signatureValid -> "AUTHENTICITY ALERT: Cryptographic signature verification failed. Public key did not sign this node."
            else -> null
        }

        return NodeVerificationReport(
            isValid = isValid,
            signatureValid = signatureValid,
            hashValid = hashValid,
            algorithm = payload.algorithm,
            keyFingerprint = keyFingerprint,
            payload = payload,
            computedHash = computedHash,
            failureReason = failureReason,
            authorityName = if (payload.publicKey == getDefaultAuthorityPublicKey()) {
                "FarmTrace Certified Root Authority"
            } else {
                "Independent Node Key (Fingerprint: $keyFingerprint)"
            }
        )
    }

    /**
     * Parses raw QR code string (JSON, URI, or pipe-separated) into SupplyChainNodePayload
     */
    fun parseNodeQrPayload(rawContent: String): SupplyChainNodePayload? {
        val trimmed = rawContent.trim()

        // 1. JSON Format
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseJsonPayload(trimmed)
        }

        // 2. URI Format: farmtrace://verify-node?...
        if (trimmed.startsWith("farmtrace://", ignoreCase = true) || trimmed.startsWith("http", ignoreCase = true)) {
            return parseUriPayload(trimmed)
        }

        // 3. Pipe-separated legacy format
        if (trimmed.contains("|")) {
            val parts = trimmed.split("|")
            if (parts.size >= 8) {
                return SupplyChainNodePayload(
                    eventId = parts[0],
                    batchId = parts.getOrElse(1) { "UNKNOWN" },
                    deviceId = parts.getOrElse(2) { "NODE-01" },
                    stage = parts.getOrElse(3) { "HARVEST" },
                    operator = parts.getOrElse(4) { "Farm Operator" },
                    timestamp = parts.getOrElse(5) { System.currentTimeMillis().toString() }.toLongOrNull() ?: System.currentTimeMillis(),
                    location = parts.getOrElse(6) { "Facility" },
                    environmentalConditions = parts.getOrElse(7) { "Safe" },
                    previousHash = parts.getOrElse(8) { GENESIS_PREV_HASH },
                    currentHash = parts.getOrElse(9) { "" },
                    publicKey = parts.getOrElse(10) { getDefaultAuthorityPublicKey() },
                    signature = parts.getOrElse(11) { "" },
                    algorithm = "SHA256withECDSA"
                )
            }
        }

        // 4. Fallback for raw batch ID or event ID: build a queryable placeholder node
        if (trimmed.isNotBlank()) {
            return SupplyChainNodePayload(
                eventId = if (trimmed.startsWith("EVT-")) trimmed else "EVT-NODE-${trimmed.takeLast(4)}",
                batchId = if (trimmed.startsWith("BATCH-")) trimmed else trimmed,
                deviceId = "NODE-GATEWAY-SCAN",
                stage = "CHECKPOINT",
                operator = "Field Inspector",
                timestamp = System.currentTimeMillis(),
                location = "Field Scan Location",
                environmentalConditions = "Verified At Handoff",
                previousHash = GENESIS_PREV_HASH,
                currentHash = "",
                publicKey = getDefaultAuthorityPublicKey(),
                signature = "",
                algorithm = "SHA256withECDSA"
            )
        }

        return null
    }

    private fun parseJsonPayload(json: String): SupplyChainNodePayload? {
        fun extract(key: String): String {
            val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            val match = pattern.find(json)
            if (match != null) return match.groupValues[1]

            // Number pattern
            val numPattern = Regex("\"$key\"\\s*:\\s*([0-9]+)")
            return numPattern.find(json)?.groupValues?.get(1) ?: ""
        }

        val eventId = extract("eventId").ifEmpty { extract("id") }
        if (eventId.isEmpty() && extract("batchId").isEmpty()) return null

        val batchId = extract("batchId").ifEmpty { "BATCH-UNKNOWN" }
        val deviceId = extract("deviceId").ifEmpty { "NODE-UNKNOWN" }
        val stage = extract("stage").ifEmpty { "HARVEST" }
        val operator = extract("operator").ifEmpty { "Operator" }
        val timestamp = extract("timestamp").toLongOrNull() ?: System.currentTimeMillis()
        val location = extract("location").ifEmpty { "Unknown GPS" }
        val env = extract("environmentalConditions").ifEmpty { extract("env") }
        val previousHash = extract("previousHash").ifEmpty { GENESIS_PREV_HASH }
        val currentHash = extract("currentHash").ifEmpty { extract("payloadHash") }
        val publicKey = extract("publicKey")
        val signature = extract("signature")
        val algorithm = extract("algorithm").ifEmpty { "SHA256withECDSA" }

        return SupplyChainNodePayload(
            eventId = eventId,
            batchId = batchId,
            deviceId = deviceId,
            stage = stage,
            operator = operator,
            timestamp = timestamp,
            location = location,
            environmentalConditions = env,
            previousHash = previousHash,
            currentHash = currentHash,
            publicKey = publicKey,
            signature = signature,
            algorithm = algorithm
        )
    }

    private fun parseUriPayload(uri: String): SupplyChainNodePayload? {
        val query = uri.substringAfter("?", "")
        val params = query.split("&").associate {
            val parts = it.split("=")
            val k = parts.getOrElse(0) { "" }
            val v = java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            k to v
        }
        val eventId = params["eventId"] ?: params["id"] ?: "EVT-SCAN-${System.currentTimeMillis() % 1000}"
        val batchId = params["batchId"] ?: "BATCH-2026-APL-001"
        return SupplyChainNodePayload(
            eventId = eventId,
            batchId = batchId,
            deviceId = params["deviceId"] ?: "NODE-01",
            stage = params["stage"] ?: "HARVEST",
            operator = params["operator"] ?: "Authority Inspector",
            timestamp = params["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
            location = params["location"] ?: "Checkpoint",
            environmentalConditions = params["env"] ?: "Optimal",
            previousHash = params["previousHash"] ?: GENESIS_PREV_HASH,
            currentHash = params["hash"] ?: "",
            publicKey = params["publicKey"] ?: getDefaultAuthorityPublicKey(),
            signature = params["sig"] ?: "",
            algorithm = params["alg"] ?: "SHA256withECDSA"
        )
    }

    /**
     * Generates a signed, verifiable JSON QR payload for any node or event
     */
    fun createSignedNodePayload(
        eventId: String,
        batchId: String,
        deviceId: String,
        stage: String,
        operator: String,
        timestamp: Long = System.currentTimeMillis(),
        location: String,
        environmentalConditions: String,
        previousHash: String = GENESIS_PREV_HASH
    ): SupplyChainNodePayload {
        val canonical = computeCanonicalNodeData(
            previousHash = previousHash,
            eventId = eventId,
            deviceId = deviceId,
            batchId = batchId,
            timestamp = timestamp,
            stage = stage,
            operator = operator,
            environmentalSummary = environmentalConditions
        )
        val currentHash = sha256(canonical)
        val signature = signWithDefaultAuthority(canonical)
        val publicKey = getDefaultAuthorityPublicKey()

        return SupplyChainNodePayload(
            eventId = eventId,
            batchId = batchId,
            deviceId = deviceId,
            stage = stage,
            operator = operator,
            timestamp = timestamp,
            location = location,
            environmentalConditions = environmentalConditions,
            previousHash = previousHash,
            currentHash = currentHash,
            publicKey = publicKey,
            signature = signature,
            algorithm = "SHA256withECDSA"
        )
    }

    /**
     * Serializes a SupplyChainNodePayload into a clean JSON string for QR encoding
     */
    fun serializeNodePayloadToJson(payload: SupplyChainNodePayload): String {
        return """
        {
          "v": 1,
          "type": "FARMTRACE_NODE",
          "eventId": "${payload.eventId}",
          "batchId": "${payload.batchId}",
          "deviceId": "${payload.deviceId}",
          "stage": "${payload.stage}",
          "operator": "${payload.operator}",
          "timestamp": ${payload.timestamp},
          "location": "${payload.location}",
          "environmentalConditions": "${payload.environmentalConditions}",
          "previousHash": "${payload.previousHash}",
          "currentHash": "${payload.currentHash}",
          "publicKey": "${payload.publicKey}",
          "signature": "${payload.signature}",
          "algorithm": "${payload.algorithm}"
        }
        """.trimIndent()
    }

    /**
     * Prepares realistic sample verifiable nodes for instant emulator testing
     */
    fun getSampleVerifiableNodes(): List<Pair<String, SupplyChainNodePayload>> {
        val orchardNode = createSignedNodePayload(
            eventId = "EVT-ORCHARD-701",
            batchId = "BATCH-2026-APL-001",
            deviceId = "ESP32-ORCHARD-ALPHA",
            stage = "HARVEST",
            operator = "Sunrise Organic Orchards (Cert #US-8842)",
            timestamp = System.currentTimeMillis() - 86400000L * 3,
            location = "Yakima Valley, WA (46.6021° N, 120.5059° W)",
            environmentalConditions = "Temp: 3.4°C | RH: 88.5% | Ethylene: 0.012ppm",
            previousHash = GENESIS_PREV_HASH
        )

        val coldStorageNode = createSignedNodePayload(
            eventId = "EVT-COLDHUB-702",
            batchId = "BATCH-2026-APL-001",
            deviceId = "COLD-GATEWAY-04",
            stage = "COLD_STORAGE",
            operator = "Cascade Cold Storage & Hydrocooling",
            timestamp = System.currentTimeMillis() - 86400000L * 2,
            location = "Selah Cold Chain Facility (46.6542° N, 120.5312° W)",
            environmentalConditions = "Temp: 1.8°C | RH: 92.0% | Ethylene: 0.018ppm",
            previousHash = orchardNode.currentHash
        )

        val reeferTransitNode = createSignedNodePayload(
            eventId = "EVT-TRANSIT-703",
            batchId = "BATCH-2026-APL-001",
            deviceId = "REEFER-TELEMATICS-09",
            stage = "IN_TRANSIT",
            operator = "Nordic Cold Logistics Fleet #14",
            timestamp = System.currentTimeMillis() - 86400000L,
            location = "I-90 Eastbound Transit Mile 108",
            environmentalConditions = "Temp: 2.2°C | RH: 90.1% | Ethylene: 0.021ppm",
            previousHash = coldStorageNode.currentHash
        )

        // Intentionally tampered node to demonstrate detection of fraud/tampering
        val tamperedNode = SupplyChainNodePayload(
            eventId = "EVT-TAMPERED-704",
            batchId = "BATCH-2026-APL-001",
            deviceId = "ROGUE-DEVICE-X",
            stage = "RETAIL_STORE",
            operator = "Forged Logistics Partner",
            timestamp = System.currentTimeMillis(),
            location = "Unknown Distribution Dock",
            environmentalConditions = "Temp: 18.5°C (TAMPERED TEMPERATURE)",
            previousHash = reeferTransitNode.currentHash,
            currentHash = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            publicKey = getDefaultAuthorityPublicKey(),
            signature = "MEQCIQD9FakeSignatureBytesForTestingTamperedPayloadDetection0123456789abcdef==",
            algorithm = "SHA256withECDSA"
        )

        return listOf(
            "Genesis Farm Node (Harvest)" to orchardNode,
            "Cold Storage Hub Node" to coldStorageNode,
            "Reefer Transit Node" to reeferTransitNode,
            "Tampered Node (Excursion & Forged Sig)" to tamperedNode
        )
    }
}

data class SupplyChainNodePayload(
    val eventId: String,
    val batchId: String,
    val deviceId: String,
    val stage: String,
    val operator: String,
    val timestamp: Long,
    val location: String,
    val environmentalConditions: String,
    val previousHash: String,
    val currentHash: String,
    val publicKey: String,
    val signature: String,
    val algorithm: String = "SHA256withECDSA"
)

data class NodeVerificationReport(
    val isValid: Boolean,
    val signatureValid: Boolean,
    val hashValid: Boolean,
    val algorithm: String,
    val keyFingerprint: String,
    val payload: SupplyChainNodePayload,
    val computedHash: String,
    val failureReason: String? = null,
    val authorityName: String = "FarmTrace Verified Authority",
    val verificationTimestamp: Long = System.currentTimeMillis()
)
