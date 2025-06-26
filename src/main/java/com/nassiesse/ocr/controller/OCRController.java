package com.nassiesse.ocr.controller;

import com.nassiesse.ocr.TesseractProperties;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
public class OCRController {
	private final static Logger LOGGER = Logger.getLogger("SimpleOCRController");

	private final TesseractProperties tesseractProperties;
	private final ExecutorService executor;

	public OCRController(TesseractProperties tesseractProperties) {
		this.tesseractProperties = tesseractProperties;
		Logger.getAnonymousLogger().info("Started controller");
		this.executor = Executors.newVirtualThreadPerTaskExecutor();
				//.newScheduledThreadPool(tesseractProperties.getWorkerPoolSize());
	}

	@PostMapping("/api/pdf/extractText")
    public @ResponseBody ResponseEntity<String> 
					extractTextFromPDFFile(@RequestParam("file") MultipartFile file) {
		long startTime = System.nanoTime();
		try {
			
			// Load file into PDFBox class
			PDDocument document = Loader.loadPDF(file.getBytes());
			PDFTextStripper stripper = new PDFTextStripper();
			String strippedText = stripper.getText(document);
			
			// Check text exists into the file
			if (strippedText.trim().isEmpty()){
				strippedText = extractTextFromScannedDocument(document);
			}
			
			JSONObject obj = new JSONObject();
	        obj.put("fileName", file.getOriginalFilename());
	        obj.put("text", strippedText);
			
			return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		} finally {
			long durationNanos = System.nanoTime() - startTime;
			LOGGER.info("Extraction took " + TimeUnit.NANOSECONDS.toSeconds(durationNanos) + 's');
		}
		
	}
	
	@GetMapping("/api/pdf/ping")
    public ResponseEntity<String> get()
    {
		return new ResponseEntity<>("PONG", HttpStatus.OK);
    }

	private String extractTextFromScannedDocument(PDDocument document) {
		long startTime = System.nanoTime();
		// Extract images from file

		List<PageImage> images = extractImages(document);
		LOGGER.info("Page count:" + document.getNumberOfPages() +  ", image count: " + images.size() +
				" took " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) + 's');

		List<PageText> pageToText = joinToList(images.stream()
			.map(
				pageImage -> {
					LOGGER.info("Running OCR on image " + pageImage.index);
					Supplier<PageText> work = () -> {
						try {
							final ITesseract tesseract = newTesseractInstance(this.tesseractProperties);
							final String result = tesseract.doOCR(pageImage.image);
							Logger.getAnonymousLogger().info("Result size: " + result.length());
							return new PageText(pageImage.index, result);
						} catch (TesseractException e) {
							Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage(), e);
						}
						return new PageText(pageImage.index, "");
					};
					return CompletableFuture.supplyAsync(work, executor);
				}
			).toList());

		return pageToText.stream().sorted(Comparator.comparingInt(PageText::index)).map(PageText::text)
				.collect(Collectors.joining());

	}

	private List<PageImage> extractImages(PDDocument document) {
		List<CompletableFuture<PageImage>> imageFutures = IntStream.range(0, document.getNumberOfPages()).mapToObj(index -> {
			Supplier<PageImage> work = () -> {
				try {
					LOGGER.log(Level.INFO, "Process page " + index);
					final PDFRenderer pdfRenderer = new PDFRenderer(document);
					return new PageImage(index, pdfRenderer.renderImageWithDPI(index, tesseractProperties.getDpi(), ImageType.GRAY));
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
					return new PageImage(index, null);
				}
			};
			return CompletableFuture.supplyAsync(work, executor);
		}).toList();
		return joinToList(imageFutures).stream()
				.filter(PageImage::isValid).toList();
	}

	static <T> List<T> joinToList(List<CompletableFuture<T>> futures) {
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		return futures.stream()
				.map(CompletableFuture::join)
				.toList();
	}

	static ITesseract newTesseractInstance(TesseractProperties properties) {
		final Tesseract tesseract = new Tesseract();
		tesseract.setDatapath(properties.getDataPath());
		tesseract.setLanguage(properties.getLanguage());
		tesseract.setTessVariable("user_defined_dpi", Integer.toString(properties.getDpi()));
		return tesseract;
	}

	record PageText(int index, String text) {
	}

	record PageImage(int index, BufferedImage image) {
		boolean isValid() {
			return image != null;
		}
	}
}

