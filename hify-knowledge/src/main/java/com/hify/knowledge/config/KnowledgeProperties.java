package com.hify.knowledge.config;

import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Externalized settings for document storage, processing and embeddings. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hify.knowledge")
public class KnowledgeProperties {

  private Path uploadDir = Path.of("./upload");
  private int vectorDimension = 1536;
  private int chunkSize = 512;
  private int chunkOverlap = 64;
  private Duration processingTimeout = Duration.ofMinutes(10);
  private int recoveryLimit = 100;
  private Embedding embedding = new Embedding();
  private Rag rag = new Rag();

  @Getter
  @Setter
  public static class Embedding {

    private Long modelConfigId;
    private Duration timeout = Duration.ofSeconds(30);
    private int batchSize = 20;
  }

  @Getter
  @Setter
  public static class Rag {

    private int topK = 5;
    private double minSimilarity = 0.5;
  }
}
