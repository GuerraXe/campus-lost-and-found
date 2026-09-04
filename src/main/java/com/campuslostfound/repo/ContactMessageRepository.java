package com.campuslostfound.repo;

import com.campuslostfound.domain.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    Page<ContactMessage> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<ContactMessage> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);
}
