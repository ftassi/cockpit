package dev.ftassi.cockpit.nowplaying

import android.service.notification.NotificationListenerService

// Intentionally empty: being an enabled notification listener is what grants
// access to other apps' media sessions (MediaSessionManager.getActiveSessions).
class SessionListener : NotificationListenerService()
