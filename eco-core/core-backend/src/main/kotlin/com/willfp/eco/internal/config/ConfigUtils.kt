@file:Suppress("UNCHECKED_CAST")

package com.willfp.eco.internal.config

import com.willfp.eco.core.config.ConfigType
import org.bukkit.configuration.file.YamlConstructor
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.Reader

fun ConfigType.toMap(input: String?): Map<String, Any?> =
    this.handler.toMap(input)

fun ConfigType.toString(map: Map<String, Any?>): String =
    this.handler.toString(map)

fun Any?.constrainConfigTypes(type: ConfigType): Any? = when (this) {
    is Map<*, *> -> EcoConfigSection(type, this.ensureTypesForConfig(type))
    is Iterable<*> -> {
        if (this.firstOrNull() == null) {
            mutableListOf<Any>()
        } else if (this.firstOrNull() is Map<*, *>) {
            this as Iterable<Map<*, *>>
            this.map { map -> EcoConfigSection(type, map.ensureTypesForConfig(type)) }
        } else {
            this.toMutableList()
        }
    }
    else -> this
}

fun Map<*, *>.ensureTypesForConfig(type: ConfigType): Map<String, Any?> {
    val building = mutableMapOf<String, Any?>()

    for ((key, value) in this.entries) {
        if (key == null || value == null) {
            continue
        }

        val constrained = value.constrainConfigTypes(type)

        building[key.toString()] = constrained
    }

    return building
}

fun Reader.readToString(): String {
    val input = this as? BufferedReader ?: BufferedReader(this)
    val builder = StringBuilder()

    var line: String?
    input.use {
        while (it.readLine().also { read -> line = read } != null) {
            builder.append(line)
            builder.append('\n')
        }
    }

    return builder.toString()
}

private val ConfigType.handler: ConfigTypeHandler
    get() = if (this == ConfigType.JSON) JSONConfigTypeHandler else YamlConfigTypeHandler

private abstract class ConfigTypeHandler(
    val type: ConfigType
) {
    fun toMap(input: String?): Map<String, Any?> {
        if (input == null || input.isBlank()) {
            return emptyMap()
        }

        return parseToMap(input).ensureTypesForConfig(type)
    }

    protected abstract fun parseToMap(input: String): Map<*, *>

    abstract fun toString(map: Map<String, Any?>): String
}

private object YamlConfigTypeHandler : ConfigTypeHandler(ConfigType.YAML) {
    private fun newYaml(): Yaml {
        val yamlOptions = DumperOptions()
        val loaderOptions = LoaderOptions()
        val representer = EcoRepresenter()

        loaderOptions.maxAliasesForCollections = Int.MAX_VALUE
        loaderOptions.isAllowDuplicateKeys = false

        yamlOptions.indent = 2
        yamlOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        yamlOptions.isPrettyFlow = true

        representer.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK

        return Yaml(
            YamlConstructor(),
            representer,
            yamlOptions,
            loaderOptions,
        )
    }

    override fun parseToMap(input: String): Map<*, *> {
        return newYaml().load(input) ?: emptyMap<Any, Any>()
    }

    override fun toString(map: Map<String, Any?>): String {
        return newYaml().dump(map)
    }
}

private object JSONConfigTypeHandler : ConfigTypeHandler(ConfigType.JSON) {
    override fun parseToMap(input: String): Map<*, *> {
        return EcoGsonSerializer.gson.fromJson(input, Map::class.java)
    }

    override fun toString(map: Map<String, Any?>): String {
        return EcoGsonSerializer.gson.toJson(map)
    }
}