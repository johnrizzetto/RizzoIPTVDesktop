package com.rizzoplayer.iptv.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class OpenSubtitlesServiceTest {

    private val mockWebServer = MockWebServer()

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private val tempDir = File.createTempFile("okhttp_test_cache", "").apply { delete(); mkdirs() }

    @Test
    fun search_returnsSubtitles_whenApiKeyIsValid() = runTest {
        mockWebServer.start()
        val port = mockWebServer.port
        val service = OpenSubtitlesService(
            tempDir,
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
            tempDir,
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
            tempDir,
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
            tempDir,
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
            tempDir,
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