package com.campuslostfound.repo;

import com.campuslostfound.domain.ContactMessage;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    @Query(value = """
            select m from ContactMessage m
            join fetch m.listing join fetch m.sender join fetch m.recipient
            where m.recipient.id = :userId
            """,
            countQuery = "select count(m) from ContactMessage m where m.recipient.id = :userId")
    Page<ContactMessage> findInbox(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            select m from ContactMessage m
            join fetch m.listing join fetch m.sender join fetch m.recipient
            where m.sender.id = :userId
            """,
            countQuery = "select count(m) from ContactMessage m where m.sender.id = :userId")
    Page<ContactMessage> findSent(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        select m from ContactMessage m
        join fetch m.listing join fetch m.sender join fetch m.recipient
        where m.id = :id
        """)
    Optional<ContactMessage> findByIdWithRefs(@Param("id") Long id);
}
