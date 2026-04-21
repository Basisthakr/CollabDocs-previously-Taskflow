package com.basisttha;

import com.basisttha.engine.Crdt;
import com.basisttha.engine.Item;
import com.basisttha.engine.ItemSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Crdt} — no Spring context, no database.
 *
 * <p>The CRDT algorithm being tested is inspired by YATA (Yet Another
 * Transformation Approach): each character is a node in a doubly-linked list
 * with a globally-unique ID {@code counter@clientId}.  Concurrent inserts at
 * the same position are ordered deterministically by clientId so every replica
 * converges to the same state.
 *
 * <h2>ID format</h2>
 * {@code counter@clientId} — e.g. {@code 3@alice}.
 * The counter is the client's local logical clock; the clientId identifies the
 * originating peer.  Two items with the same left-neighbour are ordered so that
 * the lexicographically <em>larger</em> clientId appears first.
 */
class CrdtTest {

    private Crdt crdt;

    @BeforeEach
    void setUp() {
        crdt = new Crdt();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Build an item whose left neighbour is already in {@code crdt}. */
    private Item item(String id, String content, String leftId) {
        return Item.builder()
                .id(id)
                .content(content)
                .left(leftId != null ? crdt.getItem(leftId) : null)
                .operation("insert")
                .build();
    }

    private void insert(String id, String content, String leftId) {
        crdt.insert(item(id, content, leftId));
    }

    // =========================================================================
    // Basic operations
    // =========================================================================

    @Nested
    @DisplayName("Basic operations")
    class BasicOps {

        @Test
        @DisplayName("New CRDT is empty")
        void emptyDocument() {
            assertThat(crdt.toString()).isEmpty();
            assertThat(crdt.getItems()).isEmpty();
        }

        @Test
        @DisplayName("Insert at beginning (null left)")
        void insertAtBeginning() {
            insert("0@alice", "a", null);
            assertThat(crdt.toString()).isEqualTo("a");
        }

        @Test
        @DisplayName("Sequential inserts produce correct order")
        void sequentialInserts() {
            insert("0@alice", "h", null);
            insert("1@alice", "e", "0@alice");
            insert("2@alice", "l", "1@alice");
            insert("3@alice", "l", "2@alice");
            insert("4@alice", "o", "3@alice");

            assertThat(crdt.toString()).isEqualTo("hello");
        }

        @Test
        @DisplayName("Insert in the middle of existing content")
        void insertInMiddle() {
            insert("0@alice", "a", null);
            insert("1@alice", "c", "0@alice");
            // Insert 'b' between 'a' (0@alice) and 'c' (1@alice)
            insert("2@alice", "b", "0@alice");

            assertThat(crdt.toString()).isEqualTo("abc");
        }

        @Test
        @DisplayName("getItem returns correct item by id")
        void getItemById() {
            insert("0@alice", "x", null);
            Item found = crdt.getItem("0@alice");

            assertThat(found).isNotNull();
            assertThat(found.getContent()).isEqualTo("x");
        }

    }

    // =========================================================================
    // Delete (tombstone)
    // =========================================================================

    @Nested
    @DisplayName("Tombstone deletion")
    class DeleteOps {

        @Test
        @DisplayName("Deleting an item hides it from toString() but keeps it in the list")
        void deleteHidesContent() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");
            insert("2@alice", "c", "1@alice");

            crdt.delete("1@alice");  // delete 'b'

            assertThat(crdt.toString()).isEqualTo("ac");
            assertThat(crdt.getItems()).hasSize(3); // tombstone is still there
        }

        @Test
        @DisplayName("Deleting the first item works correctly")
        void deleteFirstItem() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");

            crdt.delete("0@alice");

            assertThat(crdt.toString()).isEqualTo("b");
        }

        @Test
        @DisplayName("Deleting the last item works correctly")
        void deleteLastItem() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");

            crdt.delete("1@alice");

