package com.example.apiclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

record TokenResponse(String access_token, String token_type, String refresh_token, Long expires_in, String scope) {}

@Service
public class TokenService {
    private final WebClient web;

    public TokenService(WebClient webClient) { this.web = webClient; }

    @Value("${app.clientId}")     String clientId;
    @Value("${app.clientSecret}") String clientSecret;
    @Value("${app.username}")     String username;
    @Value("${app.password}")     String password;

    public Mono<String> getAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("scope", "");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("username", username);
        form.add("password", password);

        return web.post()
                .uri("/authorizationserver/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .map(TokenResponse::access_token);
    }
}
