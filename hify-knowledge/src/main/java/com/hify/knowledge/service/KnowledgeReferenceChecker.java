package com.hify.knowledge.service;

/** Checks whether another module still references a knowledge base. */
public interface KnowledgeReferenceChecker {

  boolean isReferenced(Long knowledgeBaseId);
}
