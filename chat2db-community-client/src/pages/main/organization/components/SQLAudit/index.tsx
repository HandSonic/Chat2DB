import AntdTable from '@/components/AntdTable';
import OperationLogFilters, {
  OperationLogFilterValues,
  useDebouncedOperationLogFilters,
} from '@/components/OperationLogFilters';
import { areOperationLogFiltersEqual, buildOperationLogListParams } from '@/components/OperationLogFilters/model';
import PageTitle from '@/components/PageTitle';
import i18n from '@/i18n';
import historyService, { IHistoryRecord, OperationTypeEnum } from '@/service/history';
import { IconfontSvg } from '@chat2db/ui';
import { Button, TablePaginationConfig } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useStyles } from './style';

interface QueryState {
  current: number;
  pageSize: number;
  filters: OperationLogFilterValues;
  refreshVersion: number;
}

const SQLAudit = () => {
  const [dataSource, setDataSource] = useState<IHistoryRecord[]>([]);
  const [filters, setFilters] = useState<OperationLogFilterValues>({});
  const [query, setQuery] = useState<QueryState>({
    current: 1,
    pageSize: 10,
    filters: {},
    refreshVersion: 0,
  });
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const appliedFilters = useDebouncedOperationLogFilters(filters);
  const requestGenerationRef = useRef(0);

  const { styles } = useStyles();

  const columns = useMemo(
    () => [
      {
        title: i18n('team.sqlAudit.table.sql'),
        dataIndex: 'ddl',
        key: 'ddl',
      },
      {
        title: i18n('team.sqlAudit.table.time'),
        dataIndex: 'gmtCreate',
        key: 'gmtCreate',
        width: 200,
      },
      {
        title: i18n('team.sqlAudit.table.user'),
        dataIndex: 'userName',
        key: 'userName',
        width: 160,
      },
    ],
    [],
  );

  useEffect(() => {
    setDataSource([]);
    setTotal(0);
    setQuery((previousQuery) => {
      if (areOperationLogFiltersEqual(previousQuery.filters, appliedFilters)) {
        return previousQuery;
      }
      return {
        ...previousQuery,
        current: 1,
        filters: appliedFilters,
      };
    });
  }, [appliedFilters]);

  useEffect(() => {
    const generation = requestGenerationRef.current + 1;
    requestGenerationRef.current = generation;
    setLoading(true);

    const queryHistoryList = async () => {
      try {
        const res = await historyService.getHistoryList(
          buildOperationLogListParams(query.filters, query.current, query.pageSize, OperationTypeEnum.SQL_AUDIT),
        );

        if (generation !== requestGenerationRef.current) {
          return;
        }
        setDataSource(res?.data ?? []);
        setTotal(res?.total ?? 0);
      } catch {
        // Request errors are surfaced by the shared request layer.
      } finally {
        if (generation === requestGenerationRef.current) {
          setLoading(false);
        }
      }
    };

    void queryHistoryList();

    return () => {
      if (generation === requestGenerationRef.current) {
        requestGenerationRef.current += 1;
      }
    };
  }, [query]);

  const handleTableChange = useCallback((pagination: TablePaginationConfig) => {
    setQuery((previousQuery) => ({
      ...previousQuery,
      current: pagination.current ?? 1,
      pageSize: pagination.pageSize ?? previousQuery.pageSize,
    }));
  }, []);

  const refresh = useCallback(() => {
    setQuery((previousQuery) => ({
      ...previousQuery,
      refreshVersion: previousQuery.refreshVersion + 1,
    }));
  }, []);

  return (
    <div className={styles.wrapper}>
      <PageTitle title={i18n('team.nav.sqlAudit')} />
      <div className={styles.tableTop}>
        <OperationLogFilters className={styles.filters} value={filters} onChange={setFilters} />
        <Button type="primary" icon={<IconfontSvg code="icon-refresh" size="sm" />} onClick={refresh}>
          {i18n('common.button.refresh')}
        </Button>
      </div>
      <AntdTable
        className={styles.antdTable}
        rowKey="id"
        dataSource={dataSource}
        columns={columns}
        loading={loading}
        pagination={{
          current: query.current,
          pageSize: query.pageSize,
          total,
        }}
        onChange={handleTableChange}
      />
    </div>
  );
};

export default SQLAudit;
