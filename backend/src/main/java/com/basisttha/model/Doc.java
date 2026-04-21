package com.basisttha.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "docs")
public class Doc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "username")
    private User owner;

    @Column(nullable = false)
    private String title;

    /**
     * CRDT state serialised as UTF-8 JSON bytes.
     *
     * <p>We store raw bytes (PostgreSQL {@code bytea}) rather than a TEXT column
     * so the column type is unchanged from the original schema — no migration
     * required.  The bytes contain UTF-8 encoded JSON produced by
     * {@code CrdtManagerService.saveAndDeleteCrdt()}.
     *
     * <p>{@code @Lob} was removed: in Hibernate 6 + PostgreSQL, {@code @Lob} on
     * a {@code byte[]} field maps to the large-object ({@code oid}) type, which
     * requires separate pg_largeobject table rows and complicates transactions.
     * A plain {@code bytea} column is simpler and sufficient for document sizes
     * up to ~1 GB.
     */
    @Column(name = "content", columnDefinition = "bytea")
    private byte[] content;

    @OneToMany(mappedBy = "doc", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserDoc> sharedWith;
}
