package com.basisttha.repo;

import com.basisttha.model.Doc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocRepository extends JpaRepository<Doc, Long> {

    @Query("""
    select d from Doc d
    where d.owner.username = ?1 or
    d in (select ud.doc from UserDoc ud where ud.user.username = ?1)
""")
    List<Doc> findByUsername(String username);

    @Transactional
    @Modifying
    @Query(value = "UPDATE docs SET content = :content WHERE id = :docId", nativeQuery = true)
    void updateContent(@Param("docId") Long docId, @Param("content") byte[] content);
}
