import { IDatabaseBaseInfo } from '@/typings/database';
import importExportServices from '@/service/importExport';
import jcefApi from '@/jcef';
import i18n from '@/i18n';
import { staticModal } from '@chat2db/ui';
import { Input, Select } from 'antd';
import { getDefaultExportFileName } from '../components/ImportExportFile/exportFileName';

export interface ExportSqlFileProps extends IDatabaseBaseInfo {
  scope: 'ALL' | 'SCHEMA' | 'TABLE';
  tableNames?: string[];
  getTaskList?: (p: any) => void;
  openLogModal?: (taskId: number) => void;
  setShowExportToolbar?: (showExportToolbar: boolean) => void;
}

const getDefaultSqlFileName = (props: ExportSqlFileProps) => {
  const candidate = props.tableNames?.[0] || props.schemaName || props.databaseName || 'chat2db_export';
  return getDefaultExportFileName(candidate, 'SQL');
};

export const handleExportSqlFile = async (props: ExportSqlFileProps) => {
  const exportPath = await jcefApi?.selectDirectory();

  if (!exportPath) return;

  const getTaskList = props.getTaskList;
  const openLogModal = props.openLogModal;

  let exportFileName = getDefaultSqlFileName(props);
  let overwriteExistingFile = false;

  staticModal.confirm({
    title: i18n('workspace.menu.exportSqlFile'),
    content: (
      <div style={{ display: 'grid', gap: 12 }}>
        <label>
          {i18n('workspace.importExport.exportFileName')}
          <Input
            autoFocus
            defaultValue={exportFileName}
            onChange={(event) => {
              exportFileName = event.target.value;
            }}
          />
        </label>
        <label>
          {i18n('workspace.importExport.existingFile')}
          <Select
            defaultValue={overwriteExistingFile}
            options={[
              { label: i18n('workspace.importExport.renameIfExists'), value: false },
              { label: i18n('workspace.importExport.overwriteIfExists'), value: true },
            ]}
            onChange={(value) => {
              overwriteExistingFile = value;
            }}
          />
        </label>
      </div>
    ),
    onOk: () => {
      const params = {
        ...props,
        exportPath,
        exportFileName: exportFileName.trim() || getDefaultSqlFileName(props),
        overwriteExistingFile,
      };

      delete params.getTaskList;
      delete params.openLogModal;
      delete params.setShowExportToolbar;

      return importExportServices.exportSqlFile(params).then((res) => {
        getTaskList && getTaskList({ visible: true });
        openLogModal && openLogModal(res);
      });
    },
  });
};
