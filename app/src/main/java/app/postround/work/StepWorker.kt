package app.postround.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.postround.container
import java.util.concurrent.TimeUnit

/** Every quarter of an hour, even with the app closed: the phone's step counter into the day's steps. */
class StepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.readSensor()
        c.syncHealth()
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) = WorkManager.getInstance(c).enqueueUniquePeriodicWork("steps", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build())
    }
}
