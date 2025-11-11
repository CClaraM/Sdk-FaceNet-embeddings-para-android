package com.dcl.demo.data

data class AppConfig(
    val server: String,
    val port: Int,
    val key: String,
    val enableLogs: Boolean,
    val centroFormacion: String,
    val ambiente: String,
    val regional: String
)