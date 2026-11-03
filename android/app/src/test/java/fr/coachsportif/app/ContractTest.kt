package fr.coachsportif.app

import fr.coachsportif.app.data.remote.AppJson
import fr.coachsportif.app.domain.CompletionPayload
import fr.coachsportif.app.domain.DayPlan
import fr.coachsportif.app.domain.ExerciseHistory
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.RecentSessions
import fr.coachsportif.app.domain.SetIn
import fr.coachsportif.app.domain.WeekPlan
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Décode les réponses RÉELLES du backend (fixtures générées depuis Postgres) avec les modèles de l'app. */
class ContractTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText()

    @Test fun mardi3_foot_a_confirmer_et_dos_biceps_reference_par_machine() {
        val d = AppJson.decodeFromString(DayPlan.serializer(), fixture("day_2026-11-03"))
        val byKey = d.activities.associateBy { it.sessionKey }
        assertEquals("pending_confirmation", byKey.getValue("20261103_football").status)
        assertEquals("19:30", byKey.getValue("20261103_football").provisionalTimeLocal)
        assertNull(byKey.getValue("20261103_football").result)
        val back = byKey.getValue("20261103_back")
        val refs = back.splitReferences.associateBy { it.variantKey }
        assertTrue("vertical_row_machine_66" in refs && "vertical_row_machine_120" in refs)
        assertEquals("66 × 10 / 66 × 10 / 66 × 10", Fmt.historical(refs.getValue("vertical_row_machine_66").reference!!.historicalReference))
    }

    @Test fun lundi2_realise_avec_allure_et_fentes_en_pas() {
        val d = AppJson.decodeFromString(DayPlan.serializer(), fixture("day_2026-11-02"))
        val run = d.activities.first { it.sessionKey == "20261102_run" }
        assertEquals("completed", run.status)
        assertEquals(1442, run.result!!.endurance!!.durationS)
        assertEquals("4:48/km", Fmt.pace(run.result!!.endurance!!.paceSPerKm))
        assertEquals(6.0, run.result!!.rpe10!!, 0.0)
        val quad = d.activities.first { it.sessionKey == "20261102_quad" }.result!!
        val lunges = quad.exercises.first { it.variantKey == "walking_lunges_44" }
        assertEquals("44 kg × 32 pas / 44 kg × 33 pas / 44 kg × 33 pas", Fmt.sets(lunges.workSets))
        assertEquals(21, quad.workSetsCount)
    }

    @Test fun mercredi4_revise_a_8km_avec_trace() {
        val d = AppJson.decodeFromString(DayPlan.serializer(), fixture("day_2026-11-04"))
        val run = d.activities.first { it.sessionKey == "20261104_run" }
        assertEquals(2, run.version)
        assertTrue(Fmt.target(run.target).first().contains("8 km"))
        assertEquals("claude_mcp", run.lastChange!!.actor)
        val chest = d.activities.first { it.sessionKey == "20261104_chest" }
        assertTrue(chest.exercises.any { it.isPriority && it.variantKey == "chest_press" })
    }

    @Test fun jour_vide_message() {
        val d = AppJson.decodeFromString(DayPlan.serializer(), fixture("day_empty"))
        assertTrue(d.activities.isEmpty())
        assertEquals("Aucune séance publiée pour ce jour", d.message)
    }

    @Test fun semaine_historique_recents() {
        val w = AppJson.decodeFromString(WeekPlan.serializer(), fixture("week"))
        assertEquals(7, w.days.size)
        assertEquals("pending_confirmation", w.days[5].activities.first { it.sessionKey == "20261107_trail" }.status)
        val h = AppJson.decodeFromString(ExerciseHistory.serializer(), fixture("history_vrow66"))
        assertTrue("vertical_row_machine_120" in h.sameNameOtherVariants)
        val r = AppJson.decodeFromString(RecentSessions.serializer(), fixture("recent"))
        val sep22 = r.sessions.first { it.key == "run_2026_09_22" }
        assertNull(sep22.endurance!!.durationS) // inconnu reste inconnu
    }

    @Test fun payload_snake_case_sans_null() {
        val p = CompletionPayload(plannedSessionKey = "20261103_back", date = "2026-11-03", sport = "strength", rpe10 = 7.0,
            sets = listOf(SetIn(variantKey = "vertical_row_machine_66", setIndex = 1, loadKg = 66.0, reps = 10)))
        val o = AppJson.encodeToJsonElement(p) as JsonObject
        assertTrue("planned_session_key" in o && "rpe_10" in o)
        val s = (o["sets"] as kotlinx.serialization.json.JsonArray)[0] as JsonObject
        assertTrue("variant_key" in s && "load_kg" in s)
        assertFalse("steps" in s) // jamais de valeur inventée
    }

    @Test fun formats() {
        assertEquals(1442, Fmt.parseDuration("24:02"))
        assertEquals(3722, Fmt.parseDuration("1:02:02"))
        assertNull(Fmt.parseDuration("abc"))
        assertEquals(30.5, Fmt.parseDecimal("30,5")!!, 0.0)
    }
}
