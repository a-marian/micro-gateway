package com.samples.gateway.microgateway;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.samples.gateway.microgateway.model.Product;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.jupiter.api.RepeatedTest;

import org.junit.rules.TestRule;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MockServerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpResponse.response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class MicroGatewayApplicationTests {
	private static final Logger LOGGER = LoggerFactory.getLogger(MicroGatewayApplicationTests.class);
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();
	public static final DockerImageName MOCKSERVER_IMAGE = DockerImageName
			.parse("mockserver/mockserver")
			.withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion());

	@Container
	public MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE);
	@Autowired
	TestRestTemplate template;
	int i =0;

	@BeforeClass
	public void init(){
		MockServerClient client = new MockServerClient(mockServer.getContainerIpAddress(), mockServer.getServerPort());
		System.setProperty("spring.cloud.gateway.routes[0].id", "product-service");
		System.setProperty("spring.cloud.gateway.routes[0].uri", "http://"+mockServer.getHost()+":"+mockServer.getServerPort());
		System.setProperty("spring.cloud.gateway.routes[0].predicates[0]", "Path=/product/**");
		System.setProperty("spring.cloud.gateway.routes[0].filters[0]", "RewritePath=/product/(?<path>.*), /$\\{path}");
		System.setProperty("spring.cloud.gateway.routes[0].filters[1].name", "CircuitBreaker");
		System.setProperty("spring.cloud.gateway.routes[0].filters[1].args.name", "exampleSlowCircuitBreaker");

		client.when(HttpRequest.request()
				.withPath("/1"))
				.respond(response()
								.withBody("{\"id\":1,\"number\":\"12341234\"}")
								.withHeader("Content-Type", "application/json"));
		client.when(HttpRequest.request()
						.withPath("/2"))
						.respond(response()
								.withBody("\"id\":2,\"number\":\"12341234\"}")
								.withDelay(TimeUnit.MILLISECONDS, 200)
								.withHeader("Content-Type", "application/json"));
	}

	@RepeatedTest(20)
	@BenchmarkOptions(warmupRounds = 0, concurrency = 1, benchmarkRounds = 20)
	public void testProductService(){
		int gen = 1 + (i++ % 2);
		ResponseEntity<Product> r = template.exchange("/product/{id}", HttpMethod.GET, null, Product.class, gen);
		LOGGER.info("{} Received: status->{}, payload->{}, call->{}", i, r.getStatusCode().value(), r.getBody(), gen );
	}

}
