package com.hify.knowledge.processing;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts UTF-8 text and Markdown, plus text-based PDFs without OCR. */
@Component
public class DocumentTextExtractor {

  public String extract(Path path, String fileType) {
    try {
      String text = switch (fileType) {
        case "txt", "md" -> Files.readString(path, StandardCharsets.UTF_8);
        case "pdf" -> extractPdf(path);
        default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的文件类型: " + fileType);
      };
      if (text == null || text.isBlank()) {
        throw new BizException(ErrorCode.PARAM_ERROR, "文档中未提取到可处理文本");
      }
      return text.replace("\r\n", "\n").replace('\r', '\n');
    } catch (BizException e) {
      throw e;
    } catch (IOException e) {
      throw new BizException(ErrorCode.PARAM_ERROR, "文档解析失败");
    }
  }

  private String extractPdf(Path path) throws IOException {
    try (PDDocument document = Loader.loadPDF(path.toFile())) {
      if (document.isEncrypted()) {
        throw new BizException(ErrorCode.PARAM_ERROR, "暂不支持加密 PDF");
      }
      return new PDFTextStripper().getText(document);
    }
  }
}
