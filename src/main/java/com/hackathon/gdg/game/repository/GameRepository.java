package com.hackathon.gdg.game.repository;

import com.hackathon.gdg.game.domain.Game;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

	Optional<Game> findFirstByRoomIdOrderByCreatedAtDesc(Long roomId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select game from Game game where game.id = :gameId")
	Optional<Game> findByIdForUpdate(@Param("gameId") Long gameId);
}
