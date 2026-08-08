package com.mobility.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class RealtimeGatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RealtimeGatewayServiceApplication.class, args);
	}

}
