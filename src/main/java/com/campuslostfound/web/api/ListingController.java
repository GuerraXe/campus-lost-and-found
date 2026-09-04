package com.campuslostfound.web.api;

import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingAttribute;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.ListingStatus;
import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.ListingService;
import com.campuslostfound.service.ListingService.CreateCommand;
import com.campuslostfound.service.ListingService.PatchCommand;
import com.campuslostfound.service.ListingService.SearchQuery;
import com.campuslostfound.service.ListingService.View;
import com.campuslostfound.service.MatchingService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.Pageables;
import com.campuslostfound.web.dto.ListingDtos.AttributeInput;
import com.campuslostfound.web.dto.ListingDtos.AttributeResponse;
import com.campuslostfound.web.dto.ListingDtos.CreateRequest;
import com.campuslostfound.web.dto.ListingDtos.DetailResponse;
import com.campuslostfound.web.dto.ListingDtos.PatchRequest;
import com.campuslostfound.web.dto.ListingDtos.StatusChangeRequest;
import com.campuslostfound.web.dto.ListingDtos.SummaryResponse;
import com.campuslostfound.web.dto.PageResponse;
import com.campuslostfound.web.error.Exceptions;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "eventDate", "title");

    private final ListingService listings;
    private final MatchingService matching;

    public ListingController(ListingService listings, MatchingService matching) {
        this.listings = listings;
        this.matching = matching;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DetailResponse create(@AuthenticationPrincipal AppPrincipal actor,
                                 @Valid @RequestBody CreateRequest req) {
        require(actor);
        List<ListingService.AttributeCommand> attrs = req.attributes() == null ? List.of()
                : req.attributes().stream()
                .map(a -> new ListingService.AttributeCommand(a.key(), a.value()))
                .toList();
        Listing saved = listings.create(actor, new CreateCommand(req.kind(), req.title(),
                req.description(), req.category(), req.locationText(), req.building(), req.area(),
                req.eventDate(), req.privateDetails(), attrs));
        return Mappers.detail(saved, true, matching.suggestedCount(saved.getId()));
    }

    @GetMapping
    public PageResponse<SummaryResponse> search(
            @RequestParam(required = false) ListingKind kind,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) String building,
            @RequestParam(required = false, name = "q") String text,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = Pageables.of(page, size, sort, SORT_FIELDS, "createdAt");
        SearchQuery query = new SearchQuery(kind, category, status, building, text, dateFrom, dateTo);
        return PageResponse.of(listings.search(query, pageable), Mappers::summary);
    }

    @GetMapping("/mine")
    public List<SummaryResponse> mine(@AuthenticationPrincipal AppPrincipal actor) {
        require(actor);
        return listings.myListings(actor.id()).stream().map(Mappers::summary).toList();
    }

    @GetMapping("/{id}")
    public DetailResponse get(@AuthenticationPrincipal AppPrincipal actor, @PathVariable Long id) {
        View view = listings.getForView(id, actor);
        return Mappers.detail(view.listing(), view.privileged(), matching.suggestedCount(id));
    }

    @PatchMapping("/{id}")
    public DetailResponse patch(@AuthenticationPrincipal AppPrincipal actor, @PathVariable Long id,
                                @Valid @RequestBody PatchRequest req) {
        require(actor);
        Listing updated = listings.patch(actor, id, new PatchCommand(req.title(), req.description(),
                req.category(), req.locationText(), req.building(), req.area(), req.eventDate(),
                req.privateDetails()));
        return Mappers.detail(updated, true, matching.suggestedCount(id));
    }

    @PostMapping("/{id}/status")
    public DetailResponse changeStatus(@AuthenticationPrincipal AppPrincipal actor,
                                       @PathVariable Long id,
                                       @Valid @RequestBody StatusChangeRequest req) {
        require(actor);
        Listing updated = listings.changeStatus(actor, id, req.status());
        return Mappers.detail(updated, true, matching.suggestedCount(id));
    }

    @PostMapping("/{id}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeResponse addAttribute(@AuthenticationPrincipal AppPrincipal actor,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AttributeInput req) {
        require(actor);
        ListingAttribute attr = listings.addAttribute(actor, id, req.key(), req.value());
        return new AttributeResponse(attr.getId(), attr.getKey(), attr.getValue());
    }

    @DeleteMapping("/{id}/attributes/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAttribute(@AuthenticationPrincipal AppPrincipal actor,
                                @PathVariable Long id, @PathVariable Long attributeId) {
        require(actor);
        listings.removeAttribute(actor, id, attributeId);
    }

    private static void require(AppPrincipal actor) {
        if (actor == null) {
            throw new Exceptions.UnauthorizedException("Authentication required.");
        }
    }
}
