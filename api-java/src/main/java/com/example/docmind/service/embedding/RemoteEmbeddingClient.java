package com.example.docmind.service.embedding;

import com.example.docmind.client.model.EmbedRequest;
import com.example.docmind.client.model.EmbedResponse;
import com.example.docmind.config.EmbeddingProperties;
import com.example.docmind.observability.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Embedding thật: gọi doc-service-py {@code POST /v1/embed} qua CÙNG RestClient
 * mà parser dùng ({@code docServiceRestClient} — same base URL). Propagate
 * X-Correlation-Id để log hai service chung một chuỗi.
 *
 * <p>Là bean {@code @Primary} (tên {@code primaryEmbeddingClient}) nên khi tồn
 * tại nó được inject thay cho {@link StubEmbeddingClient} (stub vẫn được tạo và
 * giữ làm fallback delegate — xem {@code @ConditionalOnMissingBean} trên stub).
 *
 * <p>Fallback theo TỪNG call: khi remote lỗi (hoặc bị tắt), rơi về stub cho
 * đúng call đó để ingest KHÔNG bao giờ fail cứng vì một trục trặc embedding.
 * Chỉ hỗ trợ embed đơn/batch nhỏ ở đây; nếu cần batching lớn/song song, bổ sung
 * sau (API đã nhận {@code List<String>}).
 */
@Component("primaryEmbeddingClient")
@Primary
public class RemoteEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingClient.class);

    private final RestClient restClient;
    private final StubEmbeddingClient stub;
    private final EmbeddingProperties props;

    /**
     * Stub fallback được dựng NGAY trong constructor từ {@link EmbeddingProperties}
     * (không inject như bean) vì {@link StubEmbeddingClient} mang
     * {@code @ConditionalOnMissingBean(name = "primaryEmbeddingClient")} — khi bean
     * này (tên {@code primaryEmbeddingClient}) tồn tại, bean stub KHÔNG được tạo.
     * Giữ nguyên annotation của stub là thay đổi nhỏ nhất; stub chỉ là helper tất
     * định nên tạo trực tiếp là an toàn.
     */
    public RemoteEmbeddingClient(RestClient docServiceRestClient,
                                 EmbeddingProperties props) {
        this.restClient = docServiceRestClient;
        this.props = props;
        this.stub = new StubEmbeddingClient(props);
    }

    @Override
    public int dim() {
        return props.getDim();
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text == null ? "" : text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!props.getRemote().isEnabled()) {
            log.debug("remote embedding disabled -> stub ({} texts)", texts.size());
            return stub.embedBatch(texts);
        }

        CorrelationIds.setStage("embed-serve");
        try {
            EmbedResponse resp = restClient.post()
                    .uri(props.getRemote().getEndpointPath())
                    .header(CorrelationIds.HEADER, CorrelationIds.currentCorrelationId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbedRequest(texts))
                    .retrieve()
                    .body(EmbedResponse.class);

            List<float[]> vectors = mapResponse(resp, texts.size());
            log.info("embed-serve OK provider_model={} batch_size={} dim={}",
                    resp.model(), texts.size(), resp.dim());
            return vectors;
        } catch (RestClientResponseException e) {
            // Lỗi HTTP có response: log cả status + body thật (pattern RestDocServiceClient),
            // rồi fallback stub cho call này (ingest không fail cứng).
            log.warn("embed-serve FAILED status={} body={} -> fallback stub ({} texts)",
                    e.getStatusCode(), e.getResponseBodyAsString(), texts.size());
            return stub.embedBatch(texts);
        } catch (RestClientException e) {
            // Không có response (timeout/connect) hoặc lỗi mapping/validate -> stub.
            log.warn("embed-serve FAILED (no response): {} -> fallback stub ({} texts)",
                    e.getMessage(), texts.size());
            return stub.embedBatch(texts);
        } catch (RuntimeException e) {
            // Body rỗng / dim sai / độ dài vector sai (mapResponse ném IllegalState).
            log.warn("embed-serve FAILED (bad response): {} -> fallback stub ({} texts)",
                    e.getMessage(), texts.size());
            return stub.embedBatch(texts);
        } finally {
            CorrelationIds.clearStage();
        }
    }

    /** Validate response + map {@code List<List<Float>>} -> {@code List<float[]>}. */
    private List<float[]> mapResponse(EmbedResponse resp, int expectedCount) {
        if (resp == null || resp.embeddings() == null) {
            throw new IllegalStateException("embed response rỗng");
        }
        int expectedDim = props.getDim();
        if (resp.dim() != expectedDim) {
            throw new IllegalStateException(
                    "embed dim không khớp: nhận " + resp.dim() + " kỳ vọng " + expectedDim);
        }
        if (resp.embeddings().size() != expectedCount) {
            throw new IllegalStateException(
                    "embed trả " + resp.embeddings().size() + " vector, kỳ vọng " + expectedCount);
        }
        return resp.embeddings().stream().map(vec -> toFloatArray(vec, expectedDim)).toList();
    }

    private static float[] toFloatArray(List<Float> vec, int expectedDim) {
        if (vec == null || vec.size() != expectedDim) {
            throw new IllegalStateException(
                    "vector dài " + (vec == null ? "null" : vec.size()) + ", kỳ vọng " + expectedDim);
        }
        float[] out = new float[expectedDim];
        for (int i = 0; i < expectedDim; i++) {
            out[i] = vec.get(i);
        }
        return out;
    }
}
