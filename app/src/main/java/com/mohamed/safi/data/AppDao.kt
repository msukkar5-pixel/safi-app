package com.mohamed.safi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---------- expenses ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpense(e: Expense): Long

    @Update
    suspend fun updateExpense(e: Expense)

    @Delete
    suspend fun deleteExpense(e: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE time BETWEEN :from AND :to ORDER BY time DESC")
    fun expensesBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE time BETWEEN :from AND :to ORDER BY time DESC")
    suspend fun expensesBetweenNow(from: Long, to: Long): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY time DESC LIMIT :n")
    fun recentExpenses(n: Int): Flow<List<Expense>>

    @Query("SELECT COUNT(*) FROM expenses WHERE smsHash = :h")
    suspend fun countHash(h: String): Int

    @Query("SELECT * FROM expenses WHERE category = :cat AND isIncome = 0 ORDER BY time DESC")
    fun expensesInCategory(cat: String): Flow<List<Expense>>

    // ---------- transfers ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransfer(t: Transfer): Long

    @Delete
    suspend fun deleteTransfer(t: Transfer)

    @Query("SELECT * FROM transfers WHERE time BETWEEN :from AND :to ORDER BY time DESC")
    fun transfersBetween(from: Long, to: Long): Flow<List<Transfer>>

    @Query("SELECT * FROM transfers WHERE time BETWEEN :from AND :to ORDER BY time DESC")
    suspend fun transfersBetweenNow(from: Long, to: Long): List<Transfer>

    // ---------- bills ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBill(b: Bill): Long

    @Delete
    suspend fun deleteBill(b: Bill)

    @Query("SELECT * FROM bills WHERE active = 1 ORDER BY nextDue")
    fun bills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE active = 1 ORDER BY nextDue")
    suspend fun billsNow(): List<Bill>

    // ---------- debts ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebt(d: Debt): Long

    @Delete
    suspend fun deleteDebt(d: Debt)

    @Query("SELECT * FROM debts ORDER BY closed, CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate")
    fun debts(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE closed = 0")
    suspend fun openDebtsNow(): List<Debt>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun debt(id: Long): Debt?

    // ---------- reminders ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(r: Reminder): Long

    @Delete
    suspend fun deleteReminder(r: Reminder)

    @Query("SELECT * FROM reminders ORDER BY done, time")
    fun reminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE done = 0 ORDER BY time")
    suspend fun activeRemindersNow(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminder(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE refType = :type AND refId = :refId")
    suspend fun remindersFor(type: String, refId: Long): List<Reminder>

    // ---------- locations ----------
    @Insert
    suspend fun insertLocation(l: LocationLog): Long

    @Update
    suspend fun updateLocation(l: LocationLog)

    @Query("SELECT * FROM locations ORDER BY startTime DESC LIMIT 1")
    suspend fun lastLocation(): LocationLog?

    @Query("SELECT * FROM locations WHERE startTime <= :t ORDER BY startTime DESC LIMIT 1")
    suspend fun locationAt(t: Long): LocationLog?

    @Query("SELECT * FROM locations WHERE endTime >= :from AND startTime <= :to ORDER BY startTime")
    fun locationsBetween(from: Long, to: Long): Flow<List<LocationLog>>

    @Query("SELECT * FROM locations WHERE endTime >= :from AND startTime <= :to ORDER BY startTime")
    suspend fun locationsBetweenNow(from: Long, to: Long): List<LocationLog>

    @Query("DELETE FROM locations WHERE endTime < :t")
    suspend fun deleteLocationsBefore(t: Long)

    @Query("UPDATE locations SET placeName = :name WHERE ABS(lat - :lat) < 0.0015 AND ABS(lng - :lng) < 0.0015")
    suspend fun renameLocationsNear(lat: Double, lng: Double, name: String)

    // ---------- saved places ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(p: SavedPlace): Long

    @Query("SELECT * FROM saved_places")
    suspend fun placesNow(): List<SavedPlace>

    // ---------- rules ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(r: MerchantRule)

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun ruleFor(key: String): MerchantRule?

    // ---------- budgets ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(b: Budget)

    @Delete
    suspend fun deleteBudget(b: Budget)

    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE category = :cat")
    suspend fun budgetFor(cat: String): Budget?

    // ---------- car ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCarItem(c: CarItem): Long

    @Delete
    suspend fun deleteCarItem(c: CarItem)

    @Query("SELECT * FROM car_items ORDER BY name")
    fun carItems(): Flow<List<CarItem>>

    @Query("SELECT * FROM car_items")
    suspend fun carItemsNow(): List<CarItem>

    // ---------- chat ----------
    @Insert
    suspend fun insertChat(m: ChatMsg): Long

    @Query("SELECT * FROM chat ORDER BY time")
    fun chat(): Flow<List<ChatMsg>>

    @Query("SELECT * FROM (SELECT * FROM chat ORDER BY time DESC LIMIT :n) ORDER BY time")
    suspend fun chatRecent(n: Int): List<ChatMsg>

    @Query("DELETE FROM chat")
    suspend fun clearChat()
}
