package com.mobility.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RealtimeGatewayServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
