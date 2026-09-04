package com.campuslostfound.web.api;

import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.MessageService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.Pageables;
import com.campuslostfound.web.dto.MessageDtos.MessageResponse;
import com.campuslostfound.web.dto.MessageDtos.SendRequest;
import com.campuslostfound.web.dto.PageResponse;
import com.campuslostfound.web.error.Exceptions;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** In-app contact messages: send about a listing, read your inbox/sent, mark read. */
@RestController
@RequestMapping("/api/v1")
public class MessageController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt");

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @PostMapping("/listings/{id}/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal AppPrincipal actor, @PathVariable Long id,
                                @Valid @RequestBody SendRequest req) {
        require(actor);
        return Mappers.message(messages.send(actor, id, req.message()), actor.id());
    }

    @GetMapping("/messages")
    public PageResponse<MessageResponse> list(@AuthenticationPrincipal AppPrincipal actor,
                                              @RequestParam(defaultValue = "inbox") String box,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String sort) {
        require(actor);
        Pageable pageable = Pageables.of(page, size, sort, SORT_FIELDS, "createdAt");
        var result = switch (box.toLowerCase()) {
            case "inbox" -> messages.inbox(actor.id(), pageable);
            case "sent" -> messages.sent(actor.id(), pageable);
            default -> throw new Exceptions.BadRequestException("box must be 'inbox' or 'sent'.");
        };
        return PageResponse.of(result, m -> Mappers.message(m, actor.id()));
    }

    @GetMapping("/messages/{messageId}")
    public MessageResponse get(@AuthenticationPrincipal AppPrincipal actor,
                               @PathVariable Long messageId) {
        require(actor);
        return Mappers.message(messages.getAndMaybeMarkRead(actor, messageId), actor.id());
    }

    @PostMapping("/messages/{messageId}/read")
    public MessageResponse markRead(@AuthenticationPrincipal AppPrincipal actor,
                                    @PathVariable Long messageId) {
        require(actor);
        return Mappers.message(messages.markRead(actor, messageId), actor.id());
    }

    private static void require(AppPrincipal actor) {
        if (actor == null) {
            throw new Exceptions.UnauthorizedException("Authentication required.");
        }
    }
}
