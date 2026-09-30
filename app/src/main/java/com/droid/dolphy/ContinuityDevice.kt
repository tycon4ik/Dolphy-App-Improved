package com.droid.dolphy

enum class ContinuityType { DEVICE, ACTION, NOTYOURDEVICE, ICLOUD_SPOOF, NEARBY_INFO }

data class ContinuityDevice(
    val value: String,
    val name: String,
    val deviceType: ContinuityType
)

