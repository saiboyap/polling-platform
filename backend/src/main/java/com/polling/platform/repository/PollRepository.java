package com.polling.platform.repository;

import com.polling.platform.entity.Poll;
import com.polling.platform.entity.PollStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PollRepository extends JpaRepository<Poll, UUID> {

    Page<Poll> findByStatus(PollStatus status, Pageable pageable);

    long countByStatus(PollStatus status);

    @Query("SELECT p FROM Poll p JOIN FETCH p.createdBy WHERE p.id IN :ids")
    List<Poll> findAllByIdWithCreatedBy(@org.springframework.data.repository.query.Param("ids") java.util.List<java.util.UUID> ids);

    @Query("SELECT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.id = :id")
    Optional<Poll> findByIdWithOptions(UUID id);

    @Query("SELECT p FROM Poll p JOIN FETCH p.createdBy LEFT JOIN FETCH p.options WHERE p.id = :id")
    Optional<Poll> findByIdWithDetails(UUID id);
}
