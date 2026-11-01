package dev.ftassi.cockpit.spotify

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val JOB_ID = 1
private const val PERIOD_MS = 60 * 60 * 1000L

// The history only spans the last 50 tracks, about three hours of a long
// playlist. Reading it every hour, instead of only when Cockpit opens, catches
// each playlist before it scrolls out. Android may delay the job.
class SpotifyHistoryJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = CoroutineScope(Dispatchers.Default).launch {
            SpotifyRecents(this@SpotifyHistoryJob).refresh()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    companion object {
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, SpotifyHistoryJob::class.java))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }
    }
}
