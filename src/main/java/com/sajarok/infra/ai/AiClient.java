package com.sajarok.infra.ai;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AiClient {

  private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(50);

  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;
  private final HttpClient httpClient;

  @Autowired
  public AiClient(
      ObjectMapper objectMapper,
      @Value("${ai.openai.api-key:}") String apiKey,
      @Value("${ai.openai.model:}") String model) {
    this(
        objectMapper,
        apiKey,
        model,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  AiClient(ObjectMapper objectMapper, String apiKey, String model, HttpClient httpClient) {
    this.objectMapper = objectMapper;
    this.apiKey = apiKey.trim();
    this.model = model.trim();
    this.httpClient = httpClient;
  }

  public <T> T call(String system, String user, Class<T> resultType) {
    return send(system, user + "\n\nRespond with a single JSON object.", resultType);
  }

  public <T> T callWithImage(String system, String user, String imageDataUrl, Class<T> resultType) {
    var content =
        List.of(
            Map.of("type", "input_text", "text", user + "\n\nRespond with a single JSON object."),
            Map.of("type", "input_image", "image_url", imageDataUrl, "detail", "high"));
    return send(system, List.of(Map.of("role", "user", "content", content)), resultType);
  }

  private <T> T send(String system, Object input, Class<T> resultType) {
    if (apiKey.isBlank() || model.isBlank()) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }

    try {
      String body =
          objectMapper.writeValueAsString(
              Map.of(
                  "model",
                  model,
                  "instructions",
                  system + "\nRespond with a single JSON object.",
                  "input",
                  input,
                  "store",
                  false,
                  "text",
                  Map.of("format", Map.of("type", "json_object"))));

      HttpRequest request =
          HttpRequest.newBuilder(RESPONSES_URI)
              .timeout(REQUEST_TIMEOUT)
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 408 || response.statusCode() == 504) {
        throw new BusinessException(ErrorCode.AI_TIMEOUT);
      }
      if (response.statusCode() != 200) {
        throw new BusinessException(ErrorCode.AI_FAILED);
      }

      String json = readOutputText(objectMapper.readTree(response.body()));
      T result = objectMapper.readValue(json, resultType);
      if (result == null) {
        throw new BusinessException(ErrorCode.AI_FAILED);
      }
      return result;
    } catch (HttpTimeoutException e) {
      throw new BusinessException(ErrorCode.AI_TIMEOUT);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.AI_FAILED);
    } catch (JacksonException | IOException e) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
  }

  private static String readOutputText(JsonNode response) {
    if (!"completed".equals(response.path("status").asText())) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }

    StringBuilder text = new StringBuilder();
    for (JsonNode output : response.path("output")) {
      if ("message".equals(output.path("type").asText())) {
        for (JsonNode content : output.path("content")) {
          if ("refusal".equals(content.path("type").asText())) {
            throw new BusinessException(ErrorCode.AI_FAILED);
          }
          if ("output_text".equals(content.path("type").asText())) {
            text.append(content.path("text").asText(""));
          }
        }
      }
    }

    if (text.toString().isBlank()) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    return text.toString();
  }
}
