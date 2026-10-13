package com.example.docmind.client;

import com.example.docmind.client.model.CanonicalDocument;
import com.example.docmind.client.model.JobStatusResponse;
import com.example.docmind.client.model.ParseSubmitResponse;
import com.example.docmind.exception.DocServiceException;
import com.example.docmind.observability.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Implementation RestClient của {@link DocServiceClient}.
 * Mỗi call set header X-Correlation-Id và log stage=submit-parse / poll-job.
 */
@Component
public class RestDocServiceClient implements DocServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RestDocServiceClient.class);

    private final RestClient restClient;

    public RestDocServiceClient(RestClient docServiceRestClient) {
        this.restClient = docServiceRestClient;
    }

    @Override
    public ParseSubmitResponse submitParse(byte[] fileBytes, String filename, String correlationId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Field name EXACTLY 'file' (khớp FEAT-002 contract).
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        CorrelationIds.setStage("submit-parse");
        try {
            ParseSubmitResponse resp = restClient.post()
                    .uri("/v1/parse")
                    .header(CorrelationIds.HEADER, correlationId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ParseSubmitResponse.class);
            if (resp == null) {
                throw new DocServiceException("Parser trả về body rỗng cho /v1/parse");
            }
            log.info("submit-parse OK job_id={} document_id={} cached={}",
                    resp.jobId(), resp.documentId(), resp.cached());
            return resp;
        } catch (RestClientResponseException e) {
            // Lỗi HTTP có response: log cả status + body thật (không nuốt chi tiết).
            log.error("submit-parse FAILED status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new DocServiceException("Gọi /v1/parse thất bại (" + e.getStatusCode()
                    + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            log.error("submit-parse FAILED (no response): {}", e.getMessage());
            throw new DocServiceException("Gọi /v1/parse thất bại: " + e.getMessage(), e);
        } finally {
            CorrelationIds.clearStage();
        }
    }

    @Override
    public JobStatusResponse getJob(String jobId, String correlationId) {
        CorrelationIds.setStage("poll-job");
        try {
            JobStatusResponse resp = restClient.get()
                    .uri("/v1/jobs/{jobId}", jobId)
                    .header(CorrelationIds.HEADER, correlationId)
                    .retrieve()
                    .body(JobStatusResponse.class);
            if (resp == null) {
                throw new DocServiceException("Parser trả về body rỗng cho /v1/jobs/" + jobId);
            }
            log.info("poll-job job_id={} status={} pages={}/{}",
                    jobId, resp.status(), resp.pagesDone(), resp.pagesTotal());
            return resp;
        } catch (RestClientException e) {
            log.error("poll-job FAILED job_id={}: {}", jobId, e.getMessage());
            throw new DocServiceException("Gọi /v1/jobs/" + jobId + " thất bại: " + e.getMessage(), e);
        } finally {
            CorrelationIds.clearStage();
        }
    }

    @Override
    public CanonicalDocument getResult(String jobId, String correlationId) {
        CorrelationIds.setStage("get-result");
        try {
            CanonicalDocument doc = restClient.get()
                    .uri("/v1/jobs/{jobId}/result", jobId)
                    .header(CorrelationIds.HEADER, correlationId)
                    .retrieve()
                    .body(CanonicalDocument.class);
            if (doc == null) {
                throw new DocServiceException("Parser trả về body rỗng cho /v1/jobs/" + jobId + "/result");
            }
            int blockCount = doc.blocks() == null ? 0 : doc.blocks().size();
            log.info("get-result job_id={} document_id={} blocks={}",
                    jobId, doc.documentId(), blockCount);
            return doc;
        } catch (RestClientException e) {
            log.error("get-result FAILED job_id={}: {}", jobId, e.getMessage());
            throw new DocServiceException("Gọi /v1/jobs/" + jobId + "/result thất bại: " + e.getMessage(), e);
        } finally {
            CorrelationIds.clearStage();
        }
    }
}
