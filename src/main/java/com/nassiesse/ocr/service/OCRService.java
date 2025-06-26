package com.nassiesse.ocr.service;

import com.nassiesse.ocr.TesseractProperties;
import com.nassiesse.ocr.controller.OCRController;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

@Service
public class OCRService {
    private final static Logger LOGGER = Logger.getLogger("SimpleOCRController");

    private final TesseractProperties tesseractProperties;
    private final ExecutorService executor;
    private final S3Client s3Client;

    public OCRService(TesseractProperties properties) {
        this.tesseractProperties = properties;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        //.newScheduledThreadPool(tesseractProperties.getWorkerPoolSize());
        initialize();
        this.s3Client = S3Client.builder()
                // Configure region or other options as needed
                .build();
        Logger.getAnonymousLogger().info("Started OCRService");
    }

    public File getS3File(String bucketName, String key) throws IOException {
		ResponseInputStream<GetObjectResponse> s3objectResponse = this.s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
		final File file = File.createTempFile("s3file", null);
        try (InputStream in = new BufferedInputStream(s3objectResponse)) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                IOUtils.copy(in, out);
            }
        }
        return file;
    }

    void initialize() {
        try {
            final File temp = getTestPDFFile();
            try {
                extractTextFromPDF(temp);
            } finally {
                temp.delete();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    static File getTestPDFFile() throws IOException {
        File file = File.createTempFile("init", ".pdf");
        byte[] testBytes = getTestPDFBytes();
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            IOUtils.copy(new ByteArrayInputStream(testBytes), fileOutputStream);
        }
        return file;
    }

    static byte[] getTestPDFBytes() throws IOException {
        try (InputStream in = OCRController.class.getResourceAsStream("/test.pdf")) {
            return in == null ? new byte[0] : IOUtils.toByteArray(in);
        }
    }


    public ExtractedPdfData extractTextFromPDF(File file) throws IOException {
        long startTime = System.nanoTime();
        try {
            try (final PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
                LOGGER.info("Loaded PDF document");
                final Splitter splitter = new Splitter();
                final List<PDDocument> pages = splitter.split(document);

                LOGGER.info("Split PDF document in " + pages.size() + " page(s)");

                List<CompletableFuture<PageText>> futures = IntStream.range(0, pages.size()).mapToObj(index -> {
                    Supplier<PageText> work = () -> {
                        try {
                            return new PageText(index, extractTextFromPDFPage(pages.get(index)));
                        } catch (IOException | TesseractException e) {
                            LOGGER.log(Level.SEVERE, e.getMessage(), e);
                            return new PageText(index, "");
                        }
                    };
                    return CompletableFuture.supplyAsync(work, executor);
                }).toList();

                final String text = joinToList(futures).stream()
                        .sorted(Comparator.comparingInt(PageText::index))
                        .map(PageText::text).collect(Collectors.joining());

                LOGGER.info("Total text length: " + text.length());
                return new ExtractedPdfData(text, pages.size());
            }
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            LOGGER.info("Extraction took " + TimeUnit.NANOSECONDS.toSeconds(durationNanos) + 's');
        }
    }

    private String extractTextFromPDFPage(PDDocument document) throws IOException, TesseractException {

        try {
            final PDFTextStripper stripper = new PDFTextStripper();
            // see if we can just strip the text from the file

            final String strippedText = stripper.getText(document).trim();

            LOGGER.info("Stripped text length: " + strippedText.length());
            if (false || !strippedText.isBlank()) {
                return strippedText;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Text stripping failed", e);
        }

        // Extract images from file
        LOGGER.info("Extract image");
        File image = extractImage(document);
        try {
            final ITesseract tesseract = newTesseractInstance(this.tesseractProperties);
            final String result = tesseract.doOCR(image);
            Logger.getAnonymousLogger().info("Result size: " + result.length());
            return result;
        } finally {
            image.delete();
        }
    }

    File extractImage(PDDocument document) throws IOException {
        final PDFRenderer pdfRenderer = new PDFRenderer(document);
        return toFile(pdfRenderer.renderImageWithDPI(0, tesseractProperties.getDpi(), ImageType.GRAY));
    }

    /**
     * Write buffered image to temp file to that we're not holding all pdf page image bytes in memory
     */
    static File toFile(BufferedImage bufferedImage) throws IOException {
        File file = File.createTempFile("pdfimage", ".png");
        file.deleteOnExit();
        ImageIO.write(bufferedImage, "png", file);
        return file;
    }

    public static File toFile(byte[] bytes) throws IOException {
        File temp = File.createTempFile("pdf", ".pdf");
        temp.deleteOnExit();
        IOUtils.copy(new ByteArrayInputStream(bytes), new FileOutputStream(temp));
        return temp;
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

    public record ExtractedPdfData(String text, int pageCount) {}
}
