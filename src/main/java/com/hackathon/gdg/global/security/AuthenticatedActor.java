package com.hackathon.gdg.global.security;

public record AuthenticatedActor(
		ActorType type,
		Long roomId,
		Long participantId
) {
	public static AuthenticatedActor host(Long roomId) {
		return new AuthenticatedActor(ActorType.HOST, roomId, null);
	}

	public static AuthenticatedActor player(Long roomId, Long participantId) {
		return new AuthenticatedActor(ActorType.PLAYER, roomId, participantId);
	}

	public boolean isHost() {
		return type == ActorType.HOST;
	}
}
