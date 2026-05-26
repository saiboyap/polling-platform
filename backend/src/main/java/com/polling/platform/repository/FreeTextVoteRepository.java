package com.polling.platform.repository;

import com.polling.platform.entity.FreeTextVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FreeTextVoteRepository extends JpaRepository<FreeTextVote, UUID> {

    boolean existsByPoll_IdAndUser_Id(UUID pollId, UUID userId);

    List<FreeTextVote> findByPoll_IdOrderByVotedAtDesc(UUID pollId);

    long countByPoll_Id(UUID pollId);
}
