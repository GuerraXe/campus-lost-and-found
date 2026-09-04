package com.campuslostfound.repo;

import com.campuslostfound.domain.Flag;
import com.campuslostfound.domain.FlagStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    Page<Flag> findByStatusOrderByCreatedAtAsc(FlagStatus status, Pageable pageable);

    Page<Flag> findByOrderByCreatedAtAsc(Pageable pageable);

    boolean existsByListingIdAndReporterIdAndStatusIn(Long listingId, Long reporterId,
                                                      java.util.Collection<FlagStatus> statuses);
}
