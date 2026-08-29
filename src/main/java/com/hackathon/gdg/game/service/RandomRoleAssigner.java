package com.hackathon.gdg.game.service;

import com.hackathon.gdg.participant.domain.GameRole;
import com.hackathon.gdg.participant.domain.Participant;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RandomRoleAssigner {

	private final SecureRandom secureRandom = new SecureRandom();

	public void assign(List<Participant> participants, int seekerCount) {
		List<Participant> shuffled = new ArrayList<>(participants);
		Collections.shuffle(shuffled, secureRandom);

		for (int index = 0; index < shuffled.size(); index++) {
			GameRole role = index < seekerCount ? GameRole.SEEKER : GameRole.HIDER;
			shuffled.get(index).assignRole(role);
		}
	}
}
