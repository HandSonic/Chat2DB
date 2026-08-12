import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css }) => {
  return {
    wrapper: css`
      height: 100%;
      display: flex;
      flex-direction: column;
      /* padding: 36px 24px; */
    `,
    tableTop: css`
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 16px 0;
    `,
    filters: css`
      min-width: 0;
    `,
    empty: css`
      display: flex;
      justify-content: center;
      align-items: center;
    `,
    antdTable: css`
      flex: 1;
      height: 0px;
    `,
  };
});
