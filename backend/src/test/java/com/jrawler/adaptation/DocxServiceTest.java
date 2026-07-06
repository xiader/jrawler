package com.jrawler.adaptation;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxServiceTest {

    private final DocxService docxService = new DocxService();

    private byte[] sampleDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p0 = doc.createParagraph();
            XWPFRun r0 = p0.createRun();
            r0.setText("Ivan Ivanov");
            r0.setBold(true);

            doc.createParagraph(); // index 1: empty

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("Built microservices with Spring Boot");

            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractsNonEmptyParagraphsWithOriginalIndexes() throws Exception {
        List<DocxService.DocxParagraph> paragraphs = docxService.extractParagraphs(sampleDocx());

        assertThat(paragraphs).containsExactly(
                new DocxService.DocxParagraph(0, "Ivan Ivanov"),
                new DocxService.DocxParagraph(2, "Built microservices with Spring Boot"));
    }

    @Test
    void appliesEditsAndPreservesFirstRunFormatting() throws Exception {
        byte[] result = docxService.applyEdits(sampleDocx(),
                Map.of(2, "Designed and built Kafka-based microservices with Spring Boot"));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertThat(doc.getParagraphs().get(0).getText()).isEqualTo("Ivan Ivanov");
            assertThat(doc.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(doc.getParagraphs().get(2).getText())
                    .isEqualTo("Designed and built Kafka-based microservices with Spring Boot");
        }
    }

    @Test
    void roundTripAfterEditYieldsUpdatedParagraphList() throws Exception {
        byte[] result = docxService.applyEdits(sampleDocx(), Map.of(0, "IVAN IVANOV"));

        List<DocxService.DocxParagraph> paragraphs = docxService.extractParagraphs(result);
        assertThat(paragraphs.get(0)).isEqualTo(new DocxService.DocxParagraph(0, "IVAN IVANOV"));
    }

    @Test
    void rejectsNonDocxBytes() {
        assertThatThrownBy(() -> docxService.extractParagraphs("not a docx".getBytes()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
