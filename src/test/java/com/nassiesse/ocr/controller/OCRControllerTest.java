package com.nassiesse.ocr.controller;

import com.nassiesse.ocr.TesseractProperties;
import net.sourceforge.tess4j.ITesseract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}