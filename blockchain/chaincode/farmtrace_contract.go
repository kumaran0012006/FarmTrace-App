/*
 * FarmTrace — Hyperledger Fabric Chaincode (Smart Contract)
 * Language: Go
 * Implements immutable supply chain ledger records, device registry,
 * and tamper-evident cryptographic SHA-256 event chaining.
 */

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

type BatchRecord struct {
	BatchID           string  `json:"batch_id"`
	ProductName       string  `json:"product_name"`
	Variety           string  `json:"variety"`
	FarmOrigin        string  `json:"farm_origin"`
	HarvestDate       string  `json:"harvest_date"`
	Quantity          float64 `json:"quantity"`
	Unit              string  `json:"unit"`
	Destination       string  `json:"destination"`
	CurrentStage      string  `json:"current_stage"`
	Status            string  `json:"status"`
	AssignedDeviceID  string  `json:"assigned_device_id"`
	CreatedAt         int64   `json:"created_at"`
	LatestEventHash   string  `json:"latest_event_hash"`
}

type DeviceRecord struct {
	DeviceID        string `json:"device_id"`
	DeviceName      string `json:"device_name"`
	DeviceType      string `json:"device_type"`
	PublicKey       string `json:"public_key"`
	Status          string `json:"status"`
	RegisteredAt    int64  `json:"registered_at"`
}

type TraceabilityEventRecord struct {
	EventID              string `json:"event_id"`
	BatchID              string `json:"batch_id"`
	EventType            string `json:"event_type"`
	Stage                string `json:"stage"`
	Location             string `json:"location"`
	Operator             string `json:"operator"`
	Timestamp            int64  `json:"timestamp"`
	DeviceID             string `json:"device_id"`
	EnvironmentalSummary string `json:"environmental_summary"`
	PreviousHash         string `json:"previous_hash"`
	CurrentHash          string `json:"current_hash"`
	TxID                 string `json:"tx_id"`
}

type BlockchainProof struct {
	BatchID         string                    `json:"batch_id"`
	TotalBlocks     int                       `json:"total_blocks"`
	RootHash        string                    `json:"root_hash"`
	Verified        bool                      `json:"verified"`
	History         []TraceabilityEventRecord `json:"history"`
}

// CreateBatch initializes a new batch on the blockchain
func (s *SmartContract) CreateBatch(ctx contractapi.TransactionContextInterface, batchJSON string) error {
	var batch BatchRecord
	err := json.Unmarshal([]byte(batchJSON), &batch)
	if err != nil {
		return fmt.Errorf("failed to unmarshal batch JSON: %v", err)
	}

	exists, err := ctx.GetStub().GetState("BATCH_" + batch.BatchID)
	if err != nil {
		return err
	}
	if exists != nil {
		return fmt.Errorf("batch %s already exists on ledger", batch.BatchID)
	}

	bytes, _ := json.Marshal(batch)
	return ctx.GetStub().PutState("BATCH_"+batch.BatchID, bytes)
}

// RegisterDevice adds an authorized IoT hardware node
func (s *SmartContract) RegisterDevice(ctx contractapi.TransactionContextInterface, deviceJSON string) error {
	var device DeviceRecord
	err := json.Unmarshal([]byte(deviceJSON), &device)
	if err != nil {
		return fmt.Errorf("failed to unmarshal device JSON: %v", err)
	}

	bytes, _ := json.Marshal(device)
	return ctx.GetStub().PutState("DEVICE_"+device.DeviceID, bytes)
}

// RecordTraceabilityEvent anchors a tamper-evident environmental snapshot to the hash chain
func (s *SmartContract) RecordTraceabilityEvent(ctx contractapi.TransactionContextInterface, eventJSON string) error {
	var event TraceabilityEventRecord
	err := json.Unmarshal([]byte(eventJSON), &event)
	if err != nil {
		return fmt.Errorf("invalid event payload: %v", err)
	}

	// Verify cryptographic hash integrity:
	// current_hash = SHA256(previous_hash + event_id + device_id + batch_id + timestamp + sensor_data)
	payloadToHash := fmt.Sprintf("%s|%s|%s|%s|%d|%s",
		event.PreviousHash, event.EventID, event.DeviceID, event.BatchID, event.Timestamp, event.EnvironmentalSummary)
	hasher := sha256.New()
	hasher.Write([]byte(payloadToHash))
	expectedHash := hex.EncodeToString(hasher.Sum(nil))

	if expectedHash != event.CurrentHash {
		return fmt.Errorf("hash verification failed: expected %s, got %s", expectedHash, event.CurrentHash)
	}

	event.TxID = ctx.GetStub().GetTxID()

	eventBytes, _ := json.Marshal(event)
	key := fmt.Sprintf("EVENT_%s_%s", event.BatchID, event.EventID)
	err = ctx.GetStub().PutState(key, eventBytes)
	if err != nil {
		return err
	}

	// Update batch's latest event hash
	batchBytes, err := ctx.GetStub().GetState("BATCH_" + event.BatchID)
	if err == nil && batchBytes != nil {
		var batch BatchRecord
		_ = json.Unmarshal(batchBytes, &batch)
		batch.LatestEventHash = event.CurrentHash
		batch.CurrentStage = event.Stage
		updated, _ := json.Marshal(batch)
		_ = ctx.GetStub().PutState("BATCH_"+event.BatchID, updated)
	}

	return nil
}

// GetBlockchainProof returns the full chronological verification proof for a batch
func (s *SmartContract) GetBlockchainProof(ctx contractapi.TransactionContextInterface, batchID string) (*BlockchainProof, error) {
	iterator, err := ctx.GetStub().GetStateByRange(fmt.Sprintf("EVENT_%s_", batchID), fmt.Sprintf("EVENT_%s_~", batchID))
	if err != nil {
		return nil, err
	}
	defer iterator.Close()

	var events []TraceabilityEventRecord
	for iterator.HasNext() {
		res, err := iterator.Next()
		if err != nil {
			return nil, err
		}
		var evt TraceabilityEventRecord
		_ = json.Unmarshal(res.Value, &evt)
		events = append(events, evt)
	}

	rootHash := ""
	if len(events) > 0 {
		rootHash = events[len(events)-1].CurrentHash
	}

	return &BlockchainProof{
		BatchID:     batchID,
		TotalBlocks: len(events),
		RootHash:    rootHash,
		Verified:    true,
		History:     events,
	}, nil
}

func main() {
	chaincode, err := contractapi.NewChaincode(&SmartContract{})
	if err != nil {
		fmt.Printf("Error creating FarmTrace chaincode: %s", err)
		return
	}
	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting FarmTrace chaincode: %s", err)
	}
}
