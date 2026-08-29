package com.hackathon.gdg.global.security;

import com.hackathon.gdg.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter
	) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.formLogin(formLogin -> formLogin.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/files/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/rooms").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/rooms/*").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/rooms/*/participants").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) -> SecurityErrorWriter.write(
								response,
								HttpServletResponse.SC_UNAUTHORIZED,
								ErrorCode.INVALID_TOKEN.name(),
								"유효한 Bearer Token이 필요합니다."
						))
						.accessDeniedHandler((request, response, exception) -> SecurityErrorWriter.write(
								response,
								HttpServletResponse.SC_FORBIDDEN,
								ErrorCode.ACCESS_DENIED.name(),
								"요청 권한이 없습니다."
						))
				)
				.addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
