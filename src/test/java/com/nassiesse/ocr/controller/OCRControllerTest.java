package com.nassiesse.ocr.controller;

import com.nassiesse.ocr.TesseractProperties;
import net.sourceforge.tess4j.ITesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class OCRControllerTest {

    @Autowired
    OCRController controller;

    @Autowired
    TesseractProperties properties;

    @Test
    public void context() {
        assertThat(controller).isNotNull();
    }

    @Test
    public void newTesseractInstance() {
        ITesseract instance = OCRController.newTesseractInstance(properties);
        assertThat(instance).isNotNull();
    }

    @Test
    public void extractTextFromPDF() throws IOException {
        final String text = controller.extractTextFromPDF(OCRController.getTestPDFBytes());
        assertThat(text).isNotNull();
    }

    @Test
    public void extractImages() throws IOException {
        var doc = PDDocument.load(OCRController.getTestPDFBytes());
        final List<OCRController.PageImage> images = controller.extractImages(doc);
        assertThat(images).isNotNull();
        assertThat(images.size()).isEqualTo(1);
    }
}