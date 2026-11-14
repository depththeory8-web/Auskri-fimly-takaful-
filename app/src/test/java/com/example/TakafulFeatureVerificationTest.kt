package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GmMonthlyTarget
import com.example.data.model.Lead
import com.example.data.model.LeadPermissionStatus
import com.example.data.model.LeadStatus
import com.example.data.model.OfficialProductCatalog
import com.example.data.model.Prospect
import com.example.notifications.FollowUpBootReceiver
import com.example.notifications.FollowUpReminderReceiver
import com.example.notifications.FollowUpReminderScheduler
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.AppStringsProvider
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FollowUpsScreen
import com.example.ui.screens.LeadsScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.ProspectSearchScreen
import com.example.ui.theme.AskariTakafulTheme
import com.example.ui.viewmodel.DashboardMetrics
import com.example.ui.viewmodel.UiState
import com.example.util.OutreachIntents
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29]) // Android 10 baseline verification (Infinix Hot 10 & API 29+ compatibility)
class TakafulFeatureVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardScreen_metricsAndQuickActions_navigateCorrectly() {
        val strings = AppStringsProvider.get(AppLanguage.ENGLISH)
        var navigatedFilter: LeadStatus? = LeadStatus.ARCHIVED
        var clickedSearch = false
        var clickedStudio = false
        var clickedProducts = false
        var clickedFollowUps = false

        val sampleLead = Lead(
            id = "l1",
            name = "Tariq Mahmood",
            company = "Capital Estates",
            designation = "CEO",
            city = "Islamabad",
            status = LeadStatus.FOLLOW_UP.name,
            followUpDate = "2026-01-01"
        )

