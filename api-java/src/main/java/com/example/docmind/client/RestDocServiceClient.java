package com.example.docmind.client;

import com.example.docmind.client.model.CanonicalDocument;
import com.example.docmind.client.model.JobStatusResponse;
import com.example.docmind.client.model.ParseSubmitResponse;
import com.example.docmind.config.DocServiceProperties;
import com.example.docmind.exception.DocServiceException;
import com.example.docmind.observability.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DocServiceProperties props;

    public RestDocServiceClient(RestClient docServiceRestClient) {
        this(docServiceRestClient, new DocServiceProperties());
    }

    @Autowired
    public RestDocServiceClient(RestClient docServiceRestClient, DocServiceProperties props) {
        this.restClient = docServiceRestClient;
        this.props = props != null ? props : new DocServiceProperties();
    }

    @Override
    public ParseSubmitResponse submitParse(byte[] fileBytes, String filename, String correlationId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        String parseUri = (props.getParseEndpointPath() != null && !props.getParseEndpointPath().isBlank())
                ? props.getParseEndpointPath().strip()
                : "/v1/parse";

        CorrelationIds.setStage("submit-parse");
        try {
            ParseSubmitResponse resp = restClient.post()
                    .uri(parseUri)
                    .header(CorrelationIds.HEADER, correlationId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ParseSubmitResponse.class);
            if (resp == null) {
                throw new DocServiceException("Parser trả về body rỗng cho " + parseUri);
            }
            log.info("submit-parse OK job_id={} document_id={} cached={}",
                    resp.jobId(), resp.documentId(), resp.cached());
            return resp;
        } catch (RestClientResponseException e) {
            log.error("submit-parse FAILED status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new DocServiceException("Gọi " + parseUri + " thất bại (" + e.getStatusCode()
                    + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            log.error("submit-parse FAILED (no response): {}", e.getMessage());
            throw new DocServiceException("Gọi " + parseUri + " thất bại: " + e.getMessage(), e);
        } finally {
            CorrelationIds.clearStage();
        }
    }

    @Override
    public JobStatusResponse getJob(String jobId, String correlationId) {
        String jobStatusUri = (props.getJobStatusEndpointPath() != null && !props.getJobStatusEndpointPath().isBlank())
                ? props.getJobStatusEndpointPath().strip()
                : "/v1/jobs/{jobId}";

        CorrelationIds.setStage("poll-job");
        try {
            JobStatusResponse resp = restClient.get()
                    .uri(jobStatusUri, jobId)
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
        String jobResultUri = (props.getJobResultEndpointPath() != null && !props.getJobResultEndpointPath().isBlank())
                ? props.getJobResultEndpointPath().strip()
                : "/v1/jobs/{jobId}/result";

        CorrelationIds.setStage("get-result");
        try {
            CanonicalDocument doc = restClient.get()
                    .uri(jobResultUri, jobId)
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
