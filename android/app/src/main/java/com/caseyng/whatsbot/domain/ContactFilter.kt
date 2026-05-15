package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class ContactFilter {
    @Serializable
    object All : ContactFilter()

    @Serializable
    data class Specific(val jids: List<String>) : ContactFilter()

    @Serializable
    object GroupsOnly : ContactFilter()

    @Serializable
    object IndividualsOnly : ContactFilter()

    @Serializable
    data class AllExcept(val jids: List<String>) : ContactFilter()
}
