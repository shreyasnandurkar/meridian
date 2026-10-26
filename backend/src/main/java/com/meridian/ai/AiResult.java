package com.meridian.ai;

/** A validated model output plus the metadata shown in "Why?" popovers. */
public record AiResult<T>(T value, String model, String promptVersion, int attempts, long latencyMs) {}
