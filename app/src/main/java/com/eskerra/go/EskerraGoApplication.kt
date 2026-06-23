package com.eskerra.go

import android.app.Application
import android.util.Log
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

class EskerraGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isEmpty()) {
            return
        }

        runCatching {
            SentryAndroid.init(this) { options ->
                options.dsn = dsn
                options.environment = if (BuildConfig.DEBUG) "development" else "production"
                options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+" +
                    BuildConfig.VERSION_CODE
                options.isSendDefaultPii = false
                options.tracesSampleRate = 0.0
                options.profilesSampleRate = 0.0
                options.setTag("app", "eskerra-go")
            }
        }.onSuccess {
            Sentry.addBreadcrumb("app.start")
        }.onFailure { error ->
            Log.w(TAG, "Sentry init failed; error reporting disabled", error)
        }
    }

    private companion object {
        const val TAG = "EskerraGoApplication"
    }
}
