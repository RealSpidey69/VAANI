package com.bithead.shelter.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContact(val name: String, val number: String)

object TrustedContacts {
    private const val PREFS = "vaani_trusted_contacts"

    fun load(context: Context): List<TrustedContact> = runCatching {
        val data = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("entries", "[]")
        val array = JSONArray(data)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TrustedContact(item.getString("name"), item.getString("number"))
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, name: String, number: String): List<TrustedContact> {
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        require(normalized.count(Char::isDigit) in 7..15) { "Enter a valid phone number" }
        val contact = TrustedContact(name.trim().ifBlank { normalized }, normalized)
        val next = load(context).filterNot { it.number == normalized } + contact
        save(context, next)
        return next
    }

    fun remove(context: Context, number: String): List<TrustedContact> = load(context)
        .filterNot { it.number == number }
        .also { save(context, it) }

    private fun save(context: Context, contacts: List<TrustedContact>) {
        val array = JSONArray()
        contacts.forEach { array.put(JSONObject().put("name", it.name).put("number", it.number)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("entries", array.toString()).apply()
    }
}
