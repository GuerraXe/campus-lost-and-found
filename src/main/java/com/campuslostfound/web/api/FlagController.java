package com.campuslostfound.web.api;

import com.campuslostfound.security.AppPrincipal;
import com.campuslostfound.service.FlagService;
import com.campuslostfound.web.Mappers;
import com.campuslostfound.web.dto.FlagDtos.CreateRequest;
import com.campuslostfound.web.dto.FlagDtos.FlagResponse;
import com.campuslostfound.web.error.Exceptions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Any authenticated user can report a listing as suspicious or incorrect. */
@RestController
@RequestMapping("/api/v1/listings")
public class FlagController {

    private final FlagService flags;

    public FlagController(FlagService flags) {
        this.flags = flags;
    }

    @PostMapping("/{id}/flags")
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse flag(@AuthenticationPrincipal AppPrincipal actor, @PathVariable Long id,
                             @Valid @RequestBody CreateRequest req) {
        if (actor == null) {
            throw new Exceptions.UnauthorizedException("Authentication required.");
        }
        return Mappers.flag(flags.submit(actor, id, req.reason(), req.details()));
    }
}
