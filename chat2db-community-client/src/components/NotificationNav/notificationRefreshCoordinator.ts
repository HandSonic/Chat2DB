export type NotificationRefreshChannel = 'list' | 'unread';

export interface NotificationRefreshOwner {
  channel: NotificationRefreshChannel;
  generation: number;
}

export class NotificationRefreshCoordinator {
  private generations: Record<NotificationRefreshChannel, number> = {
    list: 0,
    unread: 0,
  };

  begin(channel: NotificationRefreshChannel): NotificationRefreshOwner {
    this.generations[channel] += 1;
    return { channel, generation: this.generations[channel] };
  }

  isCurrent(owner: NotificationRefreshOwner): boolean {
    return this.generations[owner.channel] === owner.generation;
  }

  invalidateAll(): void {
    this.generations.list += 1;
    this.generations.unread += 1;
  }
}
