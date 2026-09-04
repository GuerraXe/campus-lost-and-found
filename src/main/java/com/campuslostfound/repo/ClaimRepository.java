package com.campuslostfound.repo;

import com.campuslostfound.domain.Claim;
import com.campuslostfound.domain.ClaimStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @Query("""
        select c from Claim c join fetch c.claimant
        where c.listing.id = :listingId order by c.createdAt desc
        """)
    List<Claim> findForListing(@Param("listingId") Long listingId);

    @Query("""
        select c from Claim c join fetch c.claimant
        where c.claimant.id = :claimantId order by c.createdAt desc
        """)
    List<Claim> findForClaimant(@Param("claimantId") Long claimantId);

    @Query("select c from Claim c join fetch c.claimant join fetch c.listing where c.id = :id")
    Optional<Claim> findByIdWithRefs(@Param("id") Long id);

    @Query("""
        select case when count(c) > 0 then true else false end from Claim c
        where c.listing.id = :listingId and c.claimant.id = :claimantId and c.status = :status
        """)
    boolean existsPending(@Param("listingId") Long listingId,
                          @Param("claimantId") Long claimantId,
                          @Param("status") ClaimStatus status);

    @Query("""
        select case when count(c) > 0 then true else false end from Claim c
        where c.listing.id = :listingId and c.status = :status
        """)
    boolean existsWithStatus(@Param("listingId") Long listingId, @Param("status") ClaimStatus status);
}
