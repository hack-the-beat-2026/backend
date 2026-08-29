package com.hackathon.gdg.scan.service;

import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class SeekTimeExpiredException extends ApiException {

	public SeekTimeExpiredException() {
		super(
				ErrorCode.GAME_INVALID_STATE,
				HttpStatus.CONFLICT,
				"탐색 제한시간이 끝나 게임이 종료되었습니다."
		);
	}
}
