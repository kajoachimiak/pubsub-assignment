package com.pubsub.assignment;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AssignmentApplicationTests {

	@MockitoBean
	private PubSubTemplate pubSubTemplate;

	@Test
	void contextLoads() {
	}

}
