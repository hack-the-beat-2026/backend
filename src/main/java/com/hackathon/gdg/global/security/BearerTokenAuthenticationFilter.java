package com.hackathon.gdg.global.security;

import com.hackathon.gdg.participant.domain.Participant;
import com.hackathon.gdg.participant.repository.ParticipantRepository;
import com.hackathon.gdg.room.domain.Room;
import com.hackathon.gdg.room.repository.RoomRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final TokenService tokenService;
	private final RoomRepository roomRepository;
	private final ParticipantRepository participantRepository;

	public BearerTokenAuthenticationFilter(
			TokenService tokenService,
			RoomRepository roomRepository,
			ParticipantRepository participantRepository
	) {
		this.tokenService = tokenService;
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX) && header.length() > BEARER_PREFIX.length()) {
			authenticate(header.substring(BEARER_PREFIX.length()).trim());
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String rawToken) {
		if (SecurityContextHolder.getContext().getAuthentication() != null || rawToken.isBlank()) {
			return;
		}

		String tokenHash = tokenService.hash(rawToken);
		Optional<Room> hostRoom = roomRepository.findByHostTokenHash(tokenHash);
		if (hostRoom.isPresent()) {
			setAuthentication(
					AuthenticatedActor.host(hostRoom.get().getId()),
					new SimpleGrantedAuthority("ROLE_HOST")
			);
			return;
		}

		participantRepository.findByParticipantTokenHash(tokenHash)
				.ifPresent(participant -> setAuthentication(
						AuthenticatedActor.player(participant.getRoom().getId(), participant.getId()),
						new SimpleGrantedAuthority("ROLE_PLAYER")
				));
	}

	private void setAuthentication(AuthenticatedActor actor, SimpleGrantedAuthority authority) {
		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(actor, null, List.of(authority));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
