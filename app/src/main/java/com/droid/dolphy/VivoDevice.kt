package com.droid.dolphy

data class VivoDevice(
    val modelId: Int?,
    val localName: String?,
    val name: String,
    val deviceType: Type
) {
    enum class Type { TWS, GAMEPAD }

    companion object {
        const val COMPANY_ID = 0x0837

        const val TYPE_TWS: Byte = 0x08
        const val TYPE_SPORT: Byte = 0x09
        const val PROTOCOL_V1: Byte = 0x01
        const val PROTOCOL_V2: Byte = 0x02

        const val DEFAULT_STATE: Byte = 0x0B
        const val DEFAULT_BATTERY: Byte = 0x64
        const val DEFAULT_FLAGS: Byte = 0x00

        const val HID_SERVICE_UUID = "00001812-0000-1000-8000-00805f9b34fb"
        const val TWS_SERVICE_UUID = "00008486-0000-1000-8000-00805f9b34fb"

        const val TWS_DWELL_MIN_MS = 3000L
        const val TWS_DWELL_MAX_MS = 7000L
        const val TWS_PAYLOAD_REFRESH_MS = 2000L

        fun buildTwsManufacturerData(
            modelId: Int,
            type: Byte = TYPE_TWS,
            protocol: Byte = PROTOCOL_V2,
            state: Byte = DEFAULT_STATE,
            caseBattery: Byte = DEFAULT_BATTERY,
            rightBattery: Byte = DEFAULT_BATTERY,
            leftBattery: Byte = DEFAULT_BATTERY,
            chargeFlags: Byte = DEFAULT_FLAGS
        ): ByteArray {
            val out = ByteArray(18)
            out[0] = type
            out[1] = protocol
            out[8] = state
            out[9] = caseBattery
            out[10] = rightBattery
            out[11] = leftBattery
            out[12] = chargeFlags
            if (modelId in 0..254) {
                out[13] = modelId.toByte()
            } else {
                out[13] = 0xFF.toByte()
                out[14] = 0x00
                out[15] = 0x00
                out[16] = (modelId and 0xFF).toByte()
                out[17] = ((modelId shr 8) and 0xFF).toByte()
            }
            return out
        }

        fun buildTwsManufacturerHex(
            modelId: Int,
            type: Byte = TYPE_TWS,
            protocol: Byte = PROTOCOL_V2
        ): String {
            return buildTwsManufacturerData(modelId, type, protocol)
                .joinToString("") { "%02X".format(it) }
        }

        fun buildTwsManufacturerDataRandomized(
            modelId: Int,
            rnd: kotlin.random.Random = kotlin.random.Random.Default
        ): ByteArray {
            val out = ByteArray(18)
            out[0] = TYPE_TWS
            out[1] = PROTOCOL_V2
            out[8] = DEFAULT_STATE
            out[9] = rnd.nextInt(0x0A, 0x65).toByte()
            out[10] = rnd.nextInt(0x0A, 0x65).toByte()
            out[11] = rnd.nextInt(0x0A, 0x65).toByte()
            out[12] = DEFAULT_FLAGS
            if (modelId in 0..254) {
                out[13] = modelId.toByte()
                out[14] = rnd.nextInt(0x00, 0x100).toByte()
                out[15] = rnd.nextInt(0x00, 0x100).toByte()
                out[16] = rnd.nextInt(0x00, 0x100).toByte()
                out[17] = rnd.nextInt(0x00, 0x100).toByte()
            } else {
                out[13] = 0xFF.toByte()
                out[14] = rnd.nextInt(0x00, 0x100).toByte()
                out[15] = rnd.nextInt(0x00, 0x100).toByte()
                out[16] = (modelId and 0xFF).toByte()
                out[17] = ((modelId shr 8) and 0xFF).toByte()
            }
            return out
        }
    }

    fun toManufacturerData(): ByteArray {
        require(deviceType == Type.TWS) { "Only TWS uses manufacturer data" }
        return buildTwsManufacturerData(requireNotNull(modelId) { "TWS requires modelId" })
    }

    fun toRandomizedManufacturerData(): ByteArray {
        require(deviceType == Type.TWS) { "Only TWS uses manufacturer data" }
        return buildTwsManufacturerDataRandomized(requireNotNull(modelId) { "TWS requires modelId" })
    }

    fun toManufacturerHex(): String {
        return toManufacturerData().joinToString("") { "%02X".format(it) }
    }
}
