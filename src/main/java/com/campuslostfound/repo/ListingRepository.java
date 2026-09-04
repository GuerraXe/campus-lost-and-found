package com.campuslostfound.repo;

import com.campuslostfound.domain.Category;
import com.campuslostfound.domain.Listing;
import com.campuslostfound.domain.ListingKind;
import com.campuslostfound.domain.ListingStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository
        extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    /**
     * Candidate pre-filter for the matching engine (see docs/design-decisions.md DD-9):
     * only active listings of the opposite kind that share a category, or a building, or
     * fall inside the date window are even scored. Keeps {@code POST /listings} bounded.
     */
    @Query("""
        select l from Listing l
        where l.kind = :kind
          and l.status in (com.campuslostfound.domain.ListingStatus.OPEN,
                           com.campuslostfound.domain.ListingStatus.MATCHED)
          and l.id <> :selfId
          and (
                l.category = :category
             or (:building is not null and lower(l.building) = lower(:building))
             or (l.eventDate between :dateFrom and :dateTo)
          )
        """)
    List<Listing> findMatchPrefilter(@Param("kind") ListingKind oppositeKind,
                                     @Param("selfId") Long selfId,
                                     @Param("category") Category category,
                                     @Param("building") String building,
                                     @Param("dateFrom") LocalDate dateFrom,
                                     @Param("dateTo") LocalDate dateTo);

    List<Listing> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
}
