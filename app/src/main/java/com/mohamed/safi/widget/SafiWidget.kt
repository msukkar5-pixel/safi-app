package com.mohamed.safi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Carpool
import com.mohamed.safi.data.Obligations
import com.mohamed.safi.data.dayRange
import com.mohamed.safi.data.fmt
import com.mohamed.safi.data.monthRange
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.Prayer
import com.mohamed.safi.fitness.Fit
import com.mohamed.safi.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class SafiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        scope.launch {
            try { render(context, manager, ids) } finally { pr.finish() }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, SafiWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { runCatching { render(ctx, m, ids) } }
        }

        private suspend fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val today = LocalDate.now(zone)
            val car = runCatching {
                val c = Carpool.load(ctx)
                if (!c.enabled) "" else {
                    val d = c.nextDriveDay(today.plusDays(1))
                    val who = d?.let { c.driverFor(it) }
                    if (d == null || who == null) "" else {
                        val label = Carpool.dayLabel(d)
                        if (who == c.myName) "🚗 $label انت اللي هتسوق" else "🚗 $label: $who"
                    }
                }
            }.getOrDefault("")
            val prayer = runCatching {
                val (n, t) = Prayer.nextPrayer()
                "🕌 $n " + t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).replace("AM", "ص").replace("PM", "م")
            }.getOrDefault("")
            val money = runCatching {
                val (f, t) = monthRange(YearMonth.now(zone))
                val spent = SafiApp.db.dao().expensesBetweenNow(f, t).filter { !it.isIncome }.sumOf { it.amountAed }
                val obs = Obligations.forMonth().filter { it.due >= System.currentTimeMillis() - 86_400_000L || it.overdue }.sumOf { it.amountAed }
                "💳 صرفت ${fmt(spent)} • مطلوب ${fmt(obs)} د.إ"
            }.getOrDefault("")
            val food = runCatching {
                val (f, t) = dayRange(today)
                val foods = Fit.dao.foodsBetweenNow(f, t)
                val tg = Fit.targets(Fit.latestWeight())
                if (foods.isEmpty() && tg == null) "" else
                    "🍽 ${foods.sumOf { it.kcal }.toInt()}${tg?.let { "/${it.kcal}" } ?: ""} سعر • ${foods.sumOf { it.protein }.toInt()}g بروتين"
            }.getOrDefault("")

            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_safi)
                v.setTextViewText(R.id.w_car, car.ifBlank { "${com.mohamed.safi.AppName.v}" })
                v.setTextViewText(R.id.w_prayer, prayer)
                v.setTextViewText(R.id.w_money, money)
                v.setTextViewText(R.id.w_food, food)
                v.setOnClickPendingIntent(R.id.w_root, Notifier.openAppIntent(ctx, null, 9_100_000 + id))
                m.updateAppWidget(id, v)
            }
        }
    }
}
