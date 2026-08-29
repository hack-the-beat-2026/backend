package com.hackathon.gdg.character.repository;

import com.hackathon.gdg.character.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {

	List<Character> findAllByGameIdOrderByIdAsc(Long gameId);

	Optional<Character> findByGameIdAndParticipantId(Long gameId, Long participantId);

	Optional<Character> findByQrToken(String qrToken);

	boolean existsByGameIdAndParticipantId(Long gameId, Long participantId);

	boolean existsByQrToken(String qrToken);

	long countByGameId(Long gameId);
}
