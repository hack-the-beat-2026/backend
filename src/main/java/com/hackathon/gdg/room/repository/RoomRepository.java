package com.hackathon.gdg.room.repository;

import com.hackathon.gdg.room.domain.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

	Optional<Room> findByRoomCode(String roomCode);

	Optional<Room> findByHostTokenHash(String hostTokenHash);

	boolean existsByRoomCode(String roomCode);

	boolean existsByHostTokenHash(String hostTokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select room from Room room where room.id = :roomId")
	Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);
}
