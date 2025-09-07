package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hamoon.uncleted.util.NetworkUtils
import com.hamoon.uncleted.util.TripwireManager

class NetworkStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == "android.net.conn.CONNECTIVITY_CHANGE") {
            // When network state changes, check if we now have an active connection.
            if (NetworkUtils.isNetworkAvailable(context)) {
                // If we are connected, perform a "check-in" to reset the tripwire timer.
                TripwireManager.checkIn(context)
            }
        }
    }
}
