package com.campuslostfound.repo;

import com.campuslostfound.domain.Claim;
import com.campuslostfound.domain.ClaimStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByListingIdOrderByCreatedAtDesc(Long listingId);

    List<Claim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    boolean existsByListingIdAndClaimantIdAndStatus(Long listingId, Long claimantId, ClaimStatus status);

    boolean existsByListingIdAndStatus(Long listingId, ClaimStatus status);
}
