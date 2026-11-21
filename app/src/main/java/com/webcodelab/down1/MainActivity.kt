
package com.webcodelab.down1

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : Activity() {

    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText
    private lateinit var display: TextView

    private val prefs by lazy {
        getSharedPreferences("timer", MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val end = prefs.getLong("end", 0L)
            val remaining = ((end - System.currentTimeMillis()) / 1000)
                .coerceAtLeast(0)

            display.text = String.format(
                "%02d:%02d:%02d",
                remaining / 3600,
                (remaining % 3600) / 60,
                remaining % 60
            )

            if (remaining > 0) {
                handler.postDelayed(this, 500)
            } else {
                display.text = "00:00:00"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createChannel()
        requestNotificationPermission()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "WCL COUNTDOWN"
            textSize = 27f
            setTextColor(Color.rgb(20, 70, 160))
            gravity = Gravity.CENTER
        }

        hours = makeInput("Hours")
        minutes = makeInput("Minutes")
        seconds = makeInput("Seconds")

        display = TextView(this).apply {
            text = "00:00:00"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 35, 0, 35)
        }

        val start = Button(this).apply {
            text = "START COUNTDOWN"
            setOnClickListener { startTimer() }
        }

        val cancel = Button(this).apply {
            text = "CANCEL"
            setOnClickListener { cancelTimer() }
        }

        layout.addView(title)
        layout.addView(hours)
        layout.addView(minutes)
        layout.addView(seconds)
        layout.addView(display)
        layout.addView(start)
        layout.addView(cancel)

        setContentView(layout)

        if (prefs.getLong("end", 0L) > System.currentTimeMillis()) {
            handler.post(ticker)
        }
    }

    private fun makeInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 18f
            inputType = 2
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun startTimer() {
        val h = hours.text.toString().toLongOrNull() ?: 0L
        val m = minutes.text.toString().toLongOrNull() ?: 0L
        val s = seconds.text.toString().toLongOrNull() ?: 0L

        val total = h * 3600 + m * 60 + s

        if (total <= 0 || total > 31536000) {
            Toast.makeText(this, "Enter valid time", Toast.LENGTH_SHORT).show()
            return
        }

        val end = System.currentTimeMillis() + total * 1000

        prefs.edit().putLong("end", end).apply()

        val intent = Intent(this, TimerReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= 31 &&
                alarm.canScheduleExactAlarms()
            ) {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, end, pending
                )
            } else {
                alarm.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, end, pending
                )
                Toast.makeText(
                    this,
                    "Exact alarm access unavailable; notification may be late",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: SecurityException) {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, end, pending
            )
        }

        handler.removeCallbacks(ticker)
        handler.post(ticker)

        Toast.makeText(this, "Countdown Started!", Toast.LENGTH_SHORT).show()
    }

    private fun cancelTimer() {
        val intent = Intent(this, TimerReceiver::class.java)

        val pending = PendingIntent.getBroadcast(
            this, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)

        prefs.edit().remove("end").apply()

        handler.removeCallbacks(ticker)
        display.text = "00:00:00"

        Toast.makeText(this, "Timer Cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "countdown_channel",
                "Countdown Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            channel.description = "Countdown completion notifications"

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10
            )
        }
    }
}

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
        val end = prefs.getLong("end", 0L)

        if (end == 0L || System.currentTimeMillis() < end) return

        val manager = context.getSystemService(
            NotificationManager::class.java
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = Notification.Builder(context, "countdown_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WCL Countdown")
            .setContentText("Your countdown has completed!")
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        manager.notify(1001, notification)

        prefs.edit().remove("end").apply()
    }
}
