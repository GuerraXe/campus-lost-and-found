package com.campuslostfound.repo;

import com.campuslostfound.domain.Flag;
import com.campuslostfound.domain.FlagStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    @Query(value = "select f from Flag f join fetch f.listing",
            countQuery = "select count(f) from Flag f")
    Page<Flag> findAllWithListing(Pageable pageable);

    @Query(value = "select f from Flag f join fetch f.listing where f.status = :status",
            countQuery = "select count(f) from Flag f where f.status = :status")
    Page<Flag> findByStatusWithListing(@Param("status") FlagStatus status, Pageable pageable);

    @Query("select f from Flag f join fetch f.listing where f.id = :id")
    Optional<Flag> findByIdWithListing(@Param("id") Long id);

    @Query("""
        select case when count(f) > 0 then true else false end from Flag f
        where f.listing.id = :listingId and f.reporter.id = :reporterId and f.status in :statuses
        """)
    boolean existsUnresolved(@Param("listingId") Long listingId,
                             @Param("reporterId") Long reporterId,
                             @Param("statuses") Collection<FlagStatus> statuses);
}
