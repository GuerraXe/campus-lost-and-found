package com.campuslostfound.service;

import com.campuslostfound.domain.ContactMessage;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.ContactMessageRepository;
import com.campuslostfound.repo.ListingRepository;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.support.RateLimiter;
import com.campuslostfound.web.error.Exceptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app messaging between a user and a listing's reporter. Neither party's email or phone
 * is exposed; the recipient sees only the sender's display name.
 */
@Service
public class MessageService {

    private final ContactMessageRepository messages;
    private final ListingRepository listings;
    private final AccessGuard guard;
    private final RateLimiter rateLimiter;

    public MessageService(ContactMessageRepository messages, ListingRepository listings,
                          AccessGuard guard, RateLimiter rateLimiter) {
        this.messages = messages;
        this.listings = listings;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public ContactMessage send(AppPrincipal actor, Long listingId, String body) {
        User sender = guard.requireVerified(actor);
        rateLimiter.check(RateLimiter.Bucket.CONTACT_MESSAGE, String.valueOf(actor.id()));

        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Listing not found."));
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new Exceptions.NotFoundException("Listing not found.");
        }
        User recipient = listing.getReporter();
        if (recipient.getId().equals(sender.getId())) {
            throw new Exceptions.ForbiddenException("You cannot message yourself about your own listing.");
        }
        return messages.save(new ContactMessage(listing, sender, recipient, body.trim()));
    }

    @Transactional(readOnly = true)
    public Page<ContactMessage> inbox(Long userId, Pageable pageable) {
        return messages.findInbox(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ContactMessage> sent(Long userId, Pageable pageable) {
        return messages.findSent(userId, pageable);
    }

    @Transactional
    public ContactMessage getAndMaybeMarkRead(AppPrincipal actor, Long messageId) {
        ContactMessage message = messages.findById(messageId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Message not found."));
        boolean isSender = actor.id().equals(message.getSender().getId());
        boolean isRecipient = actor.id().equals(message.getRecipient().getId());
        if (!isSender && !isRecipient) {
            throw new Exceptions.ForbiddenException("This message is not addressed to you.");
        }
        if (isRecipient) {
            message.markRead();
        }
        return message;
    }

    @Transactional
    public ContactMessage markRead(AppPrincipal actor, Long messageId) {
        ContactMessage message = messages.findById(messageId)
                .orElseThrow(() -> new Exceptions.NotFoundException("Message not found."));
        if (!actor.id().equals(message.getRecipient().getId())) {
            throw new Exceptions.ForbiddenException("Only the recipient can mark a message read.");
        }
        message.markRead();
        return message;
    }
}
