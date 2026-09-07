package com.azimulkabir.actua.data.schedules

data class ActualSchedule(
    val id: String, val name: String?, val nextDate: DayDate, val nextDateRowId: String,
    val baseNextDateTimestamp: Long?, val accountId: String, val payeeId: String?,
    val categoryId: String?, val amount: ScheduledAmount?, val dateCondition: ScheduleDateCondition,
)

data class ActualScheduleSummary(
    val id: String, val name: String?, val ruleId: String?, val nextDate: DayDate?,
    val nextDateRowId: String?, val baseNextDateTimestamp: Long?, val accountId: String?,
    val payeeId: String?, val amount: ScheduledAmount?, val amountOp: ScheduleAmountOp,
    val dateOp: String?, val dateCondition: ScheduleDateCondition?, val postsTransaction: Boolean,
    val completed: Boolean, val customUpcomingLength: String?, val sortOrder: Double?,
    val isCustom: Boolean, val conditionsJson: String?, val actionsJson: String?, val categoryId: String?,
) {
    val isRecurring get() = dateCondition is ScheduleDateCondition.Recurring
    val postAmount get() = amount?.postAmount ?: 0
}
