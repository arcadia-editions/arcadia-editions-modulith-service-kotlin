package com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.NotificationRepository
import java.math.*
import java.time.*
import java.util.*

class NotificationRepositoryInMemory : InMemoryJpaRepository<Notification, Long>(), NotificationRepository {}
