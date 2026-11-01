package com.hify.mcp.service;

import java.util.Collection;

public interface McpToolReferenceChecker {

  boolean anyReferenced(Collection<Long> toolIds);
}
