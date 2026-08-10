import { debounce } from 'lodash';

export interface SearchDebouncer {
  schedule: (value: string) => void;
  cancel: () => void;
}

/**
 * Keeps a pending search scoped to the currently active table/search lifecycle.
 * Cancelling invalidates queued callbacks as well as clearing their timer.
 */
export const createSearchDebouncer = (
  onSearch: (value: string) => void,
  wait = 500,
): SearchDebouncer => {
  let revision = 0;
  const debouncedSearch = debounce((value: string, scheduledRevision: number) => {
    if (scheduledRevision === revision) {
      onSearch(value);
    }
  }, wait);

  return {
    schedule: (value) => debouncedSearch(value, revision),
    cancel: () => {
      revision += 1;
      debouncedSearch.cancel();
    },
  };
};
