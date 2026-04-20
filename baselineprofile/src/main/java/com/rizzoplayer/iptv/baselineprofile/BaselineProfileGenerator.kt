package com.rizzoplayer.iptv.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.rizzoplayer.iptv.v2",
    ) {
        // Launch the app
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage("com.rizzoplayer.iptv.v2")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndWait(intent)

        // Wait for home screen to be interactive
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle(3000)

        // Navigate to Movies section (swipe right twice from Live tab)
        repeat(2) {
            device.executeShellCommand("input swipe 900 540 200 540")
            device.waitForIdle(500)
        }

        // Scroll Movies grid down
        repeat(5) {
            device.executeShellCommand("input swipe 540 1200 540 400")
            device.waitForIdle(300)
        }

        // Press back
        device.executeShellCommand("input keyevent 4")
        device.waitForIdle(500)
    }
}
