package com.nassiesse.ocr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

import java.util.logging.Logger;

@SpringBootApplication
@ConfigurationPropertiesScan("com.nassiesse.ocr")
@ComponentScan("com.nassiesse.ocr.controller")
public class SimpleOcrMicroserviceApplication {

	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.err.println("WTF!");
		}));
		Logger.getAnonymousLogger().info("Starting application");
        SpringApplication.run(SimpleOcrMicroserviceApplication.class, args);
	}

}