        composeRule.setContent {
            AskariTakafulTheme(isUrdu = false) {
                DashboardScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    metrics = DashboardMetrics(
                        totalLeads = 5,
                        newProspects = 1,
                        contactedClients = 1,
                        interestedClients = 1,
                        followUpsDue = 1,
                        completedSales = 1
                    ),
                    leadsState = UiState.Success(listOf(sampleLead)),
                    monthlyTargetState = UiState.Success(GmMonthlyTarget(targetMonth = "2026-11")),
                    onNavigateToLeads = { navigatedFilter = it },
                    onNavigateToSearch = { clickedSearch = true },
                    onNavigateToStudio = { clickedStudio = true },
                    onNavigateToFollowUps = { clickedFollowUps = true },
                    onNavigateToProducts = { clickedProducts = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_list").performScrollToIndex(4)
        composeRule.onNodeWithTag("dashboard_search_cta").performClick()
        composeRule.onNodeWithTag("dashboard_studio_cta").performClick()
        composeRule.onNodeWithTag("dashboard_products_cta").performClick()
        composeRule.onNodeWithTag("dashboard_list").performScrollToIndex(2)
        composeRule.onNodeWithTag("metric_total_leads").performClick()
        composeRule.onNodeWithTag("metric_follow_ups_due").performClick()

        assertTrue(clickedSearch)
        assertTrue(clickedStudio)
        assertTrue(clickedProducts)
        assertEquals(null, navigatedFilter)
        assertTrue(clickedFollowUps)
    }

    @Test
    fun prospectSearchScreen_buildsUrlsAndHandsOffToLeadCreation() {
        val strings = AppStringsProvider.get(AppLanguage.ENGLISH)
        var capturedCity = ""
        var capturedIndustry = ""
        var capturedDesignation = ""

        composeRule.setContent {
            AskariTakafulTheme(isUrdu = false) {
                ProspectSearchScreen(
                    strings = strings,
                    onCreateLeadFromSearch = { city, industry, designation ->
                        capturedCity = city
                        capturedIndustry = industry
                        capturedDesignation = designation
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("prospect_search_list").performScrollToIndex(2)
        composeRule.onNodeWithTag("search_city_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("search_city_input").performTextInput("Rawalpindi")
        composeRule.onNodeWithTag("prospect_search_list").performScrollToIndex(3)
        composeRule.onNodeWithTag("save_discovered_lead_btn").performScrollTo().performClick()

        assertEquals("Rawalpindi", capturedCity)
        assertTrue(capturedIndustry.isNotBlank())
        assertTrue(capturedDesignation.isNotBlank())
    }

    @Test
    fun leadsScreen_searchFilterAddLeadAndCsvExport_workTogether() {
        val strings = AppStringsProvider.get(AppLanguage.ENGLISH)
        val leads = listOf(
            Lead(id = "1", name = "Bilal Ahmed", city = "Islamabad", status = LeadStatus.NEW.name),
            Lead(id = "2", name = "Dr. Sara Khan", city = "Lahore", status = LeadStatus.INTERESTED.name)
        )
        val prospects = listOf(
            Prospect(id = "p1", name = "Usman Raza", contact = "+923001112233", status = "Interested")
        )

        var savedLead: Lead? = null

        composeRule.setContent {
            AskariTakafulTheme(isUrdu = false) {
                LeadsScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    leadsState = UiState.Success(leads),
                    prospectsState = UiState.Success(prospects),
                    selectedStatusFilter = null,
                    onSelectStatusFilter = {},
                    prefilledLeadDraft = null,
                    onConsumePrefilledDraft = {},
                    onSaveLead = { savedLead = it },
                    onDeleteLead = {},
                    onOpenAiStudio = {},
                    onOpenFollowUps = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_csv_button").assertIsEnabled()

        // Search filters down cleanly
        composeRule.onNodeWithTag("leads_search_input").performTextInput("Sara")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lead_card_2").assertExists()
        composeRule.onNodeWithTag("lead_card_1").assertDoesNotExist()

        // Clear search
        composeRule.onNodeWithTag("clear_leads_search_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lead_card_1").assertExists()

        // Open direct Add Full Lead dialog and save a valid record
        composeRule.onNodeWithTag("add_full_lead_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lead_name_input").performTextInput("Kamran Siddiqui")
        composeRule.onNodeWithTag("save_lead_dialog_button").performClick()
        composeRule.waitForIdle()

        assertNotNull(savedLead)
        assertEquals("Kamran Siddiqui", savedLead?.name)
    }

    @Test
    fun productsScreen_calculatorAndOfficialCatalog_handoffToAiStudio() {
        val strings = AppStringsProvider.get(AppLanguage.ENGLISH)
        var draftedProductId: String? = null

        composeRule.setContent {
            AskariTakafulTheme(isUrdu = false) {
                ProductsScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    notesState = UiState.Success(emptyMap()),
                    onSaveProductNote = {},
                    onOpenAiWithProduct = { draftedProductId = it.id }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("products_list").performScrollToIndex(2)
        composeRule.onNodeWithText("Child Education").performScrollTo().performClick()
        composeRule.onNodeWithTag("need_calc_draft_ai_button").performScrollTo().performClick()

        assertEquals("iqra", draftedProductId)
        assertEquals(7, OfficialProductCatalog.products.size)
    }

    @Test
    fun followUpReminder_android10NotificationAlarmBootAndCsvExport_work() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Schedule daily reminder on Android 10 (API 29)
        FollowUpReminderScheduler.scheduleDailyMorningReminder(
            context = context,
            hourOfDay = 9,
            dueCount = 3,
            isUrdu = true
        )
        assertTrue(FollowUpReminderScheduler.isEnabled(context))
        assertEquals(9, FollowUpReminderScheduler.getReminderHour(context))

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())

        // 2. Fire FollowUpReminderReceiver and verify posted notification
        FollowUpReminderReceiver().onReceive(context, Intent())
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(notificationManager).allNotifications
        assertTrue("Expected follow-up notification to be posted", posted.isNotEmpty())

        // 3. Verify BootReceiver reschedules when enabled
        FollowUpBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())

        // 4. Verify CSV export builds file in cacheDir/exports and includes Prospects + Leads
        val csv = OutreachIntents.buildLeadsCsv(
            leads = listOf(Lead(id = "1", name = "Ayesha Khan", city = "Islamabad")),
            prospects = listOf(Prospect(id = "p1", name = "Quick Prospect", contact = "03160894696", status = "New"))
        )
        assertTrue(csv.contains("Ayesha Khan"))
        assertTrue(csv.contains("Quick Prospect"))
        assertTrue(csv.contains("03160894696"))

        OutreachIntents.exportLeadsCsv(
            context,
            listOf(Lead(id = "1", name = "Ayesha Khan", city = "Islamabad"))
        )
        val exportFiles = File(context.cacheDir, "exports").listFiles().orEmpty()
        assertTrue("Expected exported CSV file in cache/exports", exportFiles.any { it.name.endsWith(".csv") })
    }

    @Test
    fun followUpsScreen_rendersTabsAndCallButton() {
        val strings = AppStringsProvider.get(AppLanguage.ENGLISH)
        val lead = Lead(
            id = "f1",
            name = "Zafar Iqbal",
            city = "Islamabad",
            contactPhone = "+923001234567",
            permissionStatus = LeadPermissionStatus.CONSENT_OBTAINED.name,
            status = LeadStatus.FOLLOW_UP.name,
            followUpDate = "2020-01-01"
        )

        composeRule.setContent {
            AskariTakafulTheme(isUrdu = false) {
                FollowUpsScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    leadsState = UiState.Success(listOf(lead)),
                    observeHistory = { flowOf(emptyList()) },
                    onUpdateStatusAndDate = { _, _, _ -> },
                    onAddContactLog = { _, _, _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("morning_reminder_card").assertExists()
        composeRule.onNodeWithTag("followup_call_f1").assertExists()
        composeRule.onNodeWithTag("log_contact_btn_f1").assertExists()
    }
}
