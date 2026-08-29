package com.hackathon.gdg.participant.repository;

import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.domain.ParticipantStatus;
import com.hackathon.gdg.participant.domain.ParticipantType;
import com.hackathon.gdg.participant.domain.GameRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	List<Participant> findAllByRoomIdOrderByJoinedAtAsc(Long roomId);

	@EntityGraph(attributePaths = "room")
	Optional<Participant> findByParticipantTokenHash(String participantTokenHash);

	boolean existsByParticipantTokenHash(String participantTokenHash);

	boolean existsByRoomIdAndNicknameIgnoreCase(Long roomId, String nickname);

	List<Participant> findAllByRoomIdAndTypeAndStatusOrderByJoinedAtAsc(
			Long roomId,
			ParticipantType type,
			ParticipantStatus status
	);

	long countByRoomIdAndGameRole(Long roomId, GameRole gameRole);
}
