package com.jrawler.adaptation;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocxService {

    public record DocxParagraph(int index, String text) {}

    public List<DocxParagraph> extractParagraphs(byte[] docxBytes) {
        try (XWPFDocument doc = openDocx(docxBytes)) {
            List<DocxParagraph> result = new ArrayList<>();
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i).getText();
                if (text != null && !text.isBlank()) {
                    result.add(new DocxParagraph(i, text));
                }
            }
            return result;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid .docx file");
        }
    }

    public byte[] applyEdits(byte[] docxBytes, Map<Integer, String> editsByIndex) {
        try (XWPFDocument doc = openDocx(docxBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (Map.Entry<Integer, String> edit : editsByIndex.entrySet()) {
                int index = edit.getKey();
                if (index < 0 || index >= paragraphs.size()) {
                    continue;
                }
                replaceText(paragraphs.get(index), edit.getValue());
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid .docx file");
        }
    }

    // Keep the first run (and its formatting), drop the rest, set the new text on it.
    private void replaceText(XWPFParagraph paragraph, String newText) {
        for (int i = paragraph.getRuns().size() - 1; i >= 1; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        run.setText(newText, 0);
    }

    private XWPFDocument openDocx(byte[] docxBytes) throws IOException {
        try {
            return new XWPFDocument(new ByteArrayInputStream(docxBytes));
        } catch (Exception e) {
            throw new IOException("Not a docx", e);
        }
    }
}
