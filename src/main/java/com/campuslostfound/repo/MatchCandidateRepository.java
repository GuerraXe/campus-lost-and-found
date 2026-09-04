package com.campuslostfound.repo;

import com.campuslostfound.domain.MatchCandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchCandidateRepository extends JpaRepository<MatchCandidate, Long> {

    /** Both listings and the reason rows fetched: results are mapped after the tx closes. */
    @Query("""
        select distinct mc from MatchCandidate mc
        join fetch mc.lostListing
        join fetch mc.foundListing
        left join fetch mc.reasons
        where mc.lostListing.id = :listingId or mc.foundListing.id = :listingId
        order by mc.score desc, mc.id asc
        """)
    List<MatchCandidate> findForListing(@Param("listingId") Long listingId);

    @Query("""
        select distinct mc from MatchCandidate mc
        join fetch mc.lostListing
        join fetch mc.foundListing
        left join fetch mc.reasons
        where mc.id = :id
        """)
    Optional<MatchCandidate> findByIdWithGraph(@Param("id") Long id);

    @Query("""
        select distinct mc from MatchCandidate mc
        join fetch mc.lostListing
        join fetch mc.foundListing
        left join fetch mc.reasons
        where mc.lostListing.id = :lostId and mc.foundListing.id = :foundId
        """)
    Optional<MatchCandidate> findByPair(@Param("lostId") Long lostId, @Param("foundId") Long foundId);

    @Query("""
        select count(mc) from MatchCandidate mc
        where (mc.lostListing.id = :listingId or mc.foundListing.id = :listingId)
          and mc.status = com.campuslostfound.domain.MatchStatus.SUGGESTED
        """)
    int countSuggestedForListing(@Param("listingId") Long listingId);

    @Query("""
        select case when count(mc) > 0 then true else false end from MatchCandidate mc
        where (mc.lostListing.id = :listingId or mc.foundListing.id = :listingId)
          and mc.status = com.campuslostfound.domain.MatchStatus.CONFIRMED
        """)
    boolean existsConfirmedForListing(@Param("listingId") Long listingId);
}
