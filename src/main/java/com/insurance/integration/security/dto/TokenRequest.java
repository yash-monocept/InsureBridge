package com.insurance.integration.security.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OAuth 2.0 Client Credentials token request (JSON form).
 * Snake_case is the OAuth standard; camelCase aliases are accepted for
 * convenience in interactive tooling.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequest {

    @JsonProperty("client_id")
    @JsonAlias("clientId")
    @NotBlank(message = "client_id is required")
    private String clientId;

    @JsonProperty("client_secret")
    @JsonAlias("clientSecret")
    @NotBlank(message = "client_secret is required")
    private String clientSecret;

    @JsonProperty("grant_type")
    @JsonAlias("grantType")
    private String grantType;
}
