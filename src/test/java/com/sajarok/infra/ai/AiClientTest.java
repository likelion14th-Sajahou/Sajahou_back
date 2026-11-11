package com.sajarok.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiClientTest {

  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> response;

  record Result(String answer) {}

  @Test
  void convertsCompletedResponseToDto() throws Exception {
    stubResponse(
        """
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "{\\"answer\\":\\"확인\\"}"}
                      ]
                    }
                  ]
                }
                """);

    assertThat(client().call("JSON으로 답하세요.", "테스트 입력", Result.class)).isEqualTo(new Result("확인"));
  }

  @Test
  void mapsInvalidJsonToAiFailed() throws Exception {
    stubResponse(
        """
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [{"type": "output_text", "text": "invalid json"}]
                    }
                  ]
                }
                """);

    assertThatThrownBy(() -> client().call("시스템 지시", "입력", Result.class))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }

  @Test
  void mapsTimeoutToAiTimeout() throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("test timeout"));

    assertThatThrownBy(() -> client().call("시스템 지시", "입력", Result.class))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_TIMEOUT);
  }

  private void stubResponse(String body) throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(body);
  }

  private AiClient client() {
    return new AiClient(JsonMapper.builder().build(), "test-key", "test-model", httpClient);
  }
}
