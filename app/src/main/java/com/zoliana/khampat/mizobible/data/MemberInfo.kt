package com.zoliana.khampat.mizobible.data

data class MemberInfo(
    val uid: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val membershipType: String = "FREE", // SILVER or GOLD
    val expiresAt: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
