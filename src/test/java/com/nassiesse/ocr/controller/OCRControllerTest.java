package com.nassiesse.ocr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class OCRControllerTest {

    @Autowired
    OCRController controller;

    @Test
    public void context() {
        assertThat(controller).isNotNull();
    }

    @Test
    public void json() throws IOException {
        var res = new OCRController.Result("test", "filename", 1);
        var text = new ObjectMapper().writeValueAsString(res);
        assertThat(text).isEqualTo("{\"text\":\"test\",\"fileName\":\"filename\",\"pageCount\":1}");
    }
}