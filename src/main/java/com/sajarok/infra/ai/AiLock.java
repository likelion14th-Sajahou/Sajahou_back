package com.sajarok.infra.ai;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AiLock {

  private final Set<String> activeKeys = ConcurrentHashMap.newKeySet();

  public <T> T run(String key, Supplier<T> action) {
    if (!activeKeys.add(key)) {
      throw new BusinessException(ErrorCode.AI_IN_PROGRESS);
    }

    try {
      return action.get();
    } finally {
      activeKeys.remove(key);
    }
  }
}
