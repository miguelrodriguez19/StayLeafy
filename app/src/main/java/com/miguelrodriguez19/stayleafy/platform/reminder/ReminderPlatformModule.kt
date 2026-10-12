package com.miguelrodriguez19.stayleafy.platform.reminder

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderPlatformModule {

    @Binds
    @Singleton
    abstract fun bindReminderDeliveryStore(store: RoomReminderDeliveryStore): ReminderDeliveryStore

    @Binds
    @Singleton
    abstract fun bindReminderNotificationGateway(
        gateway: AndroidReminderNotificationGateway,
    ): ReminderNotificationGateway

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(
        scheduler: WorkManagerReminderScheduler,
    ): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindReminderClock(clock: SystemReminderClock): ReminderClock
}
