import React, {
  memo,
  forwardRef,
  useImperativeHandle,
  ForwardedRef,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  useCallback,
  useMemo,
} from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import i18n from '@/i18n';
import { useStyles } from './style';
import { SearchComponent } from '@visactor/vtable-search';
import { ITableInstance } from '@/blocks/CanvasTable/typings';
import { hexToRgba } from '@/utils/color';
import { IconButton, SearchBar } from '@chat2db/ui';
import { createSearchDebouncer } from './searchDebouncer';

interface IProps {
  className?: string;
  tableInstance: ITableInstance | null;
  // closed callback
  onClose?: () => void;
  searchAreaId: string;
  // Changes whenever ResultSet replaces the records in the existing table instance.
  resultContentRevision: unknown;
}

export interface FESearchRef {
  close: () => void;
  focus: () => void;
}

const FESearch = forwardRef((props: IProps, ref: ForwardedRef<FESearchRef>) => {
  const { className, tableInstance, searchAreaId, onClose, resultContentRevision } = props;
  const { styles, cx } = useStyles();
  const searchRef = useRef<SearchComponent | null>(null);
  // The value of the last search
  const [searchResult, setSearchResult] = useState<any>(null);
  const [value, setValue] = useState('');
  const [lastSearchValue, setLastSearchValue] = useState('');
  const feSearchRef = useRef<HTMLDivElement>(null);
  const searchBarRef = useRef<HTMLDivElement>(null);
  const handleSearchRef = useRef<(searchValue?: string) => void>(() => {});
  const searchDebouncer = useMemo(
    () => createSearchDebouncer((searchValue) => handleSearchRef.current(searchValue)),
    [],
  );

  const cleanupSearch = useCallback(
    (resetSearchState = true) => {
      searchDebouncer.cancel();
      searchRef.current?.clear();
      if (resetSearchState) {
        setLastSearchValue('');
        setSearchResult(null);
      }
    },
    [searchDebouncer],
  );

  const handleClearSearch = useCallback(() => {
    cleanupSearch();
  }, [cleanupSearch]);

  const handleSearch = useCallback((_value?: string) => {
    if (!value && !_value) {
      handleClearSearch();
      return;
    }
    const res = searchRef.current?.search(_value || value);
    setSearchResult({
      index: res?.index,
      count: res?.results.length,
    });
    setLastSearchValue(_value || value);
  }, [handleClearSearch, value]);

  useEffect(() => {
    handleSearchRef.current = handleSearch;
  }, [handleSearch]);

  useLayoutEffect(() => {
    cleanupSearch();
  }, [cleanupSearch, resultContentRevision]);

  useEffect(() => {
    cleanupSearch();
    if (!tableInstance) return;
    const highlightCellStyleBgColor = hexToRgba('#ff0', 20);
    const focuseHighlightCellStyleBgColor = hexToRgba('#ff0', 60);
    const search = new SearchComponent({
      table: tableInstance,
      autoJump: true,
      highlightCellStyle: {
        bgColor: highlightCellStyleBgColor,
      } as any,
      focuseHighlightCellStyle: {
        bgColor: focuseHighlightCellStyleBgColor,
      } as any,
    });
    searchRef.current = search;

    return () => {
      cleanupSearch(false);
      if (searchRef.current === search) {
        searchRef.current = null;
      }
    };
  }, [cleanupSearch, tableInstance]);

  useEffect(() => {
    return () => cleanupSearch(false);
  }, [cleanupSearch]);

  const handleJumpNext = () => {
    if (!searchResult) return;
    const res = searchRef.current?.next();
    setSearchResult({
      index: res?.index,
      count: res?.results.length,
    });
  };

  const handleJumpPrev = () => {
    if (!searchResult) return;
    const res = searchRef.current?.prev();
    setSearchResult({
      index: res?.index,
      count: res?.results.length,
    });
  };

  const handleClose = useCallback(() => {
    cleanupSearch();
    onClose?.();
  }, [cleanupSearch, onClose]);

  useImperativeHandle(
    ref,
    () => ({
      close: handleClose,
      focus: () => {
        searchBarRef.current?.focus?.();
      },
    }),
    [handleClose],
  );

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    // If you press esc, close
    if (e.key === 'Escape') {
      handleClose();
      return;
    }
    // If it is shift+enter, the previous
    if (e.key === 'Enter' && e.shiftKey) {
      handleJumpPrev();
      return;
    }
    if (e.key === 'Enter') {
      // Press enter, if there is no value, clear the search
      if (!value) {
        handleClearSearch();
        return;
      }

      // If there isvalue === lastSearchValue Description
      if (value === lastSearchValue) {
        handleJumpNext();
        return;
      }

      // If there is value, search
      handleSearch();
    }
  };

  // Search box value changes
  const handleChange = (e) => {
    setValue(e.target.value);
    searchDebouncer.schedule(e.target.value);
  };

  return (
    <div className={cx(className, styles.container)} ref={feSearchRef}>
      <SearchBar
        ref={searchBarRef}
        className={styles.resultSetSearchBar}
        placeholder={i18n('workspace.tips.searchResultData')}
        value={value}
        searchAreaId={searchAreaId}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
      />
      {searchResult && (
        <>
          {searchResult.count > 0 ? (
            <div className={styles.count}>
              {i18n('workspace.searchResult.count', searchResult.index + 1, searchResult.count)}
            </div>
          ) : (
            <div className={cx(styles.noSearchResult, styles.count)}>{i18n('common.text.noSearchResult')}</div>
          )}
        </>
      )}
      <div className={styles.buttonGroup}>
        <IconButton
          size={{
            boxSize: 20,
            iconSize: 18,
            borderRadius: 3,
          }}
          icon={ChevronUp}
          title={i18n('workspace.searchResult.prev')}
          onClick={handleJumpPrev}
        />
        <IconButton
          size={{
            boxSize: 20,
            iconSize: 18,
            borderRadius: 3,
          }}
          icon={ChevronDown}
          title={i18n('workspace.searchResult.next')}
          onClick={handleJumpNext}
        />
        <IconButton
          size={{
            boxSize: 20,
            iconSize: 18,
            borderRadius: 3,
          }}
          code="icon-close"
          title={i18n('workspace.searchResult.close')}
          onClick={handleClose}
        />
      </div>
    </div>
  );
});

export default memo(FESearch);
