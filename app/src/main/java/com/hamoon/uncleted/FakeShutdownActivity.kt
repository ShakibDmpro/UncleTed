package com.hamoon.uncleted

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.services.PanicActionService.Severity

class FakeShutdownActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_shutdown)
        PanicActionService.trigger(this, "FAKE_SHUTDOWN", PanicActionService.Severity.MEDIUM)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing here to block the back action.
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}