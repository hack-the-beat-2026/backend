package com.hackathon.gdg.participant.repository;

import com.hackathon.gdg.participant.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	List<Participant> findAllByRoomIdOrderByJoinedAtAsc(Long roomId);

	Optional<Participant> findByParticipantTokenHash(String participantTokenHash);

	boolean existsByRoomIdAndNicknameIgnoreCase(Long roomId, String nickname);
}
