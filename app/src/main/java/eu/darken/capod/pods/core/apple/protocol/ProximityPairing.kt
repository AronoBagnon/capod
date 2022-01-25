package eu.darken.capod.pods.core.apple.protocol

import android.bluetooth.le.ScanFilter
import dagger.Reusable
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import javax.inject.Inject


object ProximityPairing {

    data class Message(
        val type: UByte,
        val length: Int,
        val data: UByteArray
    ) {
        data class Markers(
            val vendor: UByte,
            val length: UByte,
            val device: UShort,
            val podBatteryData: Set<UShort>,
            val caseBatteryData: UShort,
            val deviceColor: UByte,
        )

        fun getRecogMarkers(): Markers = Markers(
            vendor = CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING,
            length = PROXIMITY_PAIRING_MESSAGE_LENGTH.toUByte(),
            device = (((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)).toUShort(),
            // Make comparison order independent
            podBatteryData = setOf(data[4].upperNibble, data[4].lowerNibble),
            caseBatteryData = data[5].lowerNibble,
            deviceColor = data[7]
        )

        override fun toString(): String {
            val dataHex = data.joinToString(separator = " ") { String.format("%02X", it.toByte()) }
            return "ProximityPairing.Message(type=$type, length=$length, data=$dataHex)"
        }
    }

    @Reusable
    class Decoder @Inject constructor() {
        fun decode(message: ContinuityProtocol.Message): Message? {
            if (message.type != CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING) {
                log { "Not a proximity pairing message: $this" }
                return null
            }
            if (message.length != PROXIMITY_PAIRING_MESSAGE_LENGTH) {
                log { "Proximity pairing message has invalid length." }
                return null
            }

            return Message(
                type = message.type,
                length = message.length,
                data = message.data
            )
        }
    }

    fun getBleScanFilter(): Set<ScanFilter> {
        val manufacturerData = ByteArray(CONTINUITY_PROTOCOL_MESSAGE_LENGTH).apply {
            this[0] = CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING.toByte()
            this[1] = PROXIMITY_PAIRING_MESSAGE_LENGTH.toByte()
        }

        val manufacturerDataMask = ByteArray(CONTINUITY_PROTOCOL_MESSAGE_LENGTH).apply {
            this[0] = 1
            this[1] = 1
        }
        val builder = ScanFilter.Builder().apply {
            setManufacturerData(
                ContinuityProtocol.APPLE_COMPANY_IDENTIFIER,
                manufacturerData,
                manufacturerDataMask
            )
        }
        return setOf(builder.build())
    }

    private const val CONTINUITY_PROTOCOL_MESSAGE_LENGTH = 27
    private val CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING = 0x07.toUByte()
    private const val PROXIMITY_PAIRING_MESSAGE_LENGTH = 25
}
