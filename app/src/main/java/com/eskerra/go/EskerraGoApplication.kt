package com.eskerra.go

import android.app.Application
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

class EskerraGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isEmpty()) {
            return
        }

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
        Sentry.addBreadcrumb("app.start")
    }
}
