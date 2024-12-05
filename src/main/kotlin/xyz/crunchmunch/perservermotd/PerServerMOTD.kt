package xyz.crunchmunch.perservermotd;

import com.google.gson.JsonParser
import com.google.inject.Inject
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.util.Favicon
import net.kyori.adventure.text.minimessage.MiniMessage
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "perservermotd",
    name = "Per-Server MOTD!",
    version = BuildConstants.VERSION,
    description = "Configurable MOTDs for every server (or forced server) without requiring ping passthrough!",
    url = "https://crunchmunch.xyz",
    authors = ["BluSpring"]
)
class PerServerMOTD @Inject constructor(val proxy: ProxyServer, val logger: Logger, @DataDirectory val dataDir: Path) {
    val motds = mutableMapOf<String, ServerMOTD>()

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        load()

        proxy.commandManager.register(BrigadierCommand(
            LiteralArgumentBuilder.literal<CommandSource?>("reloadmotds")
                .requires { it.hasPermission("perservermotds.admin") }
                .executes {
                    load()
                    it.source.sendPlainMessage("Reloaded MOTD information!")

                    1
                }
        ))
    }

    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val address = event.connection.virtualHost.orElse(null) ?: return
        val host = address.hostName

        if (motds.contains(host)) {
            val motd = motds[host]!!
            val builder = event.ping.asBuilder()

            if (motd.description != null) {
                builder.description(MiniMessage.miniMessage().deserialize(motd.description))
            }

            if (motd.faviconPath != null) {
                builder.favicon(Favicon.create(motd.faviconPath))
            }

            event.ping = builder.build()
        }
    }

    private fun load() {
        val configFile = this.dataDir.resolve("config.json").toFile()
        val dataFile = this.dataDir.toFile()

        if (!dataFile.exists()) {
            dataFile.mkdirs()
        }

        try {
            if (!configFile.exists()) {
                configFile.createNewFile()
                configFile.writeText("{}")
                this.logger.warn("Config not set, no MOTD data will be passed through!")
                return
            }

            val json = JsonParser.parseReader(configFile.bufferedReader(Charsets.UTF_8)).asJsonObject

            motds.clear()
            for (host in json.keySet()) {
                val data = json.getAsJsonObject(host)
                motds[host] = ServerMOTD(
                    if (data.has("description"))
                        data.get("description").asString
                    else null,

                    if (data.has("favicon"))
                        this.dataDir.resolve(data.get("favicon").asString)
                    else null
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
