import assert from 'node:assert/strict';
import { getDefaultExportFileName, updateExportFileExtension } from './exportFileName';

assert.equal(getDefaultExportFileName('orders', 'CSV'), 'orders.csv');
assert.equal(getDefaultExportFileName(undefined, 'SQL'), 'chat2db_export.sql');
assert.equal(getDefaultExportFileName('orders.snapshot.csv', 'SQL'), 'orders.snapshot.sql');
assert.equal(updateExportFileExtension('orders-staging-2026-07-27.csv', 'XLSX'), 'orders-staging-2026-07-27.xlsx');
assert.equal(updateExportFileExtension('orders.snapshot.sql', 'CSV'), 'orders.snapshot.csv');
