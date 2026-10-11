package com.hify.knowledge.storage;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.knowledge.config.KnowledgeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Local disk storage using deterministic, server-generated file paths. */
@Component
public class DocumentFileStorage {

  public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES = Set.of("txt", "md", "pdf");
  private static final Logger log = LoggerFactory.getLogger(DocumentFileStorage.class);

  private final Path uploadRoot;

  public DocumentFileStorage(KnowledgeProperties properties) {
    this.uploadRoot = properties.getUploadDir().toAbsolutePath().normalize();
  }

  public ValidatedUpload validate(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() <= 0) {
      throw parameterError("上传文件不能为空");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw parameterError("上传文件不能超过 10MB");
    }

    String originalName = file.getOriginalFilename();
    if (originalName == null || originalName.isBlank()) {
      throw parameterError("文件名不能为空");
    }
    String safeName;
    try {
      safeName = Path.of(originalName).getFileName().toString();
    } catch (InvalidPathException e) {
      throw parameterError("文件名无效");
    }
    if (safeName.length() > 200) {
      throw parameterError("文件名长度不能超过 200");
    }
    int dot = safeName.lastIndexOf('.');
    if (dot <= 0 || dot == safeName.length() - 1) {
      throw parameterError("文件类型仅支持 txt、md、pdf");
    }
    String fileType = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!ALLOWED_TYPES.contains(fileType)) {
      throw parameterError("文件类型仅支持 txt、md、pdf");
    }
    validateSignature(file, fileType);
    return new ValidatedUpload(safeName, fileType, file.getSize());
  }

  public Path save(Long knowledgeBaseId, Long documentId, String fileType, MultipartFile file) {
    Path target = pathFor(knowledgeBaseId, documentId, fileType);
    try {
      Files.createDirectories(target.getParent());
      Path temp = Files.createTempFile(target.getParent(), documentId + "-", ".uploading");
      try {
        try (InputStream input = file.getInputStream()) {
          Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
          Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
          Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temp);
      }
      return target;
    } catch (IOException e) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
    }
  }

  public Path pathFor(Long knowledgeBaseId, Long documentId, String fileType) {
    if (knowledgeBaseId == null || documentId == null || !ALLOWED_TYPES.contains(fileType)) {
      throw parameterError("文档存储参数无效");
    }
    Path path = uploadRoot.resolve(String.valueOf(knowledgeBaseId))
        .resolve(documentId + "." + fileType).normalize();
    if (!path.startsWith(uploadRoot)) {
      throw parameterError("文档存储路径无效");
    }
    return path;
  }

  public void deleteBestEffort(Long knowledgeBaseId, Long documentId, String fileType) {
    try {
      Files.deleteIfExists(pathFor(knowledgeBaseId, documentId, fileType));
    } catch (Exception e) {
      log.warn("document-file-delete failed documentId={} error={}", documentId, e.getMessage());
    }
  }

  public boolean exists(Long knowledgeBaseId, Long documentId, String fileType) {
    return Files.isRegularFile(pathFor(knowledgeBaseId, documentId, fileType));
  }

  private void validateSignature(MultipartFile file, String fileType) {
    try (InputStream input = file.getInputStream()) {
      byte[] prefix = input.readNBytes(4096);
      if ("pdf".equals(fileType)) {
        String header = new String(prefix, 0, Math.min(prefix.length, 5), StandardCharsets.US_ASCII);
        if (!"%PDF-".equals(header)) {
          throw parameterError("PDF 文件格式无效");
        }
      } else {
        for (byte value : prefix) {
          if (value == 0) {
            throw parameterError("文本文件包含二进制内容");
          }
        }
      }
    } catch (IOException e) {
      throw parameterError("无法读取上传文件");
    }
  }

  private BizException parameterError(String message) {
    return new BizException(ErrorCode.PARAM_ERROR, message);
  }
}
