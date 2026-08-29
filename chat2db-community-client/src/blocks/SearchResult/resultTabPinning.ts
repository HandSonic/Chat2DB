interface PinnableResult {
  uuid?: string;
}

export function getMatchingResultReplacement<T extends PinnableResult>(value: unknown, targetResult: T) {
  if (
    !value ||
    Array.isArray(value) ||
    typeof value !== 'object' ||
    !('uuid' in value) ||
    value.uuid !== targetResult.uuid
  ) {
    return undefined;
  }
  return value as T;
}

export function retainPinnedResults<T extends PinnableResult>(
  incomingResults: T[],
  existingResults: T[],
  pinnedKeys: ReadonlySet<string>,
  incomingHistoryResults: T[] = [],
): T[] {
  const incomingKeys = new Set(
    [...incomingResults, ...incomingHistoryResults]
      .map((item) => item.uuid)
      .filter((key): key is string => !!key),
  );
  const retainedKeys = new Set<string>();
  const retainedResults = existingResults.filter((item) => {
    const key = item.uuid;
    if (!key || !pinnedKeys.has(key) || incomingKeys.has(key) || retainedKeys.has(key)) {
      return false;
    }
    retainedKeys.add(key);
    return true;
  });

  return [...retainedResults, ...incomingResults];
}

export function replaceRetainedResult<T extends PinnableResult>(
  currentResults: T[],
  targetResult: T,
  replacementResult: T,
) {
  const targetKey = targetResult.uuid;
  if (!targetKey) {
    return currentResults;
  }
  let replaced = false;
  const nextResults = currentResults.map((result) => {
    if (replaced || result.uuid !== targetKey) {
      return result;
    }
    replaced = true;
    return replacementResult;
  });
  return replaced ? nextResults : currentResults;
}
