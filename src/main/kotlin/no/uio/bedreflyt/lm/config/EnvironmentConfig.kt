package org.smolang.no.uio.bedreflyt.lm.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

@Configuration
open class EnvironmentConfig {

    private val variables: MutableMap<String, String> = mutableMapOf()

    @PostConstruct
    fun init() {
        variables.putAll(System.getenv())
    }

    open fun get(key: String): String? = variables[key]

    open fun set(key: String, value: String) {
        variables[key] = value
    }

    fun getOrDefault(key: String, default: String): String = variables[key] ?: default
}