            assertThat(crdt.toString()).isEqualTo("a");
        }

        @Test
        @DisplayName("Inserting after a deleted item resolves correctly")
        void insertAfterDeletedItem() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");
            crdt.delete("1@alice");  // 'b' is now a tombstone

            // Insert 'c' after the tombstoned 'b'
            insert("2@alice", "c", "1@alice");

            assertThat(crdt.toString()).isEqualTo("ac");
            // Structural order: a(visible) → b(deleted) → c(visible)
            List<Item> items = crdt.getItems();
            assertThat(items.get(0).getContent()).isEqualTo("a");
            assertThat(items.get(1).isDeleted()).isTrue();
            assertThat(items.get(2).getContent()).isEqualTo("c");
        }

        @Test
        @DisplayName("getItems returns all items in order including tombstones")
        void getItemsIncludesTombstones() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");
            crdt.delete("0@alice");

            List<Item> items = crdt.getItems();
            assertThat(items).hasSize(2);
            assertThat(items.get(0).isDeleted()).isTrue();
            assertThat(items.get(1).isDeleted()).isFalse();
        }

        @Test
        @DisplayName("Deleting an unknown id is a no-op (idempotent)")
        void deleteUnknownIdIsNoOp() {
            insert("0@alice", "a", null);
            // Should not throw
            crdt.delete("nonexistent");
            assertThat(crdt.toString()).isEqualTo("a");
        }

        @Test
        @DisplayName("Deleting the same item twice is idempotent")
        void deleteTwiceIsIdempotent() {
            insert("0@alice", "a", null);
            crdt.delete("0@alice");
            crdt.delete("0@alice"); // second delete — should not crash
            assertThat(crdt.toString()).isEmpty();
        }
    }

    // =========================================================================
    // Formatting
    // =========================================================================

    @Nested
    @DisplayName("Formatting")
    class FormatOps {

        @Test
        @DisplayName("Bold and italic flags are set by format()")
        void formatSetsBoldItalic() {
            insert("0@alice", "a", null);
            crdt.format("0@alice", true, false);

            Item a = crdt.getItem("0@alice");
            assertThat(a.isBold()).isTrue();
            assertThat(a.isItalic()).isFalse();
        }

        @Test
        @DisplayName("Format can be toggled off")
        void formatToggleOff() {
            insert("0@alice", "a", null);
            crdt.format("0@alice", true, true);
            crdt.format("0@alice", false, false);

            Item a = crdt.getItem("0@alice");
            assertThat(a.isBold()).isFalse();
            assertThat(a.isItalic()).isFalse();
        }

        @Test
        @DisplayName("Formatting an unknown id is a no-op")
        void formatUnknownIdIsNoOp() {
            insert("0@alice", "a", null);
            // Should not throw
            crdt.format("nonexistent", true, true);
            assertThat(crdt.toString()).isEqualTo("a");
        }
    }

    // =========================================================================
    // Concurrent-insert conflict resolution (the core CRDT guarantee)
    // =========================================================================

    @Nested
    @DisplayName("Concurrent insert conflict resolution")
    class ConcurrentInserts {

        /**
         * Two clients insert at the same position concurrently.
         *
         * <p>Expected ordering rule: lexicographically larger clientId wins
         * (appears to the left).  "bob" > "alice" → Bob's character comes first.
         *
         * <p>This test verifies CONVERGENCE: regardless of which replica applies
         * the operations first, both end up with the same string.
         */
        @Test
        @DisplayName("Two-way concurrent insert: Alice-first replica")
        void twoWayConcurrentInsertAliceFirst() {
            // Setup: existing character 'x' at position 0
            insert("0@_", "x", null);

            // Alice inserts 'a' after 'x', Bob concurrently inserts 'b' after 'x'.
            // On Alice's replica, Alice applies first:
            insert("1@alice", "a", "0@_");
            insert("1@bob",   "b", "0@_");

            // "bob" > "alice" → Bob's 'b' appears before Alice's 'a'
            assertThat(crdt.toString()).isEqualTo("xba");
        }

        @Test
        @DisplayName("Two-way concurrent insert: Bob-first replica — same result")
        void twoWayConcurrentInsertBobFirst() {
            insert("0@_", "x", null);

            // On Bob's replica, Bob applies first:
            insert("1@bob",   "b", "0@_");
            insert("1@alice", "a", "0@_");

            // Must produce the same order as Alice-first replica
            assertThat(crdt.toString()).isEqualTo("xba");
        }

        @Test
        @DisplayName("Three-way concurrent insert converges regardless of arrival order")
        void threeWayConcurrentInsertAllPermutations() {
            // All three clients insert at the same position concurrently.
            // Expected: "charlie" > "bob" > "alice" → c, b, a
            String[][] orders = {
                {"charlie", "bob",     "alice"},
                {"charlie", "alice",   "bob"},
                {"bob",     "charlie", "alice"},
                {"bob",     "alice",   "charlie"},
                {"alice",   "charlie", "bob"},
                {"alice",   "bob",     "charlie"},
            };

            for (String[] order : orders) {
                Crdt local = new Crdt();
                local.insert(Item.builder().id("0@_").content("x").operation("insert").build());

                for (String client : order) {
                    String content = client.equals("alice") ? "a"
                                   : client.equals("bob")   ? "b" : "c";
                    local.insert(Item.builder()
                            .id("1@" + client)
                            .content(content)
                            .left(local.getItem("0@_"))
                            .operation("insert")
                            .build());
                }

                // "charlie" > "bob" > "alice" → c b a
                assertThat(local.toString())
                        .as("Failed for arrival order: %s %s %s", order[0], order[1], order[2])
                        .isEqualTo("xcba");
            }
        }

        @Test
        @DisplayName("Duplicate insert is idempotent — same id applied twice produces same state")
        void duplicateInsertIsIdempotent() {
            insert("0@alice", "a", null);
            insert("0@alice", "a", null);  // duplicate

            assertThat(crdt.toString()).isEqualTo("a");
            assertThat(crdt.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("Concurrent inserts interleaved with sequential inserts converge")
        void concurrentInterleavedWithSequential() {
            // Alice types "hi", Bob concurrently types "hey" starting from the same position.
            // Setup: existing text "x"
            insert("0@_", "x", null);

            // Alice: inserts 'h' then 'i' sequentially (left chain)
            insert("1@alice", "h", "0@_");
            insert("2@alice", "i", "1@alice");

            // Bob: concurrently inserts 'h','e','y' after "0@_" (same starting point)
            insert("1@bob", "h", "0@_");
            insert("2@bob", "e", "1@bob");
            insert("3@bob", "y", "2@bob");

            // Bob's characters come before Alice's at the conflict point ("bob" > "alice")
            // After 0@_, we get Bob's chain, then Alice's chain
            // x  → [bob: h,e,y] → [alice: h,i]
            assertThat(crdt.toString()).isEqualTo("xheyhi");
        }
    }

    // =========================================================================
    // Persistence: snapshot round-trip
    // =========================================================================

    @Nested
    @DisplayName("Snapshot round-trip (JSON persistence)")
    class Persistence {

        @Test
        @DisplayName("Empty CRDT produces empty snapshot list")
        void emptySnapshot() {
            assertThat(crdt.toSnapshots()).isEmpty();
        }

        @Test
        @DisplayName("Snapshot + initFromSnapshots preserves content")
        void roundTrip() {
            insert("0@alice", "h", null);
            insert("1@alice", "i", "0@alice");

            List<ItemSnapshot> snapshots = crdt.toSnapshots();
            assertThat(snapshots).hasSize(2);

            Crdt restored = new Crdt();
            restored.initFromSnapshots(snapshots);

            assertThat(restored.toString()).isEqualTo("hi");
        }

        @Test
        @DisplayName("Tombstoned items are stripped from snapshots (compaction)")
        void tombstonesAreStrippedFromSnapshots() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");
            insert("2@alice", "c", "1@alice");
            crdt.delete("1@alice"); // delete 'b'

            List<ItemSnapshot> snapshots = crdt.toSnapshots();

            // Only 'a' and 'c' survive compaction
            assertThat(snapshots).hasSize(2);
            assertThat(snapshots.get(0).content()).isEqualTo("a");
            assertThat(snapshots.get(1).content()).isEqualTo("c");
        }

        @Test
        @DisplayName("Snapshot IDs are rewritten to sequential index@_")
        void snapshotIdsAreRewritten() {
            insert("42@alice", "x", null);
            insert("99@bob",   "y", "42@alice");

            List<ItemSnapshot> snapshots = crdt.toSnapshots();

            assertThat(snapshots.get(0).id()).isEqualTo("0@_");
            assertThat(snapshots.get(1).id()).isEqualTo("1@_");
        }

        @Test
        @DisplayName("Snapshot left/right pointers are consistent")
        void snapshotPointersAreConsistent() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");
            insert("2@alice", "c", "1@alice");

            List<ItemSnapshot> snapshots = crdt.toSnapshots();

            // First item has no left neighbour
            assertThat(snapshots.get(0).leftId()).isNull();
            // Middle item links correctly
            assertThat(snapshots.get(1).leftId()).isEqualTo("0@_");
            assertThat(snapshots.get(1).rightId()).isEqualTo("2@_");
            // Last item has no right neighbour
            assertThat(snapshots.get(2).rightId()).isNull();
        }

        @Test
        @DisplayName("Round-trip allows new inserts after restore")
        void canInsertAfterRoundTrip() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");

            Crdt restored = new Crdt();
            restored.initFromSnapshots(crdt.toSnapshots());

            // Insert 'c' after the first item (now id "0@_" after compaction)
            Item c = Item.builder()
                    .id("2@alice")
                    .content("c")
                    .left(restored.getItem("0@_"))
                    .operation("insert")
                    .build();
            restored.insert(c);

            assertThat(restored.toString()).isEqualTo("acb");
        }

        @Test
        @DisplayName("initFromSnapshots replaces existing state")
        void initReplacesExistingState() {
            insert("0@alice", "old", null);
            assertThat(crdt.toString()).isEqualTo("old");

            Crdt fresh = new Crdt();
            fresh.insert(Item.builder().id("0@bob").content("new").operation("insert").build());

            crdt.initFromSnapshots(fresh.toSnapshots());
            assertThat(crdt.toString()).isEqualTo("new");
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Insert null item is a no-op")
        void insertNullItemIsNoOp() {
            crdt.insert(null);
            assertThat(crdt.toString()).isEmpty();
        }

        @Test
        @DisplayName("Long document with many characters is handled correctly")
        void longDocument() {
            String text = "the quick brown fox jumps over the lazy dog";
            for (int i = 0; i < text.length(); i++) {
                String leftId = i > 0 ? (i - 1) + "@alice" : null;
                insert(i + "@alice", String.valueOf(text.charAt(i)), leftId);
            }
            assertThat(crdt.toString()).isEqualTo(text);
        }

        @Test
        @DisplayName("Deleting all characters leaves empty visible content")
        void deleteAllCharacters() {
            insert("0@alice", "a", null);
            insert("1@alice", "b", "0@alice");
            insert("2@alice", "c", "1@alice");

            crdt.delete("0@alice");
            crdt.delete("1@alice");
            crdt.delete("2@alice");

            assertThat(crdt.toString()).isEmpty();
            assertThat(crdt.getItems()).hasSize(3); // tombstones remain
        }

        @Test
        @DisplayName("Multiple concurrent inserts at different positions do not interfere")
        void concurrentInsertsAtDifferentPositions() {
            insert("0@_", "a", null);
            insert("1@_", "c", "0@_");

            // Alice inserts 'x' after 'a', Bob inserts 'y' after 'c' — no conflict
            insert("1@alice", "x", "0@_");
            insert("1@bob",   "y", "1@_");

            assertThat(crdt.toString()).isEqualTo("axcy");
        }
    }
}
