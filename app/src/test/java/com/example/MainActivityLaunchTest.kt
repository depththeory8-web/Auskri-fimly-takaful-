package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.resolveDefaultWebClientId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(sdk = [29])
    fun launchMainActivity_api29_showsSignInInsteadOfCrashing() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button").assertExists()
    }

    @Test
    @Config(sdk = [36])
    fun launchMainActivity_api36_showsSignInInsteadOfCrashing() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button").assertExists()
    }

    @Test
    @Config(sdk = [24])
    fun launchMainActivity_api24_showsSignInInsteadOfCrashing() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button").assertExists()
    }

    @Test
    @Config(sdk = [29])
    fun googleServicesPlugin_generatesValidWebClientId_andAuthToggleWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clientId = resolveDefaultWebClientId(context)
        assertNotNull("default_web_client_id must be generated from app/google-services.json", clientId)
        assertTrue("Web Client ID must belong to project 560884538260", clientId!!.startsWith("560884538260-"))
        assertTrue("Web Client ID must end with .apps.googleusercontent.com", clientId.endsWith(".apps.googleusercontent.com"))

        // Verify language toggle preserves Urdu & English options on SignInScreen
        composeRule.waitForIdle()
        composeRule.onNodeWithText("English").assertExists()
        composeRule.onNodeWithTag("auth_language_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("اردو").assertExists()
    }

    @Test
    @Config(sdk = [29])
    fun googleSignIn_whenWebClientIdMissing_reportsConfigurationErrorGracefully() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val contextWithoutClientId = object : ContextWrapper(baseContext) {
            override fun getPackageName(): String = "com.missing.package.for.test"
            override fun getResources(): Resources = baseContext.resources
        }
        var capturedError: String? = null
        onGoogleSignInClicked(
            context = contextWithoutClientId,
            credentialManager = CredentialManager.create(baseContext),
            onAuthSuccess = {},
            onAuthError = { capturedError = it },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        assertEquals(
            "Google Sign-In configuration missing: default_web_client_id not found",
            capturedError
        )
    }
}
