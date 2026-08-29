package com.hackathon.gdg.character.repository;

import com.hackathon.gdg.character.domain.Character;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {

	List<Character> findAllByGameIdOrderByIdAsc(Long gameId);

	Optional<Character> findByGameIdAndParticipantId(Long gameId, Long participantId);

	Optional<Character> findByQrToken(String qrToken);

	@Query("select character.game.id from Character character where character.qrToken = :qrToken")
	Optional<Long> findGameIdByQrToken(@Param("qrToken") String qrToken);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select character from Character character where character.qrToken = :qrToken")
	Optional<Character> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

	boolean existsByGameIdAndParticipantId(Long gameId, Long participantId);

	boolean existsByQrToken(String qrToken);

	long countByGameId(Long gameId);
}
