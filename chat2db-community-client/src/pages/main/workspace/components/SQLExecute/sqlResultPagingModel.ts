import type { DataSourceExecutionTarget } from '@/service/dataSourceExecutionSnapshot';
import type { SqlExecutionEvent } from '@/service/sqlExecutionStream';
import type { IExecuteSqlParams, IManageResultData } from '@/typings';
import {
  createViewTablePagingState,
  normalizeViewTablePageResults,
  reduceViewTablePagingEvent,
  type ViewTablePagingState,
} from '@/hooks/viewTablePagingModel';

export interface SqlResultPagingRequest {
  targetResult: IManageResultData;
  params: IExecuteSqlParams;
}

export interface SqlResultPagingState extends ViewTablePagingState {
  targetResult: IManageResultData;
}

export interface SqlResultPagingTransition {
  state: SqlResultPagingState;
  completedResult?: IManageResultData;
  failed?: boolean;
  errorMessage?: string;
}

export function getSqlResultPagingExecutionTarget(result: IManageResultData) {
  return result.extra?.executionTarget as DataSourceExecutionTarget | undefined;
}

export function buildSqlResultPagingExecuteParams(
  targetResult: IManageResultData,
  params: IExecuteSqlParams,
): IExecuteSqlParams {
  const executionTarget = getSqlResultPagingExecutionTarget(targetResult);
  if (!executionTarget) {
    return { ...params };
  }
  return {
    ...params,
    dataSourceId: executionTarget.dataSourceId,
    dataSourceName: executionTarget.dataSourceName,
    databaseName: executionTarget.databaseName,
    databaseType: executionTarget.databaseType,
    schemaName: executionTarget.schemaName,
  };
}

export function createSqlResultPagingState(
  requestSequence: number,
  request: SqlResultPagingRequest,
): SqlResultPagingState {
  return {
    ...createViewTablePagingState(requestSequence, request.params),
    targetResult: request.targetResult,
  };
}

export function createSqlResultPagingCancellationResult(targetResult: IManageResultData) {
  return { ...targetResult };
}

export function normalizeSqlResultPagingResults(results: IManageResultData[], request: SqlResultPagingRequest) {
  const normalizedResults = normalizeViewTablePageResults(results, request.params);
  const resultSetId = request.params.resultSetId ?? request.targetResult.resultSetId;
  const pagedResult =
    resultSetId === undefined
      ? normalizedResults[0]
      : normalizedResults.find((result) => result.resultSetId === resultSetId);
  return pagedResult ? mergeSqlResultPage(request.targetResult, pagedResult) : undefined;
}

export function resolveSqlResultPagingCompletion(
  results: IManageResultData[],
  request: SqlResultPagingRequest,
  options: {
    streamedResult?: IManageResultData;
    streamFailed?: boolean;
    streamedErrorMessage?: string;
    fallbackErrorMessage?: string;
  } = {},
) {
  const completedResult = options.streamedResult || normalizeSqlResultPagingResults(results, request);
  if (!options.streamFailed && completedResult && completedResult.success !== false) {
    return completedResult;
  }
  const responseError = results.find((result) => result.success === false)?.message;
  throw new Error(
    options.streamedErrorMessage ||
      completedResult?.message ||
      responseError ||
      options.fallbackErrorMessage ||
      'SQL result paging returned no matching result set',
  );
}

export function reduceSqlResultPagingEvent(
  state: SqlResultPagingState,
  event: SqlExecutionEvent,
  requestSequence: number,
): SqlResultPagingTransition {
  if (state.requestSequence !== requestSequence) {
    return { state };
  }
  if (isFailedPagingEvent(event)) {
    return {
      state,
      failed: true,
      errorMessage: getPagingEventErrorMessage(event),
    };
  }
  if (!isTargetPagingResultEvent(state, event)) {
    return { state };
  }
  const viewTableTransition = reduceViewTablePagingEvent(state, event, requestSequence);
  const nextState = {
    ...viewTableTransition.state,
    targetResult: state.targetResult,
  };
  return {
    state: nextState,
    completedResult: viewTableTransition.completedResult
      ? mergeSqlResultPage(state.targetResult, viewTableTransition.completedResult)
      : undefined,
  };
}

export function replaceSqlResultPage(
  current: IManageResultData[],
  targetResult: IManageResultData,
  pagedResult: IManageResultData,
) {
  const targetIdentity = getPagingIdentity(targetResult);
  if (!targetIdentity) {
    return current;
  }
  let replaced = false;
  const next = current.map((result) => {
    if (replaced || getPagingIdentity(result) !== targetIdentity) {
      return result;
    }
    replaced = true;
    return mergeSqlResultPage(targetResult, pagedResult);
  });
  return replaced ? next : current;
}

function mergeSqlResultPage(targetResult: IManageResultData, pagedResult: IManageResultData) {
  return {
    ...targetResult,
    ...pagedResult,
    uuid: targetResult.uuid,
    displayName: targetResult.displayName,
    statementSequence: targetResult.statementSequence,
    extra: {
      ...(pagedResult.extra || {}),
      ...(targetResult.extra || {}),
    },
  };
}

function getPagingIdentity(result: IManageResultData) {
  return result.uuid || result.extra?.resultKey;
}

function isTargetPagingResultEvent(state: SqlResultPagingState, event: SqlExecutionEvent) {
  if (
    event.eventType !== 'resultStarted' &&
    event.eventType !== 'rows' &&
    event.eventType !== 'resultFinished'
  ) {
    return false;
  }
  const expectedResultSetId = state.params.resultSetId ?? state.targetResult.resultSetId;
  return expectedResultSetId !== undefined && event.message?.resultSetId === expectedResultSetId;
}

function isFailedPagingEvent(event: SqlExecutionEvent) {
  return event.eventType === 'failed' || event.message?.success === false;
}

function getPagingEventErrorMessage(event: SqlExecutionEvent) {
  const message = event.message?.errorMessage || event.message?.message;
  return typeof message === 'string' && message ? message : undefined;
}
