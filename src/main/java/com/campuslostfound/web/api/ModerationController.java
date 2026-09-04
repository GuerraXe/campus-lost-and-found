package com.campuslostfound.web.api;

import com.campuslostfound.domain.FlagStatus;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.FlagService;
import com.campuslostfound.service.ListingService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.Pageables;
import com.campuslostfound.web.dto.FlagDtos.FlagResponse;
import com.campuslostfound.web.dto.FlagDtos.ResolveRequest;
import com.campuslostfound.web.dto.ListingDtos.DetailResponse;
import com.campuslostfound.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Moderator-only queue and actions. Gated by role at the controller and re-checked in services. */
@RestController
@RequestMapping("/api/v1/moderation")
@PreAuthorize("hasRole('MODERATOR')")
public class ModerationController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt");

    private final FlagService flags;
    private final ListingService listings;

    public ModerationController(FlagService flags, ListingService listings) {
        this.flags = flags;
        this.listings = listings;
    }

    @GetMapping("/flags")
    public PageResponse<FlagResponse> queue(@RequestParam(required = false) FlagStatus status,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        Pageable pageable = Pageables.of(page, size, sort, SORT_FIELDS, "createdAt");
        return PageResponse.of(flags.queue(status, pageable), Mappers::flag);
    }

    @PostMapping("/flags/{flagId}/resolve")
    public FlagResponse resolve(@AuthenticationPrincipal AppPrincipal actor,
                                @PathVariable Long flagId,
                                @Valid @RequestBody ResolveRequest req) {
        FlagService.Outcome outcome = switch (req.action()) {
            case REVIEW -> FlagService.Outcome.REVIEW;
            case ACTION -> FlagService.Outcome.ACTION;
            case DISMISS -> FlagService.Outcome.DISMISS;
        };
        return Mappers.flag(flags.resolve(actor, flagId, outcome, req.note()));
    }

    @PostMapping("/listings/{id}/takedown")
    public DetailResponse takedown(@AuthenticationPrincipal AppPrincipal actor, @PathVariable Long id) {
        var updated = listings.changeStatus(actor, id, ListingStatus.REMOVED);
        return Mappers.detail(updated, true, 0);
    }
}
