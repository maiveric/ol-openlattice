package com.openlattice.scheduling

import java.time.OffsetDateTime
import java.util.*

data class ScheduledTask(
        val id: UUID,
        val scheduledDateTime: OffsetDateTime,
        val task: RunnableTask
)