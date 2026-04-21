package com.basisttha.engine;

/**
 * Flat, serialisation-friendly view of an {@link Item} used when persisting the
 * CRDT to the database as JSON.  Using a record avoids circular-reference issues
 * that would arise from serialising the doubly-linked {@link Item} directly.
 */
public record ItemSnapshot(
        String id,
        String content,
        String leftId,
        String rightId,
        String operation,
        boolean deleted,
        boolean bold,
        boolean italic,
        boolean underline,
        boolean strike,
        Integer header,
        String align,
        String list,
        int indent,
        String color,
        String background,
        String link
) {}
