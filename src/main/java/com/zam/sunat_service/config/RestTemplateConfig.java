package com.zam.sunat_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate para llamar a las APIs de SUNAT. Los converters por defecto ya incluyen
     * FormHttpMessageConverter (MultiValueMap -> application/x-www-form-urlencoded) y Jackson (JSON).
     * Timeouts explícitos: sin ellos una SUNAT lenta dejaría hilos colgados indefinidamente.
     */
    @Bean
    public RestTemplate sunatRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

}
