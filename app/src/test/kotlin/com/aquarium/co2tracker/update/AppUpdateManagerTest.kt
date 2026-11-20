package com.aquarium.co2tracker.update

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testIsNewerVersion_greaterPatch() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.1", "1.0"))
    }

    @Test
    fun testIsNewerVersion_greaterMinor() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.0", "1.0.9"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.2", "1.1.5"))
    }

    @Test
    fun testIsNewerVersion_greaterMajor() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "1.9.9"))
    }

    @Test
    fun testIsNewerVersion_equalVersion() {
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0", "1.0"))
    }

    @Test
    fun testIsNewerVersion_olderVersion() {
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.1"))
        assertFalse(AppUpdateManager.isNewerVersion("v0.9.5", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v0.9", "1.0"))
    }

    @Test
    fun testIsNewerVersion_multiSegments() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.0.1", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.1-beta", "1.0.0"))
    }

    @Test
    fun testExecuteWithPublicFallback_retriesUnauthenticatedOn401() {
        val seenAuthHeaders = mutableListOf<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val auth = req.header("Authorization")
                seenAuthHeaders.add(auth)
                val code = if (auth != null) 401 else 200
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 401) "Unauthorized" else "OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        AppUpdateManager.executeWithPublicFallback(
            httpClient = client,
            url = "https://api.github.com/repos/tobiweber25-netizen/C02-App/releases/latest",
            headers = mapOf("Accept" to "application/vnd.github.v3+json"),
            githubToken = "ghs_expired_actions_token"
        ).use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(2, seenAuthHeaders.size)
        assertEquals("Bearer ghs_expired_actions_token", seenAuthHeaders[0])
        assertNull(seenAuthHeaders[1])
    }

    @Test
    fun testExecuteWithPublicFallback_omitsAuthWhenTokenBlank() {
        val seenAuthHeaders = mutableListOf<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                seenAuthHeaders.add(req.header("Authorization"))
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        AppUpdateManager.executeWithPublicFallback(
            httpClient = client,
            url = "https://api.github.com/repos/tobiweber25-netizen/C02-App/releases/latest",
            headers = mapOf("Accept" to "application/vnd.github.v3+json"),
            githubToken = ""
        ).use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(1, seenAuthHeaders.size)
        assertNull(seenAuthHeaders[0])
    }
}
