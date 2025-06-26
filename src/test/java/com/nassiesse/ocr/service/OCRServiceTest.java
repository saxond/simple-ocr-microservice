package com.nassiesse.ocr.service;

import com.nassiesse.ocr.TesseractProperties;
import net.sourceforge.tess4j.ITesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class OCRServiceTest {

    @Autowired
    OCRService service;

    @Autowired
    TesseractProperties properties;

    @Test
    public void newTesseractInstance() {
        ITesseract instance = OCRService.newTesseractInstance(properties);
        assertThat(instance).isNotNull();
    }

    @Test
    public void extractTextFromPDF() throws IOException {
        final OCRService.ExtractedPdfData data = service.extractTextFromPDF(OCRService.getTestPDFFile());
        assertThat(data).isNotNull();
        assertThat(data.text()).isNotNull();
        assertThat(data.pageCount()).isEqualTo(1);
    }

    @Test
    public void extractImages() throws IOException {
        var doc = PDDocument.load(OCRService.getTestPDFBytes());
        final File image = service.extractImage(doc);
        assertThat(image).isNotNull();
    }
}