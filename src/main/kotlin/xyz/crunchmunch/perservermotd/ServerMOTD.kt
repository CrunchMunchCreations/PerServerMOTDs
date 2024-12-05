package xyz.crunchmunch.perservermotd

import java.nio.file.Path

data class ServerMOTD(
    val description: String?,
    val faviconPath: Path?
)
