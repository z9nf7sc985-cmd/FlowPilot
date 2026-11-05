package com.flowpilot.automation.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AutomationRepository(context: Context) {
    private val dir = File(context.filesDir, "automations").also { it.mkdirs() }
    private val templatesDir = File(context.filesDir, "templates").also { it.mkdirs() }
    private val mutex = Mutex()
    private val _automations = MutableStateFlow<List<Automation>>(emptyList())
    val automations: StateFlow<List<Automation>> = _automations.asStateFlow()

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    init {
        reload()
    }

    fun reload() {
        val list = dir.listFiles()?.filter { it.extension == "json" }?.mapNotNull { f ->
            runCatching { json.decodeFromString<Automation>(f.readText()) }.getOrNull()
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
        _automations.value = list
    }

    suspend fun save(automation: Automation) = mutex.withLock {
        val updated = automation.copy(updatedAt = System.currentTimeMillis())
        File(dir, "${updated.id}.json").writeText(json.encodeToString(updated))
        reload()
    }

    suspend fun delete(id: String) = mutex.withLock {
        File(dir, "$id.json").delete()
        reload()
    }

    fun get(id: String): Automation? = _automations.value.find { it.id == id }
        ?: File(dir, "$id.json").takeIf { it.exists() }?.let {
            runCatching { json.decodeFromString<Automation>(it.readText()) }.getOrNull()
        }

    fun exportJson(id: String): String? = get(id)?.let { json.encodeToString(it) }

    suspend fun importJson(raw: String): Automation {
        val a = json.decodeFromString<Automation>(raw)
        val withNewId = a.copy(id = java.util.UUID.randomUUID().toString(), name = a.name + " (imported)")
        save(withNewId)
        return withNewId
    }

    suspend fun ensureExamples(examples: List<Automation>) = mutex.withLock {
        if (_automations.value.isNotEmpty()) return
        examples.forEach { a ->
            File(dir, "${a.id}.json").writeText(json.encodeToString(a))
        }
        reload()
    }

    fun templatesDir(): File = templatesDir

    fun listTemplates(): List<String> =
        templatesDir.listFiles()?.filter { it.extension in listOf("png", "jpg", "webp") }
            ?.map { it.name } ?: emptyList()
}
