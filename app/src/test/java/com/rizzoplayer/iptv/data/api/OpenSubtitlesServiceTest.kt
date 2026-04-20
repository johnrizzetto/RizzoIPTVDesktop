package com.rizzoplayer.iptv.data.api

import android.content.Context
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private val tempDir = File.createTempFile("okhttp_test_cache", "").apply { delete(); mkdirs() }

class OpenSubtitlesServiceTest {

    private val mockWebServer = MockWebServer()

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private abstract class StubContext : android.content.Context() {
        override fun getAssets() = throw UnsupportedOperationException()
        override fun getResources() = throw UnsupportedOperationException()
        override fun getPackageManager() = throw UnsupportedOperationException()
        override fun getPackageName() = ""
        override fun getApplicationInfo() = throw UnsupportedOperationException()
        override fun createDeviceProtectedStorageContext() = throw UnsupportedOperationException()
        override fun getSharedPreferences(name: String, mode: Int) = throw UnsupportedOperationException()
        override fun getContentResolver() = throw UnsupportedOperationException()
        override fun getMainLooper() = throw UnsupportedOperationException()
        override fun getFilesDir() = tempDir
        override fun getExternalFilesDir(type: String?) = throw UnsupportedOperationException()
        override fun getExternalCacheDir() = throw UnsupportedOperationException()
        override fun getDataDir() = tempDir
        override fun getCacheDir() = tempDir
        override fun getCodeCacheDir() = tempDir
        override fun checkPermission(permission: String, pid: Int, uid: Int) = 0
        override fun checkSelfPermission(permission: String) = 0
        override fun getSystemService(name: String) = null
        override fun getSystemServiceName(serviceClass: Class<*>) = ""
        override fun getObbDir() = tempDir
        override fun getExternalCacheDirs() = arrayOf<File>()
        override fun getExternalFilesDirs(type: String?) = arrayOf<File>()
        override fun getObbDirs() = arrayOf<File>()
        override fun getExternalMediaDirs() = arrayOf<File>()
        override fun deleteFile(name: String) = false
        override fun fileList() = arrayOf<String>()
        override fun getDir(name: String, mode: Int) = tempDir
        override fun openFileOutput(name: String, mode: Int) = throw UnsupportedOperationException()
        override fun openFileInput(name: String) = throw UnsupportedOperationException()
        override fun getFileStreamPath(name: String) = throw UnsupportedOperationException()
        override fun getClassLoader() = throw UnsupportedOperationException()
        override fun setTheme(resId: Int) {}
        override fun getTheme() = throw UnsupportedOperationException()
        override fun getPackageResourcePath() = ""
        override fun getPackageCodePath() = ""
        override fun deleteSharedPreferences(name: String) = false
        override fun moveSharedPreferencesFrom(source: android.content.Context, name: String) = false
        override fun getNoBackupFilesDir() = tempDir
        override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) = throw UnsupportedOperationException()
        override fun deleteDatabase(name: String) = false
        override fun getDatabasePath(name: String) = throw UnsupportedOperationException()
        override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?) = throw UnsupportedOperationException()
        override fun moveDatabaseFrom(source: android.content.Context, name: String) = false
        override fun databaseList() = arrayOf<String>()
        override fun getWallpaper() = throw UnsupportedOperationException()
        override fun peekWallpaper() = throw UnsupportedOperationException()
        override fun getWallpaperDesiredMinimumWidth() = 0
        override fun getWallpaperDesiredMinimumHeight() = 0
        override fun setWallpaper(bitmap: android.graphics.Bitmap?) = throw UnsupportedOperationException()
        override fun setWallpaper(stream: java.io.InputStream?) = throw UnsupportedOperationException()
        override fun clearWallpaper() = throw UnsupportedOperationException()
        override fun startActivity(intent: android.content.Intent) = throw UnsupportedOperationException()
        override fun startActivity(intent: android.content.Intent, options: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun startActivities(intents: Array<android.content.Intent>) = throw UnsupportedOperationException()
        override fun startActivities(intents: Array<android.content.Intent>, options: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun startService(intent: android.content.Intent) = null as android.content.ComponentName?
        override fun startForegroundService(intent: android.content.Intent) = null as android.content.ComponentName?
        override fun sendBroadcast(intent: android.content.Intent) = throw UnsupportedOperationException()
        override fun sendBroadcast(intent: android.content.Intent, receiverPermission: String?) = throw UnsupportedOperationException()
        override fun sendBroadcastAsUser(intent: android.content.Intent, userHandle: android.os.UserHandle) = throw UnsupportedOperationException()
        override fun sendBroadcastAsUser(intent: android.content.Intent, userHandle: android.os.UserHandle, receiverPermission: String?) = throw UnsupportedOperationException()
        override fun sendOrderedBroadcast(intent: android.content.Intent, receiverPermission: String?) = throw UnsupportedOperationException()
        override fun sendOrderedBroadcast(intent: android.content.Intent, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun sendOrderedBroadcastAsUser(intent: android.content.Intent, userHandle: android.os.UserHandle, broadcastPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun sendStickyBroadcast(intent: android.content.Intent) = throw UnsupportedOperationException()
        override fun sendStickyOrderedBroadcast(intent: android.content.Intent, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun sendStickyBroadcastAsUser(intent: android.content.Intent, userHandle: android.os.UserHandle) = throw UnsupportedOperationException()
        override fun sendStickyOrderedBroadcastAsUser(intent: android.content.Intent, userHandle: android.os.UserHandle, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun removeStickyBroadcast(intent: android.content.Intent) = throw UnsupportedOperationException()
        override fun removeStickyBroadcastAsUser(intent: android.content.Intent, userHandle: android.os.UserHandle) = throw UnsupportedOperationException()
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter) = null as android.content.Intent?
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter, flags: Int) = null as android.content.Intent?
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter, broadcastPermission: String?, scheduler: android.os.Handler?) = null as android.content.Intent?
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter, broadcastPermission: String?, scheduler: android.os.Handler?, flags: Int) = null as android.content.Intent?
        override fun unregisterReceiver(receiver: android.content.BroadcastReceiver) = throw UnsupportedOperationException()
        override fun bindService(intent: android.content.Intent, conn: android.content.ServiceConnection, flags: Int) = false
        override fun unbindService(conn: android.content.ServiceConnection) = throw UnsupportedOperationException()
        override fun stopService(intent: android.content.Intent) = false
        override fun startIntentSender(intent: android.content.IntentSender, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int) = throw UnsupportedOperationException()
        override fun startIntentSender(intent: android.content.IntentSender, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int, options: android.os.Bundle?) = throw UnsupportedOperationException()
        override fun startInstrumentation(componentName: android.content.ComponentName, profileFile: String?, arguments: android.os.Bundle?) = false
        override fun checkCallingPermission(permission: String) = 0
        override fun checkCallingOrSelfPermission(permission: String) = 0
        override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingPermission(permission: String, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingOrSelfPermission(permission: String, message: String?) = throw UnsupportedOperationException()
        override fun grantUriPermission(toPackage: String, uri: android.net.Uri, modeFlags: Int) = throw UnsupportedOperationException()
        override fun revokeUriPermission(toPackage: String, uri: android.net.Uri, modeFlags: Int) = throw UnsupportedOperationException()
        override fun revokeUriPermission(uri: android.net.Uri, modeFlags: Int) = throw UnsupportedOperationException()
        override fun checkUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int) = 0
        override fun checkUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int) = 0
        override fun checkCallingUriPermission(uri: android.net.Uri?, modeFlags: Int) = 0
        override fun checkCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int) = 0
        override fun enforceUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceUriPermission(uri: android.net.Uri, pid: Int, uid: Int, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun createContext(params: android.content.ContextParams) = throw UnsupportedOperationException()
        override fun createPackageContext(packageName: String, flags: Int) = throw UnsupportedOperationException()
        override fun createContextForSplit(splitName: String) = throw UnsupportedOperationException()
        override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration) = throw UnsupportedOperationException()
        override fun createDisplayContext(display: android.view.Display) = throw UnsupportedOperationException()
        override fun isDeviceProtectedStorage() = false
        override fun getAttributionTag() = null
    }

    private val testContext = object : StubContext() {
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun search_returnsSubtitles_whenApiKeyIsValid() = runTest {
        mockWebServer.start()
        val port = mockWebServer.port
        val service = OpenSubtitlesService(
            testContext,
            apiKey = "test-api-key",
            baseUrl = "http://localhost:$port"
        )

        mockWebServer.enqueue(MockResponse().apply {
            setBody(searchResponseJson)
            setHeader("Content-Type", "application/json")
        })

        val results = service.search("tt0000001")

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun search_returnsEmptyList_whenApiReturnsError() = runTest {
        mockWebServer.start()
        val port = mockWebServer.port
        val service = OpenSubtitlesService(
            testContext,
            apiKey = "bad-key",
            baseUrl = "http://localhost:$port"
        )

        mockWebServer.enqueue(MockResponse().apply {
            setResponseCode(401)
            setBody("""{"error": "Invalid API key"}""")
        })

        val results = service.search("tt9999999")
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_parsesEpisodeCorrectly() = runTest {
        mockWebServer.start()
        val port = mockWebServer.port
        val service = OpenSubtitlesService(
            testContext,
            apiKey = "test-api-key",
            baseUrl = "http://localhost:$port"
        )

        mockWebServer.enqueue(MockResponse().apply {
            setBody(episodeSearchResponseJson)
            setHeader("Content-Type", "application/json")
        })

        val results = service.search("tt0000001", season = 1, episode = 3)

        assertTrue(results.isNotEmpty())
        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("episode_number=3"))
        assertTrue(request.path!!.contains("season_number=1"))
    }

    @Test
    fun download_returnsBytes_whenFileIdIsValid() = runTest {
        mockWebServer.start()
        val port = mockWebServer.port
        val service = OpenSubtitlesService(
            testContext,
            apiKey = "test-api-key",
            baseUrl = "http://localhost:$port"
        )

        mockWebServer.enqueue(MockResponse().apply {
            setBody("WEBVTT\n\n00:00 --> 00:05\nHello world")
            setHeader("Content-Type", "text/vtt")
        })

        val bytes = service.download("file123")
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 0)
    }

    @Test
    fun download_returnsNull_whenServerReturns404() = runTest {
        mockWebServer.start()
        val port = mockWebServer.port
        val service = OpenSubtitlesService(
            testContext,
            apiKey = "test-api-key",
            baseUrl = "http://localhost:$port"
        )

        mockWebServer.enqueue(MockResponse().apply {
            setResponseCode(404)
            setBody("Not found")
            setHeader("Content-Type", "application/json")
        })

        val bytes = service.download("nonexistent-file")
        assertNull(bytes)
    }
}

private const val searchResponseJson = """
{
  "data": [
    {
      "id": "sub-1",
      "type": "subtitle",
      "attributes": {
        "files": [
          {
            "file_id": "12345",
            "filename": "tt0000001_en.srt",
            "cd_number": 1,
            "release": "TestRelease",
            "language": "en",
            "download_count": 100,
            "hearing_impaired": false,
            "hd": true,
            "download_url": "https://opensubtitles.com/download/12345"
          }
        ]
      }
    }
  ]
}
"""

private const val episodeSearchResponseJson = """
{
  "data": [
    {
      "id": "sub-ep1",
      "type": "subtitle",
      "attributes": {
        "files": [
          {
            "file_id": "67890",
            "filename": "Show_S01E03_en.srt",
            "cd_number": 1,
            "release": "ShowRelease",
            "language": "en",
            "download_count": 50,
            "hearing_impaired": false,
            "hd": false,
            "download_url": "https://opensubtitles.com/download/67890"
          }
        ]
      }
    }
  ]
}
"""
