package eu.darken.capod.reaction.popup

import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.DualAirPods
import eu.darken.capod.reaction.popup.ui.PopUpWindow
import eu.darken.capod.reaction.settings.ReactionSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpReaction @Inject constructor(
    private val podMonitor: PodMonitor,
    private val reactionSettings: ReactionSettings,
    private val popupWindow: PopUpWindow,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val coolDowns = mutableMapOf<PodDevice.Id, Instant>()

    fun monitor(): Flow<Unit> = reactionSettings.showPopUpOnCaseOpen.flow
        .flatMapLatest { isEnabled ->
            if (isEnabled) {
                podMonitor.mainDevice.distinctUntilChangedBy { it?.rawDataHex }
            } else {
                emptyFlow()
            }
        }
        .withPrevious()
        .setupCommonEventHandlers(TAG) { "monitor" }
        .map { (previous, current) ->
            if (previous is DualAirPods? && current is DualAirPods) {
                log(TAG, VERBOSE) {
                    val prev = previous?.rawCaseLidState?.let { String.format("%02X", it.toByte()) }
                    val cur = current.rawCaseLidState.let { String.format("%02X", it.toByte()) }
                    "previous=$prev (${previous?.caseLidState}), current=$cur (${current.caseLidState})"
                }
                log(TAG, VERBOSE) { "previous-id=${previous?.identifier}, current-id=${current.identifier}" }

                val isSameDeviceWithCaseNowOpen =
                    previous?.identifier == current.identifier && previous.caseLidState != current.caseLidState
                val isNewDeviceWithJustOpenedCase =
                    previous?.identifier != current.identifier && previous?.caseLidState != current.caseLidState

                if (isSameDeviceWithCaseNowOpen || isNewDeviceWithJustOpenedCase) {
                    log(TAG) { "Case lid status changed for monitored device." }

                    tryPopWindow(current)
                }
            }
        }

    private suspend fun tryPopWindow(current: DualAirPods) {
        if (current.caseLidState == DualAirPods.LidState.OPEN) {
            log(TAG, INFO) { "Show popup" }

            val now = Instant.now()
            val lastShown = coolDowns[current.identifier] ?: Instant.MIN
            val sinceLastPop = Duration.between(lastShown, now)
            log(TAG) { "Time since last popup: $sinceLastPop" }
            if (sinceLastPop < Duration.ofSeconds(10)) {
                log(TAG, INFO) { "Popup is still on cooldown: $sinceLastPop" }
                return
            } else {
                coolDowns[current.identifier] = Instant.now()
            }

            withContext(dispatcherProvider.Main) {
                popupWindow.show(current)
            }
        } else if (current.caseLidState != DualAirPods.LidState.OPEN) {
            when (current.caseLidState) {
                DualAirPods.LidState.CLOSED -> {
                    log(TAG, INFO) { "Lid was actively closed, resetting cooldown." }
                    coolDowns.remove(current.identifier)
                }
                else -> {
                    log(TAG, WARN) { "Lid was was not actively closed, refreshing cooldown." }
                    coolDowns[current.identifier] = Instant.now()
                }
            }

            log(TAG, INFO) { "Hide popup" }
            withContext(dispatcherProvider.Main) {
                popupWindow.close()
            }
        }
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp")
    }
}