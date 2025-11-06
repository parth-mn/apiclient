package com.example.apiclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TwilioClient {
    private final WebClient web;
    @Value("${twilio.accountSid}") String accountSid;
    @Value("${twilio.authToken}")  String authToken;
    @Value("${twilio.fromWhatsApp}") String fromWhatsApp;

    public TwilioClient() {
        this.web = WebClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01")
                .build();
    }

    public Mono<String> sendWhatsApp(String toWhatsApp, String body) {
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("To", toWhatsApp);
        form.add("From", fromWhatsApp);
        form.add("Body", body);

        return web.post()
                .uri("/Accounts/{sid}/Messages.json", accountSid)
                .headers(h -> h.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(String.class);
    }
}
