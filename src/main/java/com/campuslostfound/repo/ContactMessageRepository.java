package com.campuslostfound.repo;

import com.campuslostfound.domain.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    @Query("select m from ContactMessage m where m.recipient.id = :userId order by m.createdAt desc")
    Page<ContactMessage> findInbox(@Param("userId") Long userId, Pageable pageable);

    @Query("select m from ContactMessage m where m.sender.id = :userId order by m.createdAt desc")
    Page<ContactMessage> findSent(@Param("userId") Long userId, Pageable pageable);
}
