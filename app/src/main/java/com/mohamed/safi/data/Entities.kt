package com.mohamed.safi.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Money that went out (or came in when isIncome = true). */
@Entity(tableName = "expenses", indices = [Index(value = ["smsHash"], unique = true), Index("time")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String = "AED",
    val amountAed: Double,
    val category: String,
    val merchant: String = "",
    val note: String = "",
    val method: String = "card",          // card | cash
    val bank: String = "",
    val time: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String = "",
    val source: String = "manual",        // sms | manual | voice | receipt | bill
    val smsHash: String? = null,
    val receiptPath: String? = null,
    val isIncome: Boolean = false,
)

/** Money sent to Egypt, tracked in EGP with the AED equivalent at the time. */
@Entity(tableName = "transfers", indices = [Index("time")])
data class Transfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountEgp: Double,
    val rate: Double,                      // EGP per 1 AED at the time
    val amountAed: Double,
    val feesAed: Double = 0.0,
    val category: String,                  // ماما / البيت / دروس الأولاد / ...
    val recipient: String = "",
    val note: String = "",
    val time: Long = System.currentTimeMillis(),
)

/** Recurring obligations: bills, planned transfers, subscriptions, installments. */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = "bill",             // bill | transfer | subscription | installment
    val category: String,
    val amount: Double,
    val currency: String = "AED",
    val frequency: String = "monthly",     // monthly | quarterly | yearly | weekly | once
    val nextDue: Long,
    val remindDaysBefore: Int = 2,
    val note: String = "",
    val active: Boolean = true,
    val lastPaid: Long? = null,
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amount: Double,
    val currency: String = "AED",
    val direction: String = "i_owe",       // i_owe | owed_to_me
    val dueDate: Long? = null,
    val monthlyInstallment: Double? = null,
    val paid: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closed: Boolean = false,
)

val Debt.remaining: Double get() = (amount - paid).coerceAtLeast(0.0)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val time: Long,
    val repeat: String = "none",           // none | daily | weekly | monthly | yearly
    val kind: String = "reminder",         // reminder | appointment
    val location: String = "",
    val remindBeforeMin: Int = 0,
    val alarm: Boolean = false,            // loud alarm-style alert
    val done: Boolean = false,
    val refType: String? = null,
    val refId: Long? = null,
)

/** One stay at a place: from startTime until endTime. */
@Entity(tableName = "locations", indices = [Index("startTime")])
data class LocationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val placeName: String = "",
)

/** Places Mohamed named himself (البيت، الشغل ...). */
@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
)

/** Learned merchant -> category mapping. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val category: String,
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val monthlyLimit: Double,
)

@Entity(tableName = "car_items")
data class CarItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalKm: Int = 0,
    val intervalMonths: Int = 0,
    val lastKm: Int = 0,
    val lastDate: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chat")
data class ChatMsg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,                      // user | assistant
    val text: String,
    val actions: String = "",              // confirmation lines, newline separated
    val time: Long = System.currentTimeMillis(),
)
