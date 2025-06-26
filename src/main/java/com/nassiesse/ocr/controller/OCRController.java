package com.nassiesse.ocr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nassiesse.ocr.service.OCRService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
public class OCRController {
	private final static Logger LOGGER = Logger.getLogger("SimpleOCRController");

	private final OCRService service;

	public OCRController(OCRService service) {
		this.service = service;
		Logger.getAnonymousLogger().info("Started controller");
	}

	@GetMapping("/api/pdf/aws/{bucket}")
	public @ResponseBody ResponseEntity<String>
			extractTextFromAWSPDFFile(@PathVariable String bucket,
								   @RequestParam("key") String key) {
		if (bucket == null) {
			return new ResponseEntity<>("Missing bucket", HttpStatus.BAD_REQUEST);
		}

		if (key == null) {
			return new ResponseEntity<>("Missing key", HttpStatus.BAD_REQUEST);
		}
		LOGGER.info("Bucket: " + bucket + ", key:" + key);
		try {
			final File pdfFile = service.getS3File(bucket, key);
			try {
				final OCRService.ExtractedPdfData data = service.extractTextFromPDF(pdfFile);

				ObjectMapper mapper = new ObjectMapper();
				final String res = mapper.writeValueAsString(new Result(data.text(), key, data.pageCount()));

				return new ResponseEntity<>(res, HttpStatus.OK);
			} finally {
				pdfFile.delete();
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

		@PostMapping("/api/pdf/extractText")
    public @ResponseBody ResponseEntity<String> 
					extractTextFromPDFFile(@RequestParam("file") MultipartFile file) {
		LOGGER.info("extractText called");
		try {
			final File pdfFile = OCRService.toFile(file.getBytes());
			try {
				final OCRService.ExtractedPdfData data = service.extractTextFromPDF(pdfFile);

				ObjectMapper mapper = new ObjectMapper();
				final String res = mapper.writeValueAsString(new Result(data.text(), file.getOriginalFilename(), data.pageCount()));

				return new ResponseEntity<>(res, HttpStatus.OK);
			} finally {
				pdfFile.delete();
			}
		} catch (Exception e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@GetMapping("/api/pdf/ping")
    public ResponseEntity<String> get()
    {
		return new ResponseEntity<>("PONG", HttpStatus.OK);
    }

	record Result(String text, String fileName, int pageCount) {}
}

