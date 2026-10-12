package com.example.docmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Bean RestClient cho DocServiceClient (base-url + timeouts từ config).
 *
 * <p>Dùng JDK HttpClient + JdkClientHttpRequestFactory từ spring-web core (luôn có
 * sẵn) để set connect/read timeout, tránh phụ thuộc helper của module Boot http-client.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient docServiceRestClient(DocServiceProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
