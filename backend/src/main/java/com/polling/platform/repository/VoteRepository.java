package com.polling.platform.repository;

import com.polling.platform.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

    /** True if this user has cast ANY vote in this poll (used for SINGLE_CHOICE guard). */
    boolean existsByPoll_IdAndUser_Id(UUID pollId, UUID userId);

    /** True if this user already voted for this specific option (MULTI_CHOICE duplicate guard). */
    boolean existsByPoll_IdAndUser_IdAndOption_Id(UUID pollId, UUID userId, UUID optionId);

    /** Count how many distinct options this user voted for in this poll (MULTI_CHOICE limit check). */
    long countByPoll_IdAndUser_Id(UUID pollId, UUID userId);

    @Query("SELECT v.option.id, COUNT(v) FROM Vote v WHERE v.poll.id = :pollId GROUP BY v.option.id")
    List<Object[]> countVotesByOption(UUID pollId);

    long countByPollId(UUID pollId);
}
