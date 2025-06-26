package com.nassiesse.ocr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

import java.util.logging.Logger;

@SpringBootApplication
@ConfigurationPropertiesScan("com.nassiesse.ocr")
@ComponentScan("com.nassiesse.ocr.controller")
public class OcrApplication {

	public static void main(String[] args) {
		Logger.getAnonymousLogger().info("Starting application");
        SpringApplication.run(OcrApplication.class, args);
	}

}