package com.sajarok.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TEST", matches = "true")
class AiClientLiveTest {

  record Result(String answer) {}

  @Test
  void convertsRealOpenAiResponseToDto() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    String model = System.getenv("OPENAI_MODEL");
    assertThat(apiKey != null && !apiKey.isBlank()).as("OPENAI_API_KEY 설정").isTrue();
    assertThat(model != null && !model.isBlank()).as("OPENAI_MODEL 설정").isTrue();

    var client = new AiClient(JsonMapper.builder().build(), apiKey, model);
    Result result =
        client.call(
            "Return a JSON object with one string field named answer.",
            "Set answer to exactly OK.",
            Result.class);

    assertThat(result.answer()).isEqualTo("OK");
  }
}
