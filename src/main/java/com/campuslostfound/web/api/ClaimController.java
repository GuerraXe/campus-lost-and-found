package com.campuslostfound.web.api;

import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.ClaimService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.dto.ClaimDtos.ClaimResponse;
import com.campuslostfound.web.dto.ClaimDtos.CreateRequest;
import com.campuslostfound.web.dto.ClaimDtos.Decision;
import com.campuslostfound.web.dto.ClaimDtos.DecisionRequest;
import com.campuslostfound.web.error.Exceptions;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ownership claims: submit against a FOUND listing, list, decide (finder/moderator), withdraw. */
@RestController
@RequestMapping("/api/v1")
public class ClaimController {

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping("/listings/{id}/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimResponse submit(@AuthenticationPrincipal AppPrincipal actor, @PathVariable Long id,
                                @Valid @RequestBody CreateRequest req) {
        require(actor);
        return Mappers.claim(claims.submit(actor, id, req.answer(), req.matchCandidateId()));
    }

    @GetMapping("/listings/{id}/claims")
    public List<ClaimResponse> forListing(@AuthenticationPrincipal AppPrincipal actor,
                                          @PathVariable Long id) {
        require(actor);
        return claims.forListing(actor, id).stream().map(Mappers::claim).toList();
    }

    @GetMapping("/claims/mine")
    public List<ClaimResponse> mine(@AuthenticationPrincipal AppPrincipal actor) {
        require(actor);
        return claims.mine(actor.id()).stream().map(Mappers::claim).toList();
    }

    @PostMapping("/claims/{claimId}/decision")
    public ClaimResponse decide(@AuthenticationPrincipal AppPrincipal actor,
                                @PathVariable Long claimId,
                                @Valid @RequestBody DecisionRequest req) {
        require(actor);
        boolean approve = req.decision() == Decision.APPROVE;
        return Mappers.claim(claims.decide(actor, claimId, approve, req.note()));
    }

    @PostMapping("/claims/{claimId}/withdraw")
    public ClaimResponse withdraw(@AuthenticationPrincipal AppPrincipal actor,
                                  @PathVariable Long claimId) {
        require(actor);
        return Mappers.claim(claims.withdraw(actor, claimId));
    }

    private static void require(AppPrincipal actor) {
        if (actor == null) {
            throw new Exceptions.UnauthorizedException("Authentication required.");
        }
    }
}
