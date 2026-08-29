import assert from 'node:assert/strict';
import test from 'node:test';
import { DatabaseTypeCode } from '@/constants/common';
import type { SqlExecutionEvent } from '@/service/sqlExecutionStream';
import type { IExecuteSqlParams, IManageResultData } from '@/typings';
import {
  buildSqlResultPagingExecuteParams,
  createSqlResultPagingCancellationResult,
  createSqlResultPagingState,
  normalizeSqlResultPagingResults,
  reduceSqlResultPagingEvent,
  replaceSqlResultPage,
  resolveSqlResultPagingCompletion,
  type SqlResultPagingRequest,
} from './sqlResultPagingModel';

function result(uuid: string, overrides: Partial<IManageResultData> = {}): IManageResultData {
  return {
    uuid,
    dataList: [[{ value: uuid }]] as any,
    headerList: [{ name: '#' }, { name: 'value' }] as any,
    description: '',
    sql: 'SELECT 1',
    originalSql: 'SELECT 1',
    success: true,
    sqlType: 'SELECT' as any,
    refreshTargets: [],
    resultSetId: 1,
    statementSequence: 1,
    pageNo: 1,
    pageSize: 1000,
    fuzzyTotal: '1',
    hasNextPage: false,
    ...overrides,
  };
}

function pagingRequest(targetResult: IManageResultData): SqlResultPagingRequest {
  return {
    targetResult,
    params: {
      ...targetResult.executeSqlParams,
      sql: targetResult.executeSqlParams?.sql || targetResult.originalSql || targetResult.sql,
      pageNo: 2,
      pageSize: 5000,
    },
  };
}

function event(eventType: SqlExecutionEvent['eventType'], message: IManageResultData): SqlExecutionEvent {
  return {
    executionId: 'paging-execution',
    eventType,
    message,
    statementSequence: 1,
    resultSequence: 1,
    resultKey: 'paging-execution:1:1',
  };
}

test('paging always executes against the clicked result execution target', () => {
  const targetResult = result('target', {
    executeSqlParams: {
      dataSourceId: 11,
      databaseName: 'source_database',
      schemaName: 'source_schema',
      sql: 'SELECT 1',
    },
    extra: {
      executionTarget: {
        dataSourceId: 11,
        dataSourceName: 'source',
        databaseName: 'source_database',
        schemaName: 'source_schema',
        databaseType: DatabaseTypeCode.MYSQL,
      },
    },
  });
  const paramsFromCurrentEditor: IExecuteSqlParams = {
    ...targetResult.executeSqlParams,
    sql: targetResult.executeSqlParams?.sql || targetResult.originalSql || targetResult.sql,
    dataSourceId: 99,
    dataSourceName: 'current',
    databaseName: 'current_database',
    schemaName: 'current_schema',
    databaseType: DatabaseTypeCode.POSTGRESQL,
    pageNo: 2,
  };

  assert.deepEqual(buildSqlResultPagingExecuteParams(targetResult, paramsFromCurrentEditor), {
    ...paramsFromCurrentEditor,
    dataSourceId: 11,
    dataSourceName: 'source',
    databaseName: 'source_database',
    schemaName: 'source_schema',
    databaseType: DatabaseTypeCode.MYSQL,
  });
});

test('paging cancellation republishes a new object with the confirmed page', () => {
  const confirmedResult = result('stable-uuid', { pageNo: 2, pageSize: 1000 });
  const cancellationResult = createSqlResultPagingCancellationResult(confirmedResult);

  assert.notEqual(cancellationResult, confirmedResult);
  assert.equal(cancellationResult.uuid, confirmedResult.uuid);
  assert.equal(cancellationResult.pageNo, 2);
  assert.equal(cancellationResult.pageSize, 1000);
});

test('a streamed page replaces the target while preserving its mounted identity', () => {
  const targetResult = result('stable-uuid', {
    displayName: '#4-1 SELECT 1',
    executeSqlParams: { dataSourceId: 11, sql: 'SELECT 1', resultSetId: 1 },
    extra: {
      executionId: 'original-execution',
      executionSequence: 4,
      resultKey: 'original-execution:1:1',
      resultSequence: 1,
      statementSequence: 1,
      executionTarget: { dataSourceId: 11, databaseName: 'source_database' },
    },
  });
  const request = pagingRequest(targetResult);
  const requestSequence = 7;
  let transition = reduceSqlResultPagingEvent(
    createSqlResultPagingState(requestSequence, request),
    event('rows', result('backend-chunk', { dataList: [[{ value: 'page-2-row' }]] as any })),
    requestSequence,
  );
  transition = reduceSqlResultPagingEvent(
    transition.state,
    event('resultFinished', result('backend-finished', { dataList: [], pageNo: 2, pageSize: 5000 })),
    requestSequence,
  );

  assert.equal(transition.completedResult?.uuid, 'stable-uuid');
  assert.equal(transition.completedResult?.displayName, '#4-1 SELECT 1');
  assert.equal(transition.completedResult?.extra?.resultKey, 'original-execution:1:1');
  assert.equal(
    transition.completedResult?.extra?.executionTarget,
    targetResult.extra?.executionTarget,
    'the replacement keeps the original datasource snapshot',
  );
  assert.deepEqual(transition.completedResult?.dataList, [[{ value: 'page-2-row' }]]);
});

