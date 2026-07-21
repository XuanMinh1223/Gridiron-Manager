package com.xuan.gridironmanager.domain.model

data class Team(
    val id: String,
    val city: String,
    val name: String,
    val roster: List<Player>,
    val salaryCapUsed: Long,
    val salaryCapTotal: Long = 250_000_000L
) {
    val fullName: String get() = "$city $name"
}
