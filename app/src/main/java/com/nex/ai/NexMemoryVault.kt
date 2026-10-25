package com.nex.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object NexMemoryVault {

    private const val PREF_NAME = "NEX_MEMORY_VAULT_PREFS"
    private const val KEY_HISTORY = "KEY_CONVERSATION_HISTORY"
    private const val KEY_LEARNED_FACTS = "KEY_LEARNED_FACTS"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    // 1. Save dialog turn to permanent phone storage
    fun saveTurn(role: String, content: String) {
        val p = prefs ?: return
        try {
            val rawHistory = p.getString(KEY_HISTORY, "[]") ?: "[]"
            val array = JSONArray(rawHistory)

            val turn = JSONObject().apply {
                put("role", role)
                put("content", content)
            }
            array.put(turn)

            // Keep last 16 dialog turns permanently on disk
            while (array.length() > 16) {
                array.remove(0)
            }

            p.edit().putString(KEY_HISTORY, array.toString()).apply()
        } catch (_: Exception) {}
    }

    // 2. Load permanent history
    fun loadHistory(): List<JSONObject> {
        val p = prefs ?: return emptyList()
        val list = mutableListOf<JSONObject>()
        try {
            val rawHistory = p.getString(KEY_HISTORY, "[]") ?: "[]"
            val array = JSONArray(rawHistory)
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
        } catch (_: Exception) {}
        return list
    }

    // 3. Save a permanently learned fact/correction about Sir
    fun recordLearnedFact(fact: String) {
        val p = prefs ?: return
        val cleanFact = fact.trim()
        if (cleanFact.isBlank()) return

        try {
            val rawFacts = p.getString(KEY_LEARNED_FACTS, "[]") ?: "[]"
            val array = JSONArray(rawFacts)

            // Avoid duplicate facts
            for (i in 0 until array.length()) {
                if (array.getString(i).equals(cleanFact, ignoreCase = true)) return
            }

            array.put(cleanFact)

            // Keep up to 30 lifetime learned facts/rules about Sir
            while (array.length() > 30) {
                array.remove(0)
            }

            p.edit().putString(KEY_LEARNED_FACTS, array.toString()).apply()
        } catch (_: Exception) {}
    }

    // 4. Return all learned facts to inject directly into AI System Brain
    fun getLearnedContext(): String {
        val p = prefs ?: return ""
        val sb = StringBuilder()
        try {
            val rawFacts = p.getString(KEY_LEARNED_FACTS, "[]") ?: "[]"
            val array = JSONArray(rawFacts)
            if (array.length() > 0) {
                sb.append("\nPERMANENT MEMORY & LEARNED FACTS ABOUT SIR (Never Forget):\n")
                for (i in 0 until array.length()) {
                    sb.append("- ").append(array.getString(i)).append("\n")
                }
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    // 5. Reset memory if user commands
    fun resetVault() {
        prefs?.edit()?.clear()?.apply()
    }
}
