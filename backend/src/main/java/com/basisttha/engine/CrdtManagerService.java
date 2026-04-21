package com.basisttha.engine;

import com.basisttha.repo.DocRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of in-memory {@link Crdt} instances.
 *
 * <p>One {@code Crdt} is kept in memory per open document.  When the first
 * subscriber joins a document it is loaded from the database; when the last
 * subscriber leaves it is serialised back to JSON and removed from memory.
 *
 * <p>Persistence format: the document content column stores UTF-8 encoded JSON
 * produced by {@link Crdt#toSnapshots()}.  This replaces the previous Java
 * serialisation approach, which was fragile (any class rename broke stored
 * documents) and a known deserialization-gadget attack surface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrdtManagerService {

    private final DocRepository docRepository;
    private final ObjectMapper objectMapper;

    /**
     * docId → live Crdt for that document.
     * ConcurrentHashMap protects map-level operations; per-Crdt mutations are
     * synchronised inside the Crdt itself.
     */
    private final ConcurrentHashMap<Long, Crdt> crdtMap = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns the live {@link Crdt} for the given document, or {@code null}. */
    public Crdt getCrdt(Long docId) {
        return crdtMap.get(docId);
    }

    /**
     * Ensures a {@link Crdt} exists in memory for {@code docId}, loading it
     * from the database if necessary.
     *
     * <p>Uses {@link ConcurrentHashMap#computeIfAbsent} so that two threads
     * racing to initialise the same document will only ever execute the loader
     * lambda once — eliminating the TOCTOU race that existed in the previous
     * {@code containsKey / put} pattern.
     */
    public void createCrdt(Long docId) {
        crdtMap.computeIfAbsent(docId, id -> {
            log.info("Loading CRDT for document {} from database", id);
            Crdt crdt = new Crdt();
            try {
                docRepository.findById(id).ifPresent(doc -> {
                    byte[] raw = doc.getContent();
                    if (raw != null && raw.length > 0) {
                        crdt.initFromSnapshots(deserializeSnapshots(raw));
                    }
                });
            } catch (Exception e) {
                log.error("Failed to load CRDT for document {}: {}", id, e.getMessage(), e);
            }
            return crdt;
        });
    }

    /**
     * Saves every live CRDT to the database every 30 seconds.
     * This ensures content survives a backend restart even without an explicit save.
     */
    @Scheduled(fixedDelay = 30_000)
    public void autoSaveAll() {
        if (crdtMap.isEmpty()) return;
        log.info("Auto-save: persisting {} open document(s)", crdtMap.size());
        for (Long docId : crdtMap.keySet()) {
            saveCrdt(docId);
        }
    }

    /**
     * Serialises the {@link Crdt} for {@code docId} to JSON and persists it to
     * the database WITHOUT removing it from memory.
     *
     * <p>Called periodically or on explicit user save while editing is still active.
     */
    public void saveCrdt(Long docId) {
        Crdt crdt = crdtMap.get(docId);
        if (crdt == null) {
            log.debug("saveCrdt called for {} but no live Crdt found", docId);
            return;
        }
        log.info("Persisting (checkpoint) CRDT for document {}", docId);
        byte[] jsonBytes = serializeSnapshots(crdt.toSnapshots());
        docRepository.updateContent(docId, jsonBytes);
        log.info("CRDT checkpoint for document {} saved ({} bytes)", docId, jsonBytes.length);
    }

    /**
     * Serialises the {@link Crdt} for {@code docId} to JSON, persists it to the
     * database, and removes it from the in-memory map.
     *
     * <p>Called when the last WebSocket subscriber leaves a document.
     */
    public void saveAndDeleteCrdt(Long docId) {
        Crdt crdt = crdtMap.remove(docId);
        if (crdt == null) {
            log.debug("saveAndDeleteCrdt called for {} but no live Crdt found — nothing to save", docId);
            return;
        }
        log.info("Persisting CRDT for document {} to database", docId);
        try {
            byte[] jsonBytes = serializeSnapshots(crdt.toSnapshots());
            docRepository.updateContent(docId, jsonBytes);
            log.info("CRDT for document {} saved ({} bytes)", docId, jsonBytes.length);
        } catch (Exception e) {
            log.error("Failed to persist CRDT for document {}: {}", docId, e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Serialisation helpers
    // -------------------------------------------------------------------------

    private byte[] serializeSnapshots(List<ItemSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize CRDT snapshots", e);
        }
    }

    private List<ItemSnapshot> deserializeSnapshots(byte[] raw) {
        try {
            String json = new String(raw, StandardCharsets.UTF_8);
            // Guard against legacy Java-serialised bytes that start with 0xACED
            if (json.charAt(0) != '[' && json.charAt(0) != '{') {
                log.warn("Content looks like legacy Java-serialised data — treating document as empty");
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<ItemSnapshot>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize CRDT snapshots: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
