package com.samples.gateway.microgateway;

import com.samples.gateway.microgateway.model.Product;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpResponse.response;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class GatewayRetriesTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRetriesTests.class);

    public static final DockerImageName MOCKSERVER_IMAGE = DockerImageName
            .parse("mockserver/mockserver")
            .withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion());

    @ClassRule
    @Container
    public static MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE);
    @Autowired
    TestRestTemplate template;

    @BeforeAll
    public static void init() {
        mockServer.start();
        System.setProperty("spring.cloud.gateway.httpclient.response-timeout", "100ms");
        System.setProperty("spring.cloud.gateway.routes[0].id", "product-service");
        System.setProperty("spring.cloud.gateway.routes[0].uri", "http://" + mockServer.getHost() + ":" + mockServer.getServerPort());
        System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/product/**");
        System.setProperty("spring.cloud.gateway.routes[0].filters[0]", "RewritePath=/product/(?<path>.*), /$\\{path}");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].name", "Retry");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.retries", "10");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.statuses", "INTERNAL_SERVER_ERROR");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.firstBackoff", "50ms");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.maxBackoff", "500ms");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.factor", "2");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.backoff.basedOnPreviousValue", "true");
        System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.fallbackUri", "null");
        try (MockServerClient client = new MockServerClient(mockServer.getHost(), mockServer.getServerPort())) {

            client.when(HttpRequest.request()
                            .withPath("/product/1"), Times.exactly(3))
                    .respond(response()
                            .withStatusCode(500)
                            .withBody("{\"errorCode\":\"500\"}")
                            .withHeader("Content-Type", "application/json"));
            client.when(HttpRequest.request()
                            .withPath("/1"))
                    .respond(response()
                            .withBody("{\"id\":1,\"number\":\"12341234\"}")
                            .withHeader("Content-Type", "application/json"));
            client.when(HttpRequest.request()
                            .withPath("/2"))
                    .respond(response()
                            .withBody("{\"id\":2,\"number\":\"12341234\"}")
                            .withDelay(TimeUnit.MILLISECONDS, 200)
                            .withHeader("Content-Type", "application/json"));
        }

    }

    @Test
    public void testProductService(){
        LOGGER.info("Sending /1...");
        ResponseEntity<Product> prod = template.exchange("/product/{id}", HttpMethod.GET, null, Product.class, 1);
        LOGGER.info("Received: status->{}, payload->{},", prod.getStatusCode().value(), prod.getBody());
        Assert.assertEquals(200, prod.getStatusCode().value());
    }

}