test('stream paging filters unrelated results and surfaces failed results without an id', () => {
  const targetResult = result('target-result', {
    resultSetId: 2,
    executeSqlParams: { dataSourceId: 11, sql: 'SELECT 1; SELECT 2', resultSetId: 2 },
  });
  const request = pagingRequest(targetResult);
  const requestSequence = 11;
  let transition = reduceSqlResultPagingEvent(
    createSqlResultPagingState(requestSequence, request),
    event('rows', result('unrelated-row', { resultSetId: 1, dataList: [[{ value: 'wrong' }]] as any })),
    requestSequence,
  );
  transition = reduceSqlResultPagingEvent(
    transition.state,
    event('resultFinished', result('unrelated-finish', { resultSetId: 1 })),
    requestSequence,
  );
  transition = reduceSqlResultPagingEvent(
    transition.state,
    event('updateCount', result('update-count', { resultSetId: 2, updateCount: 1 })),
    requestSequence,
  );
  assert.equal(transition.state.result, undefined);
  assert.equal(transition.completedResult, undefined);

  transition = reduceSqlResultPagingEvent(
    transition.state,
    event('rows', result('target-row', { resultSetId: 2, dataList: [[{ value: 'right' }]] as any })),
    requestSequence,
  );
  transition = reduceSqlResultPagingEvent(
    transition.state,
    event('resultFinished', result('target-finish', { resultSetId: 2, dataList: [] })),
    requestSequence,
  );
  assert.deepEqual(transition.completedResult?.dataList, [[{ value: 'right' }]]);
  assert.equal(transition.completedResult?.resultSetId, 2);

  const failedTransition = reduceSqlResultPagingEvent(
    createSqlResultPagingState(requestSequence, request),
    event(
      'resultFinished',
      result('failed-result', { resultSetId: undefined, success: false, message: 'paging failed' }),
    ),
    requestSequence,
  );
  assert.equal(failedTransition.failed, true);
  assert.equal(failedTransition.errorMessage, 'paging failed');
  assert.equal(failedTransition.completedResult, undefined);
  assert.equal(failedTransition.state.result, undefined);
});

test('web paging rejects failed and non-matching result responses', () => {
  const targetResult = result('target-result', {
    resultSetId: 2,
    executeSqlParams: { dataSourceId: 11, sql: 'SELECT 1; SELECT 2', resultSetId: 2 },
  });
  const request = pagingRequest(targetResult);

  assert.throws(
    () =>
      resolveSqlResultPagingCompletion(
        [result('unrelated-result', { resultSetId: 1 })],
        request,
        { fallbackErrorMessage: 'No matching result' },
      ),
    /No matching result/,
  );
  assert.throws(
    () =>
      resolveSqlResultPagingCompletion(
        [result('failed-result', { resultSetId: undefined, success: false, message: 'backend failed' })],
        request,
      ),
    /backend failed/,
  );
  const streamedResult = normalizeSqlResultPagingResults(
    [result('target-page', { resultSetId: 2 })],
    request,
  );
  assert.throws(
    () =>
      resolveSqlResultPagingCompletion([], request, {
        streamedResult,
        streamFailed: true,
        streamedErrorMessage: 'late stream failure',
      }),
    /late stream failure/,
  );
});

test('replace mode keeps the other results from the same multi-statement execution', () => {
  const firstStatement = result('statement-1', {
    extra: { executionSequence: 8, executionId: 'execution-8', statementSequence: 1 },
  });
  const secondStatement = result('statement-2', {
    statementSequence: 2,
    extra: { executionSequence: 8, executionId: 'execution-8', statementSequence: 2 },
  });
  const pagedResult = result('backend-page', { dataList: [[{ value: 'updated' }]] as any, pageNo: 2 });

  const next = replaceSqlResultPage([firstStatement, secondStatement], firstStatement, pagedResult);

  assert.equal(next.length, 2, 'keep-history off still preserves sibling statement results');
  assert.equal(next[0].uuid, 'statement-1');
  assert.deepEqual(next[0].dataList, [[{ value: 'updated' }]]);
  assert.equal(next[1], secondStatement);
});

test('keep-history mode updates only the clicked result across retained batches', () => {
  const olderResult = result('older-result', {
    extra: { executionSequence: 7, executionId: 'execution-7', statementSequence: 1 },
  });
  const firstStatement = result('statement-1', {
    extra: { executionSequence: 8, executionId: 'execution-8', statementSequence: 1 },
  });
  const secondStatement = result('statement-2', {
    statementSequence: 2,
    extra: { executionSequence: 8, executionId: 'execution-8', statementSequence: 2 },
  });
  const request = pagingRequest(secondStatement);
  const pagedResult = normalizeSqlResultPagingResults(
    [result('backend-page', { dataList: [[{ value: 'updated' }]] as any, pageNo: 2 })],
    request,
  )!;

  const next = replaceSqlResultPage([olderResult, firstStatement, secondStatement], secondStatement, pagedResult);

  assert.deepEqual(
    next.map((item) => item.uuid),
    ['older-result', 'statement-1', 'statement-2'],
  );
  assert.equal(next[0], olderResult);
  assert.equal(next[1], firstStatement);
  assert.deepEqual(next[2].dataList, [[{ value: 'updated' }]]);
});
