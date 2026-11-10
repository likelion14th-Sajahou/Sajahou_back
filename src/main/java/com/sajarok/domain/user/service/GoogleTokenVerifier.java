package com.sajarok.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifier {

  private final GoogleIdTokenVerifier verifier;

  public GoogleTokenVerifier(@Value("${auth.google.client-id}") String clientId) {
    this.verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(List.of(clientId))
            .build();
  }

  public Optional<GoogleIdentity> verify(String idToken)
      throws GeneralSecurityException, IOException {
    var token = verifier.verify(idToken);
    if (token == null) {
      return Optional.empty();
    }

    var payload = token.getPayload();
    return Optional.of(
        new GoogleIdentity(payload.getSubject(), payload.getEmail(), (String) payload.get("name")));
  }

  public record GoogleIdentity(String providerId, String email, String suggestedName) {}
}
