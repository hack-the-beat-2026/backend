package com.hackathon.gdg.room.repository;

import com.hackathon.gdg.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

	Optional<Room> findByRoomCode(String roomCode);

	Optional<Room> findByHostTokenHash(String hostTokenHash);

	boolean existsByRoomCode(String roomCode);
}
