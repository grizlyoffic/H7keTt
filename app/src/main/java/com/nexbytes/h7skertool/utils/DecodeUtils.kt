package com.nexbytes.h7skertool.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

object DecodeUtils {

    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    data class DecodedField(
        val key: String,
        val value: String,
        val type: String,
        val path: String
    )

    fun prettyPrintJson(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val element = JsonParser.parseString(raw.trim())
            gson.toJson(element)
        } catch (e: JsonSyntaxException) {
            raw
        }
    }

    fun isJson(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return try {
            JsonParser.parseString(raw.trim())
            true
        } catch (_: Exception) { false }
    }

    fun flattenFields(raw: String?, parentPath: String = ""): List<DecodedField> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val element = JsonParser.parseString(raw.trim())
            flattenElement(element, parentPath)
        } catch (_: Exception) { emptyList() }
    }

    private fun flattenElement(
        element: com.google.gson.JsonElement,
        path: String
    ): List<DecodedField> {
        val fields = mutableListOf<DecodedField>()
        when {
            element.isJsonObject -> {
                element.asJsonObject.entrySet().forEach { (k, v) ->
                    val childPath = if (path.isEmpty()) k else "$path.$k"
                    fields.addAll(flattenElement(v, childPath))
                }
            }
            element.isJsonArray -> {
                element.asJsonArray.forEachIndexed { i, v ->
                    val childPath = "$path[$i]"
                    fields.addAll(flattenElement(v, childPath))
                }
            }
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                val type = when {
                    prim.isBoolean -> "boolean"
                    prim.isNumber -> "number"
                    else -> "string"
                }
                val key = path.substringAfterLast('.').substringAfterLast('[').trimEnd(']')
                fields.add(DecodedField(key = key, value = prim.asString, type = type, path = path))
            }
            element.isJsonNull -> {
                val key = path.substringAfterLast('.').substringAfterLast('[').trimEnd(']')
                fields.add(DecodedField(key = key, value = "null", type = "null", path = path))
            }
        }
        return fields
    }

    fun applyFieldEdit(original: String, path: String, newValue: String): String {
        return try {
            val root = JsonParser.parseString(original.trim())
            setValueAtPath(root, path.split('.', '[').filter { it.isNotEmpty() && it != "]" }, newValue)
            gson.toJson(root)
        } catch (_: Exception) { original }
    }

    private fun setValueAtPath(
        element: com.google.gson.JsonElement,
        parts: List<String>,
        newValue: String
    ) {
        if (parts.isEmpty()) return
        val key = parts.first()
        val rest = parts.drop(1)
        when {
            element.isJsonObject && rest.isEmpty() -> {
                val obj = element.asJsonObject
                val existing = obj.get(key)
                val newElement = when {
                    existing?.isJsonPrimitive == true && existing.asJsonPrimitive.isBoolean ->
                        com.google.gson.JsonPrimitive(newValue.toBooleanStrictOrNull() ?: (newValue == "true"))
                    existing?.isJsonPrimitive == true && existing.asJsonPrimitive.isNumber ->
                        com.google.gson.JsonPrimitive(newValue.toDoubleOrNull() ?: newValue.toLongOrNull() ?: 0)
                    else -> com.google.gson.JsonPrimitive(newValue)
                }
                obj.add(key, newElement)
            }
            element.isJsonObject && rest.isNotEmpty() -> {
                setValueAtPath(element.asJsonObject.get(key) ?: return, rest, newValue)
            }
            element.isJsonArray -> {
                val idx = key.trimEnd(']').toIntOrNull() ?: return
                if (rest.isEmpty()) {
                    element.asJsonArray.set(idx, com.google.gson.JsonPrimitive(newValue))
                } else {
                    setValueAtPath(element.asJsonArray[idx], rest, newValue)
                }
            }
        }
    }
}
