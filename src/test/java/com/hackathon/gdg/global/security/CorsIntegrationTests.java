package com.hackathon.gdg.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTests {

	private static final String FRONTEND_ORIGIN =
			"https://dist-two-ecru-35.vercel.app";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowsDeployedFrontendPreflightRequest() throws Exception {
		mockMvc.perform(preflight(FRONTEND_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						containsString("authorization")
				));
	}

	@Test
	void allowsLocalFrontendPreflightRequest() throws Exception {
		mockMvc.perform(preflight("http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173"
				));
	}

	@Test
	void rejectsUnknownOriginPreflightRequest() throws Exception {
		mockMvc.perform(preflight("https://example.invalid"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	private MockHttpServletRequestBuilder preflight(String origin) {
		return options("/api/v1/rooms")
				.header(HttpHeaders.ORIGIN, origin)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
	}
}
