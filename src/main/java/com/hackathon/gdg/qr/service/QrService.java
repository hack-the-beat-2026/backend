package com.hackathon.gdg.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class QrService {

	private static final int QR_IMAGE_SIZE = 512;

	private final String scanBaseUrl;

	public QrService(@Value("${app.frontend-base-url}") String frontendBaseUrl) {
		this.scanBaseUrl = frontendBaseUrl.replaceAll("/+$", "") + "/c/";
	}

	public byte[] generatePng(String qrToken) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(
					scanPayload(qrToken),
					BarcodeFormat.QR_CODE,
					QR_IMAGE_SIZE,
					QR_IMAGE_SIZE,
					Map.of(
							EncodeHintType.CHARACTER_SET, "UTF-8",
							EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
							EncodeHintType.MARGIN, 2
					)
			);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(matrix, "PNG", output);
			return output.toByteArray();
		} catch (WriterException | IOException exception) {
			throw new ApiException(
					ErrorCode.QR_GENERATION_FAILED,
					HttpStatus.INTERNAL_SERVER_ERROR,
					"QR 이미지를 생성하지 못했습니다."
			);
		}
	}

	public String scanPayload(String qrToken) {
		return scanBaseUrl + qrToken;
	}
}
