package fr.coachsportif.app

import android.app.Application
import fr.coachsportif.app.data.Repository
import fr.coachsportif.app.data.auth.SessionStore
import fr.coachsportif.app.data.local.LocalDb
import fr.coachsportif.app.data.remote.SupabaseApi
import fr.coachsportif.app.sync.SyncWorker

class CoachApp : Application() {
    val repository: Repository by lazy {
        Repository(
            api = SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
            sessions = SessionStore(this),
            db = LocalDb.create(this),
            scheduleSync = { SyncWorker.schedule(this) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this) // vide la file éventuelle au démarrage
    }
}
