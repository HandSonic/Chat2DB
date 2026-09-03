import assert from 'node:assert/strict';
import {
  NotificationRefreshCoordinator,
  type NotificationRefreshChannel,
} from './notificationRefreshCoordinator';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((next) => {
    resolve = next;
  });
  return { promise, resolve };
}

async function commitWhenCurrent(
  coordinator: NotificationRefreshCoordinator,
  channel: NotificationRefreshChannel,
  response: Promise<string>,
  commits: string[],
) {
  const owner = coordinator.begin(channel);
  const value = await response;
  if (coordinator.isCurrent(owner)) {
    commits.push(value);
  }
}

async function run() {
  const coordinator = new NotificationRefreshCoordinator();
  const listCommits: string[] = [];
  const unreadCommits: string[] = [];
  const mountList = deferred<string>();
  const mountUnread = deferred<string>();
  const refreshList = deferred<string>();
  const refreshUnread = deferred<string>();

  const mountListRequest = commitWhenCurrent(coordinator, 'list', mountList.promise, listCommits);
  const mountUnreadRequest = commitWhenCurrent(coordinator, 'unread', mountUnread.promise, unreadCommits);
  const refreshListRequest = commitWhenCurrent(coordinator, 'list', refreshList.promise, listCommits);
  const refreshUnreadRequest = commitWhenCurrent(coordinator, 'unread', refreshUnread.promise, unreadCommits);

  refreshList.resolve('refresh-list');
  refreshUnread.resolve('refresh-unread');
  await Promise.all([refreshListRequest, refreshUnreadRequest]);
  mountList.resolve('mount-list');
  mountUnread.resolve('mount-unread');
  await Promise.all([mountListRequest, mountUnreadRequest]);

  const activeListOwner = coordinator.begin('list');
  coordinator.begin('unread');
  const listSurvivesUnreadRefresh = coordinator.isCurrent(activeListOwner);

  const pendingAtUnmount = coordinator.begin('list');
  coordinator.invalidateAll();
  const unmountedOwnerIsCurrent = coordinator.isCurrent(pendingAtUnmount);
  const remountedOwner = coordinator.begin('list');

  assert.deepEqual(
    {
      listCommits,
      unreadCommits,
      listSurvivesUnreadRefresh,
      unmountedOwnerIsCurrent,
      remountedOwnerIsCurrent: coordinator.isCurrent(remountedOwner),
    },
    {
      listCommits: ['refresh-list'],
      unreadCommits: ['refresh-unread'],
      listSurvivesUnreadRefresh: true,
      unmountedOwnerIsCurrent: false,
      remountedOwnerIsCurrent: true,
    },
  );

  console.log('Notification refresh coordinator tests passed.');
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
