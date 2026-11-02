package ru.setnote.data

import org.json.JSONArray
import org.json.JSONObject
import ru.setnote.domain.WorkoutSet

internal fun encodeSets(sets: List<WorkoutSet>): String =
    JSONArray()
        .apply {
            sets.forEach { set ->
                put(
                    JSONObject().apply {
                        set.weight?.let { put("w", it) }
                        set.reps?.let { put("r", it) }
                        set.seconds?.let { put("s", it) }
                    }
                )
            }
        }
        .toString()

internal fun decodeSets(raw: String): List<WorkoutSet> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        WorkoutSet(
            if (o.has("w")) o.getDouble("w") else null,
            if (o.has("r")) o.getInt("r") else null,
            if (o.has("s")) o.getInt("s") else null,
        )
    }
}
