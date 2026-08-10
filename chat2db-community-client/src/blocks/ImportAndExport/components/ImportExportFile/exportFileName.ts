const exportFileExtensions: Record<string, string> = {
  CSV: '.csv',
  XLSX: '.xlsx',
  XLS: '.xls',
  SQL: '.sql',
};

const DEFAULT_EXPORT_FILE_NAME = 'chat2db_export';

export const getExportFileExtension = (exportType?: string): string => {
  return exportFileExtensions[exportType || ''] || exportFileExtensions.CSV;
};

export const updateExportFileExtension = (
  fileName: string | undefined,
  exportType?: string,
  fallbackFileName?: string,
): string => {
  const normalizedFileName = fileName?.trim() || fallbackFileName?.trim() || DEFAULT_EXPORT_FILE_NAME;
  const extensionIndex = normalizedFileName.lastIndexOf('.');
  const baseName = extensionIndex > 0 ? normalizedFileName.slice(0, extensionIndex) : normalizedFileName;
  return `${baseName}${getExportFileExtension(exportType)}`;
};

export const getDefaultExportFileName = (tableName: string | undefined, exportType?: string): string => {
  return updateExportFileExtension(undefined, exportType, tableName);
};
