package com.AirDrop.Spherical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableAsync
@SpringBootApplication
@EnableScheduling
@EnableFeignClients
public class SphericalApplication {

	public static void main(String[] args) {
		SpringApplication.run(SphericalApplication.class, args);
	}

}
