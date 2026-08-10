package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.IngestionFailureCode;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeIngestionProperties;
import io.github.yanziki.enterpriseai.knowledge.TextLocatorType;
import io.github.yanziki.enterpriseai.knowledge.storage.StoredObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Version;
import org.springframework.stereotype.Component;

@Component
public class BoundedDocumentContentExtractor implements DocumentContentExtractor {

    private static final int BUFFER_SIZE = 16 * 1024;

    private final KnowledgeIngestionProperties properties;

    public BoundedDocumentContentExtractor(KnowledgeIngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public ExtractionResult extract(StoredObject storedObject, String detectedContentType) {
        if (storedObject.contentLength() <= 0
                || storedObject.contentLength() > properties.maxOriginalBytes()) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.FILE_TOO_LARGE,
                    "The stored document size is outside the processing limit");
        }
        byte[] bytes = readBounded(storedObject.content());
        if (detectedContentType.equals("application/pdf")) {
            return extractPdf(bytes);
        }
        if (detectedContentType.startsWith("text/")) {
            return extractText(bytes);
        }
        throw new DocumentExtractionException(
                IngestionFailureCode.UNSUPPORTED_MEDIA_TYPE,
                "The stored document type is unsupported");
    }

    private ExtractionResult extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new DocumentExtractionException(
                        IngestionFailureCode.PARSER_FAILURE,
                        "Encrypted PDF documents are not supported");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > properties.maxPdfPages()) {
                throw new DocumentExtractionException(
                        IngestionFailureCode.TEXT_LIMIT_EXCEEDED,
                        "The PDF exceeds the configured page limit");
            }

            List<ExtractedTextUnit> units = new ArrayList<>();
            int totalCharacters = 0;
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (text.isBlank()) {
                    continue;
                }
                totalCharacters += text.length();
                requireWithinTextLimit(totalCharacters);
                units.add(
                        new ExtractedTextUnit(
                                units.size() + 1,
                                TextLocatorType.PAGE,
                                Integer.toString(page),
                                text));
            }
            requireNonEmpty(units);
            return new ExtractionResult("Apache PDFBox", Version.getVersion(), units);
        } catch (DocumentExtractionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.PARSER_FAILURE,
                    "The PDF could not be parsed safely",
                    exception);
        }
    }

    private ExtractionResult extractText(byte[] bytes) {
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException exception) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.PARSER_FAILURE,
                    "The text document is not valid UTF-8",
                    exception);
        }
        text = normalize(text);
        if (text.isBlank()) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.EMPTY_DOCUMENT,
                    "The document contains no extractable text");
        }
        requireWithinTextLimit(text.length());
        return new ExtractionResult(
                "JDK UTF-8",
                Runtime.version().toString(),
                List.of(new ExtractedTextUnit(1, TextLocatorType.DOCUMENT, "body", text)));
    }

    private byte[] readBounded(InputStream input) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                total += bytesRead;
                if (total > properties.maxOriginalBytes()) {
                    throw new DocumentExtractionException(
                            IngestionFailureCode.FILE_TOO_LARGE,
                            "The stored document exceeds the processing limit");
                }
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.STORAGE_FAILURE,
                    "The stored document could not be read",
                    exception);
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private void requireWithinTextLimit(int characterCount) {
        if (characterCount > properties.maxExtractedCharacters()) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.TEXT_LIMIT_EXCEEDED,
                    "The extracted text exceeds the configured limit");
        }
    }

    private void requireNonEmpty(List<ExtractedTextUnit> units) {
        if (units.isEmpty()) {
            throw new DocumentExtractionException(
                    IngestionFailureCode.EMPTY_DOCUMENT,
                    "The document contains no extractable text");
        }
    }
}
