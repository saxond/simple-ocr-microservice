package com.nassiesse.ocr.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import com.nassiesse.ocr.TesseractProperties;
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

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@RestController
public class SimpleOCRController {
	private final static Logger LOGGER = Logger.getLogger("SimpleOCRController");

	private final TesseractProperties tesseractProperties;

	public SimpleOCRController(TesseractProperties tesseractProperties) {
		this.tesseractProperties = tesseractProperties;
		Logger.getAnonymousLogger().info("Started controller");
	}

	@PostMapping("/api/pdf/extractText")
    public @ResponseBody ResponseEntity<String> 
					extractTextFromPDFFile(@RequestParam("file") MultipartFile file) {
		try {
			
			// Load file into PDFBox class
			PDDocument document = Loader.loadPDF(file.getBytes());
			PDFTextStripper stripper = new PDFTextStripper();
			String strippedText = stripper.getText(document);
			
			// Check text exists into the file
			if (true || strippedText.trim().isEmpty()){
				strippedText = extractTextFromScannedDocument(document);
			}
			
			JSONObject obj = new JSONObject();
	        obj.put("fileName", file.getOriginalFilename());
	        obj.put("text", strippedText);
			
			return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
	}
	
	@GetMapping("/api/pdf/ping")
    public ResponseEntity<String> get()
    {
		return new ResponseEntity<String>("PONG", HttpStatus.OK);
    }

	private String extractTextFromScannedDocument(PDDocument document) throws IOException, TesseractException {
		
		// Extract images from file
		final File missing = new File(".");
		List<File> images = IntStream.range(0, document.getNumberOfPages())
				.parallel()
				.mapToObj(index -> {
					try {
						LOGGER.log(Level.INFO, "Process page " + index);
						final PDFRenderer pdfRenderer = new PDFRenderer(document);
						BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(index, 300, ImageType.RGB);
						File temp = File.createTempFile("tempfile_" + index, ".png");
						temp.deleteOnExit();
						ImageIO.write(bufferedImage, "png", temp);
						return temp;
					} catch (IOException e) {
						LOGGER.log(Level.SEVERE, e.getMessage(), e);
						return missing;
					}
				})
				.toList();

		/*
		CompletableFuture.allOf(imageFutures.toArray(new CompletableFuture[0])).join();

		final List<File> images = imageFutures.stream()
				.map(CompletableFuture::join) // Get the result of each completed future
				.toList();
*/
		List<CompletableFuture<String>> futures = images.stream()
				.filter(file -> missing != file)
				.map(
				file -> CompletableFuture.supplyAsync(() -> {
					LOGGER.info("Running OCR on image " + file.getName());
					try {
						final ITesseract tesseract = newTesseractInstance(this.tesseractProperties);
						final String result = tesseract.doOCR(file);
						Logger.getAnonymousLogger().info("Result size: " + result.length());
						return result;
					} catch (TesseractException e) {
						Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage(), e);
					} finally {
						// Delete temp file
						file.delete();
					}
					return "";
				})
		).toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		return futures.stream()
				.map(CompletableFuture::join) // Get the result of each completed future
				.collect(Collectors.joining());

		/*
		IntStream.range(0, document.getNumberOfPages())
				.mapToObj(index -> {
                    try {
                        return pdfRenderer.renderImageWithDPI(index, 300, ImageType.RGB);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
				.map(bufferedImage -> {
					File temp = null;
					try {
						temp = File.createTempFile("tempfile", ".png");

						ImageIO.write(bufferedImage, "png", temp);

						final String result = tesseract.doOCR(temp);
						Logger.getAnonymousLogger().info("Result size: " + result.length());
						return result;
					} catch (TesseractException e) {
                        Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage(), e);
                    } catch (IOException e) {
						Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage(), e);
                    } finally {
						// Delete temp file
						if (temp != null) {
							temp.delete();
						}
					}
					return "";
				}).collect(Collectors.toList());



		for (int page = 0; page < document.getNumberOfPages(); page++)
		{ 
		    final BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
		    
		    // Create a temp image file
    	    final File temp = File.createTempFile("tempfile_" + page, ".png");
			try {

				ImageIO.write(bim, "png", temp);

				final String result = tesseract.doOCR(temp);
				out.append(result);
				Logger.getAnonymousLogger().info("Result size: " + result.length());
			} finally {
				// Delete temp file
				temp.delete();
			}
		}
		*/

	}

	static ITesseract newTesseractInstance(TesseractProperties properties) {
		final Tesseract tesseract = new Tesseract();
		tesseract.setDatapath(properties.getDataPath());
		tesseract.setLanguage(properties.getLanguage());
		return tesseract;
	}

}

