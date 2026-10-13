package com.example.docmind.service.embedding;

import com.example.docmind.client.model.EmbedResponse;
import com.example.docmind.config.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test fallback-to-stub của RemoteEmbeddingClient KHÔNG cần Python service sống
 * (mock toàn bộ fluent chain của RestClient). Stub fallback là nội bộ (dựng từ
 * EmbeddingProperties) nên ta chứng minh hành vi qua GIÁ TRỊ TRẢ VỀ quan sát được:
 *  - happy path: trả đúng vector từ response (khác hẳn vector stub);
 *  - lỗi HTTP (RestClientResponseException): rơi về stub, KHÔNG ném exception;
 *  - remote tắt: KHÔNG chạm RestClient, trả stub;
 *  - dim()=384.
 */
class RemoteEmbeddingClientTest {

    private EmbeddingProperties props;

    @BeforeEach
    void setUp() {
        props = new EmbeddingProperties();
        props.setDim(384);
    }

    @Test
    void dimIs384() {
        RemoteEmbeddingClient client =
                new RemoteEmbeddingClient(mock(RestClient.class), props);
        assertThat(client.dim()).isEqualTo(384);
    }

    @Test
    void happyPathReturnsRemoteVectorNotStubVector() {
        // Vector 384 chiều xác định (one-hot) — KHÁC hẳn vector stub của cùng text.
        List<Float> remoteVec = new ArrayList<>();
        for (int i = 0; i < 384; i++) {
            remoteVec.add(i == 0 ? 1.0f : 0.0f);
        }
        EmbedResponse resp =
                new EmbedResponse("paraphrase-multilingual-MiniLM-L12-v2", 384, List.of(remoteVec));

        RestClient restClient = mockRestClientReturning(resp);
        RemoteEmbeddingClient client = new RemoteEmbeddingClient(restClient, props);

        float[] out = client.embed("đạo hàm");

        assertThat(out).hasSize(384);
        assertThat(out[0]).isEqualTo(1.0f);
        assertThat(out[1]).isEqualTo(0.0f);
        // Chứng minh KHÔNG dùng stub: vector stub của cùng text khác vector remote.
        float[] stubVec = new StubEmbeddingClient(props).embed("đạo hàm");
        assertThat(out).isNotEqualTo(stubVec);
    }

    @Test
    void httpErrorFallsBackToStubAndDoesNotThrow() {
        RestClient restClient = mockRestClientThrowing(
                new RestClientResponseException(
                        "500 Internal Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error",
                        HttpHeaders.EMPTY,
                        "boom".getBytes(),
                        null));

        RemoteEmbeddingClient client = new RemoteEmbeddingClient(restClient, props);

        float[] out = client.embed("đạo hàm");
        float[] expected = new StubEmbeddingClient(props).embed("đạo hàm");

        assertThat(out).hasSize(384);
        assertThat(out).containsExactly(expected);
    }

    @Test
    void disabledRemoteReturnsStubWithoutCallingRestClient() {
        props.getRemote().setEnabled(false);
        RestClient restClient = mock(RestClient.class);
        RemoteEmbeddingClient client = new RemoteEmbeddingClient(restClient, props);

        float[] out = client.embed("đạo hàm");
        float[] expected = new StubEmbeddingClient(props).embed("đạo hàm");

        assertThat(out).containsExactly(expected);
        // RestClient không được chạm tới khi remote tắt.
        verifyNoInteractions(restClient);
    }

    // --- helpers: dựng fluent chain của RestClient vừa đủ để chạy 2 nhánh ---
    // Dùng Answers.RETURNS_SELF cho RequestBodyUriSpec để mọi method fluent
    // (post/uri/header/contentType/body) trả về chính nó — tránh phải khớp
    // varargs của header(String, String...) vốn mong manh với giá trị null.

    private RestClient mockRestClientReturning(EmbedResponse resp) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec spec =
                mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(spec);
        when(spec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(EmbedResponse.class)).thenReturn(resp);
        return restClient;
    }

    private RestClient mockRestClientThrowing(RuntimeException toThrow) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec spec =
                mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(spec);
        when(spec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(EmbedResponse.class)).thenThrow(toThrow);
        return restClient;
    }
}
