package com.unjunkify.ui

import com.unjunkify.data.BatteryEvent
import com.unjunkify.data.InMemoryOverheatAlertStore
import com.unjunkify.data.episodeKey
import com.unjunkify.data.repository.BatteryRepository
import com.unjunkify.data.shouldNotifyOverheat
import com.unjunkify.data.tempBand
import com.unjunkify.monitor.AppConsumerRanker
import com.unjunkify.monitor.BatteryMonitor
import com.unjunkify.monitor.BatterySnapshot
import com.unjunkify.monitor.ConsumerUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 7: one persistent alert per overheat episode (day + temp band).
 * Double-trigger same day → 1 notification; dismiss → silent (persisted,
 * survives ViewModel recreation); new day or hotter band → allowed again.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OverheatAlertStoreTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private fun spike(at: Long, tempC: Float) =
        BatteryEvent(at = at, levelPct = 80, tempC = tempC, charging = true)

    private class FakeBattery(val events: List<BatteryEvent>) : BatteryRepository {
        override fun history24hFlow(): Flow<List<BatteryEvent>> = flowOf(events)
        override suspend fun history24hOnce(): List<BatteryEvent> = events
        override suspend fun historySinceOnce(sinceMs: Long): List<BatteryEvent> =
            events.filter { it.at >= sinceMs }
        override suspend fun record(event: BatteryEvent) = Unit
        override suspend fun prune(olderThan: Long) = Unit
    }

    private val monitor = object : BatteryMonitor {
        override suspend fun snapshot() = BatterySnapshot(80, 42f, true)
    }
    private val ranker = object : AppConsumerRanker {
        override suspend fun topConsumers(limit: Int) = emptyList<ConsumerUsage>()
    }

    private fun viewModel(store: InMemoryOverheatAlertStore, events: List<BatteryEvent>) =
        HealthViewModel(monitor, ranker, FakeBattery(events), null, Dispatchers.Unconfined, store)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun tempBand_splitsAt45() {
        assertEquals("42-44", tempBand(42f))
        assertEquals("42-44", tempBand(44.9f))
        assertEquals("45+", tempBand(45f))
        assertEquals("45+", tempBand(52f))
    }

    @Test
    fun firstTrigger_notifies() {
        assertTrue(shouldNotifyOverheat(null, null, now, 42f, true))
    }

    @Test
    fun doubleTrigger_sameDay_singleNotification() {
        val store = InMemoryOverheatAlertStore()
        val episode = episodeKey(now, 42f)
        assertTrue(shouldNotifyOverheat(store.lastAlertedEpisode.value, null, now, 42f, true))
        kotlinx.coroutines.runBlocking { store.markAlerted(episode) }
        assertFalse(
            shouldNotifyOverheat(store.lastAlertedEpisode.value, null, now + 1000L, 43f, true)
        )
        assertTrue(store.isSilenced(episode))
    }

    @Test
    fun dismiss_silencesEpisodePersistently() {
        val store = InMemoryOverheatAlertStore()
        val episode = episodeKey(now, 42f)
        kotlinx.coroutines.runBlocking { store.markDismissed(episode) }
        assertFalse(shouldNotifyOverheat(null, store.lastDismissedEpisode.value, now, 42f, true))
        // Survives "restart": a fresh gate reading the persisted values stays silent.
        assertFalse(shouldNotifyOverheat(null, store.lastDismissedEpisode.value, now, 43f, true))
    }

    @Test
    fun newDay_allowsAlertAgain() {
        val store = InMemoryOverheatAlertStore()
        kotlinx.coroutines.runBlocking { store.markAlerted(episodeKey(now, 42f)) }
        assertTrue(
            shouldNotifyOverheat(
                store.lastAlertedEpisode.value,
                store.lastDismissedEpisode.value,
                now + day,
                42f,
                true,
            )
        )
    }

    @Test
    fun hotterBand_sameDay_isNewEpisode() {
        val store = InMemoryOverheatAlertStore()
        kotlinx.coroutines.runBlocking { store.markAlerted(episodeKey(now, 42f)) }
        assertTrue(shouldNotifyOverheat(store.lastAlertedEpisode.value, null, now, 46f, true))
    }

    @Test
    fun noAlert_belowThresholdOrNotCharging() {
        assertFalse(shouldNotifyOverheat(null, null, now, 41.9f, true))
        assertFalse(shouldNotifyOverheat(null, null, now, 48f, false))
    }

    @Test
    fun viewModel_showsAlert_thenDismissPersistsAcrossRecreation() {
        val store = InMemoryOverheatAlertStore()
        val events = listOf(spike(now, 42f))
        val vm = viewModel(store, events)

        assertNotNull(vm.activeOverheatAlert(events, null))

        vm.dismissAlert(episodeKey(now, 42f))
        assertEquals(episodeKey(now, 42f), store.lastDismissedEpisode.value)
        assertNull(vm.activeOverheatAlert(events, null))

        // Fresh ViewModel, same persisted store (process restart) → still silent.
        val restarted = viewModel(store, events)
        assertNull(restarted.activeOverheatAlert(events, null))
        // ...but a new day alerts again.
        val nextDay = listOf(spike(now + day, 42f))
        assertNotNull(restarted.activeOverheatAlert(nextDay, null))
    }

    @Test
    fun viewModel_noSpike_noAlert() {
        val store = InMemoryOverheatAlertStore()
        val vm = viewModel(store, listOf(spike(now, 39f)))
        assertNull(vm.activeOverheatAlert(listOf(spike(now, 39f)), null))
    }
}
