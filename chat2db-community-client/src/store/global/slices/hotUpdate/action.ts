import { clientRuntime } from '@client-runtime';
import { UpdatedStatus } from '@/constants/settings';
import jcefApi from '@/jcef';
import { IHotUpdateConfig } from '@/typings/settings';
import { isDesktop } from '@/utils/env';
import produce from 'immer';
import type { StateCreator } from 'zustand/vanilla';
import { GlobalStore } from '../../store';

export interface HotUpdateAction {
  // Update and restart the app
  updateAndRestartApp: () => Promise<void>;
  // Check for updates
  handleCheckUpdate: () => Promise<boolean>;
  // Synchronize updater-owned preferences
  syncUpdatePreferences: () => Promise<void>;
  // Update hot update configuration
  updateHotUpdateConfig: (property: keyof IHotUpdateConfig, value: any) => Promise<void>;
}

export const createHotUpdateAction: StateCreator<GlobalStore, [['zustand/devtools', never]], [], HotUpdateAction> = (
  set,
  get,
) => {
  let updateAndRestartInFlight: Promise<void> | null = null;

  const runUpdateAndRestart = async () => {
    if (get().updateDetail.status === UpdatedStatus.Updated) {
      get().setUpdateDetail({
        status: UpdatedStatus.Installing,
      });
      try {
        const accepted = await jcefApi.triggerInstallation();
        if (!accepted) {
          get().setUpdateDetail({
            status: UpdatedStatus.UpdateFailed,
          });
          return;
        }
      } catch {
        get().setUpdateDetail({
          status: UpdatedStatus.UpdateFailed,
        });
        return;
      }
    }
    try {
      await jcefApi.restartApp();
    } catch {
      get().setUpdateDetail({
        status: UpdatedStatus.UpdateFailed,
      });
    }
  };

  return {
    updateAndRestartApp: () => {
      if (!clientRuntime.enableAutoUpdate) {
        return Promise.resolve();
      }
      if (updateAndRestartInFlight) {
        return updateAndRestartInFlight;
      }

      const operation = Promise.resolve().then(runUpdateAndRestart);
      updateAndRestartInFlight = operation;
      const clearOperation = () => {
        if (updateAndRestartInFlight === operation) {
          updateAndRestartInFlight = null;
        }
      };
      void operation.then(clearOperation, clearOperation);
      return operation;
    },
    handleCheckUpdate: async () => {
      if (!isDesktop || !clientRuntime.enableAutoUpdate) {
        return false;
      }
      try {
        const res = await jcefApi.appCheckUpdate();
        get().setUpdateDetail({
          status: res.status,
          version: res.version,
        });
        return res.status === UpdatedStatus.Available;
      } catch {
        get().setUpdateDetail({
          status: UpdatedStatus.UpdateFailed,
        });
        return false;
      }
    },
    syncUpdatePreferences: async () => {
      if (!isDesktop || !clientRuntime.enableAutoUpdate) {
        return;
      }
      try {
        const preferences = await jcefApi.updatePreferences();
        set({
          hotUpdateConfig: produce(get().hotUpdateConfig, (draft) => {
            draft.receiveBeta = preferences.receiveBeta;
          }),
        });
      } catch {
        // Keep the last locally confirmed preference when the desktop bridge fails.
      }
    },
    updateHotUpdateConfig: async (property, value) => {
      let persistedValue = value;
      if (property === 'receiveBeta' && isDesktop && clientRuntime.enableAutoUpdate) {
        try {
          const preferences = await jcefApi.updatePreferences({ receiveBeta: Boolean(value) });
          if (!preferences.saved) {
            return;
          }
          persistedValue = preferences.receiveBeta;
        } catch {
          return;
        }
      }
      set({
        hotUpdateConfig: produce(get().hotUpdateConfig, (draft) => {
          draft[property] = persistedValue;
        }),
      });
    },
  };
};
