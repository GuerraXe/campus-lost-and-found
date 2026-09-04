package com.campuslostfound.repo;

import com.campuslostfound.domain.Flag;
import com.campuslostfound.domain.FlagStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    Page<Flag> findByStatusOrderByCreatedAtAsc(FlagStatus status, Pageable pageable);

    Page<Flag> findByOrderByCreatedAtAsc(Pageable pageable);

    @Query("""
        select case when count(f) > 0 then true else false end from Flag f
        where f.listing.id = :listingId and f.reporter.id = :reporterId and f.status in :statuses
        """)
    boolean existsUnresolved(@Param("listingId") Long listingId,
                             @Param("reporterId") Long reporterId,
                             @Param("statuses") Collection<FlagStatus> statuses);
}
