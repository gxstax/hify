package com.hify.knowledge.embedding;

import com.hify.provider.dto.ModelInvocationConfig;
import java.util.List;

/** Generates one embedding vector per input text, preserving input order. */
public interface EmbeddingClient {

  List<float[]> embed(List<String> texts, ModelInvocationConfig config);
}
