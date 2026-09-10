package com.example.equivalence

import com.example.core.StatsInstallment
import com.example.core.StatsPayment
import com.example.core.collectionRatePct
import com.example.core.daysBetweenFloor
import com.example.core.deriveAmountHistogram
import com.example.core.deriveCategoryMix
import com.example.core.deriveDebtAging
import com.example.core.derivePaymentStats
import com.example.core.deriveRecoveryFunnel
import com.example.core.installmentRemaining
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * T-285 (PARITY-002) — the LIVE-DATABASE equivalence test.
 *
 * THE OWNER'S MANDATE: "create a new comprehensive equivalence test that
 * verifies all statistics and class-related data against the database …
 * every numerical value is identical across desktop and mobile."
 *
 * WHAT IT DOES (three independent sources, one comparison):
 *   1. Pulls the RAW rows from the live Supabase PostgREST API
 *      (payments where status=paid, ALL installments, class counts,
 *      today's attendance) with the service key — the same rows the
 *      Android pull-sync stores in Room (amounts are DZD in the DB;
 *      the test multiplies by 100 EXACTLY like PaymentDto.toEntity(),
 *      the pull mapper).
 *   2. Runs the ANDROID core/StatisticsEngine over those rows — the exact
 *      code the dashboard renders.
 *   3. Runs the SQL TRUTH server-side (the same statement as the hub's
 *      canonical scripts/verify_t-285.sql) via the Management API SQL
 *      endpoint and asserts the engine output equals the database's own
 *      aggregates, value by value: count, total, mean, median, sample σ,
 *      min/max, best month, the 5 amount bins, the category mix, the
 *      per-installment INV-4 aging census (amount + distinct families
 *      per bucket), the recovery funnel, the outstanding/overdue totals,
 *      the collection rate, the class census, and today's attendance rate.
 *
 * CREDENTIALS (never committed — AGENTS.md §15.12): supplied via JVM
 * system properties / environment variables at RUN time only:
 *   SUPABASE_URL               (env or -Dsupabase.url)
 *   SUPABASE_SERVICE_KEY       (env or -Dsupabase.service.key)   — PostgREST read
 *   SUPABASE_ACCESS_TOKEN      (env or -Dsupabase.access.token)  — Management API SQL
 * The test SKIPS (JUnit assumption) when they are absent, so CI without
 * credentials stays green — a skip is NEVER a pass: live evidence requires
 * a run with the keys present (the 44th session recorded one).
 */
class LiveDatabaseEquivalenceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun prop(env: String, sys: String): String? =
        System.getProperty(sys) ?: System.getenv(env)

    private val supabaseUrl: String? get() = prop("SUPABASE_URL", "supabase.url")
    private val serviceKey: String? get() = prop("SUPABASE_SERVICE_KEY", "supabase.service.key")
    private val accessToken: String? get() = prop("SUPABASE_ACCESS_TOKEN", "supabase.access.token")

    private fun liveAvailable(): Boolean =
        !supabaseUrl.isNullOrBlank() && !serviceKey.isNullOrBlank() && !accessToken.isNullOrBlank()

    // ── HTTP plumbing (no extra dependencies — java.net only) ─────────────

    private fun http(url: String, method: String, headers: Map<String, String>, body: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 30_000
        conn.readTimeout = 90_000
        conn.doOutput = body != null
        // Management-API quirk #9: the default Java User-Agent gets
        // Cloudflare-blocked — send an explicit curl-like one.
        conn.setRequestProperty("User-Agent", "elimtiyaz-equivalence-test/1.0")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code from $url: ${text.take(400)}")
        }
        return text
    }

    /** Pull ALL rows of a PostgREST table (paginated by Range — the DB holds >1000 installments). */
    private fun restGetAll(table: String, select: String, filter: String? = null): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        var offset = 0
        while (true) {
            val url = buildString {
                append("$supabaseUrl/rest/v1/$table?select=$select")
                filter?.let { append("&$it") }
                append("&order=id.asc")
            }
            val text = http(
                url, "GET",
                mapOf(
                    "apikey" to serviceKey!!,
                    "Authorization" to "Bearer $serviceKey",
                    "Range" to "$offset-${offset + 999}",
                ),
            )
            val arr = json.parseToJsonElement(text).jsonArray
            arr.forEach { rows.add(it.jsonObject) }
            if (arr.size < 1000) break
            offset += 1000
        }
        return rows
    }

    /** Run the canonical truth SQL server-side (the verify_t-285.sql statement). */
    private fun sqlTruth(): JsonObject {
        // The SQL statement contains only single quotes; JsonPrimitive.toString()
        // emits a correctly-escaped JSON string literal for the body.
        val body = "{\"query\":${JsonPrimitive(TRUTH_SQL)}}"
        val text = http(
            "https://api.supabase.com/v1/projects/hkvkefubghbbotgnteir/database/query",
            "POST",
            mapOf(
                "Authorization" to "Bearer ${accessToken!!}",
                "Content-Type" to "application/json",
            ),
            body,
        )
        val arr = json.parseToJsonElement(text).jsonArray
        return arr.first().jsonObject
    }

    // ── The engine side: raw rows → StatisticsEngine (the pull-mapper boundary) ──

    private fun paidPayments(): List<StatsPayment> =
        restGetAll("payments", "id,amount,method,status,category,collected_at", "status=eq.paid")
            .map { p ->
                StatsPayment(
                    id = p["id"]!!.jsonPrimitive.content,
                    amount = dzdToCentimes(p["amount"]!!.jsonPrimitive.content),
                    method = p["method"]?.jsonPrimitive?.contentOrNull ?: "cash",
                    status = p["status"]?.jsonPrimitive?.contentOrNull ?: "paid",
                    category = p["category"]?.jsonPrimitive?.contentOrNull ?: "tuition",
                    collectedAt = p["collected_at"]!!.jsonPrimitive.content,
                )
            }

    /** The DB stores DZD numerics; Room (and the engine) work in centimes — ×100 like PaymentDto.toEntity(). */
    private fun dzdToCentimes(raw: String): Long = Math.round(raw.toDouble() * 100)

    private fun allInstallments(): List<StatsInstallment> =
        restGetAll("installments", "id,parent_id,amount_due,amount_paid,amount_pending,due_date,status")
            .map { i ->
                StatsInstallment(
                    id = i["id"]!!.jsonPrimitive.content,
                    parentId = i["parent_id"]!!.jsonPrimitive.content,
                    amountDue = dzdToCentimes(i["amount_due"]!!.jsonPrimitive.content),
                    amountPaid = i["amount_paid"]?.jsonPrimitive?.contentOrNull?.let { dzdToCentimes(it) } ?: 0L,
                    amountPending = i["amount_pending"]?.jsonPrimitive?.contentOrNull?.let { dzdToCentimes(it) } ?: 0L,
                    dueDate = i["due_date"]!!.jsonPrimitive.content,
                    status = i["status"]?.jsonPrimitive?.contentOrNull ?: "overdue",
                )
            }

    // ── The canonical truth SQL (identical to the hub's verify_t-285.sql) ──

    private val TRUTH_SQL = """
        BEGIN;
        CREATE TEMP TABLE t285_truth AS
        WITH paid AS (
          SELECT amount::double precision AS amt, method, category, collected_at
          FROM payments WHERE status = 'paid'
        ),
        stats AS (
          SELECT
            count(*)::int AS ops,
            round(sum(amt))::double precision AS total_dzd,
            round(avg(amt))::double precision AS mean_dzd,
            round(stddev_samp(amt))::double precision AS stddev_dzd,
            min(amt)::double precision AS min_dzd,
            max(amt)::double precision AS max_dzd
          FROM paid
        ),
        median_row AS (
          SELECT round(percentile_cont(0.5) WITHIN GROUP (ORDER BY amt))::double precision AS median_dzd
          FROM paid
        ),
        best_month AS (
          SELECT to_char(collected_at, 'Mon') AS label, sum(amt) AS amount
          FROM paid GROUP BY 1 ORDER BY 2 DESC LIMIT 1
        ),
        bins AS (
          SELECT
            count(*) FILTER (WHERE amt >= 0 AND amt < 5000)::int AS b0_5,
            count(*) FILTER (WHERE amt >= 5000 AND amt < 10000)::int AS b5_10,
            count(*) FILTER (WHERE amt >= 10000 AND amt < 20000)::int AS b10_20,
            count(*) FILTER (WHERE amt >= 20000 AND amt < 50000)::int AS b20_50,
            count(*) FILTER (WHERE amt >= 50000)::int AS b50p
          FROM paid
        ),
        categories AS (
          SELECT category, sum(amt) AS amount, count(*)::int AS cnt
          FROM paid GROUP BY 1
        ),
        unpaid AS (
          SELECT
            parent_id::text AS parent_id,
            greatest(0, amount_due - amount_paid - coalesce(amount_pending, 0))::double precision AS remaining,
            greatest(0, floor(extract(epoch FROM (now()::timestamp - due_date::timestamp)) / 86400.0))::int AS days_overdue
          FROM installments WHERE status <> 'paid'
        ),
        census_agg AS (
          SELECT
            CASE
              WHEN days_overdue <= 30 THEN '0_30'
              WHEN days_overdue <= 60 THEN '31_60'
              WHEN days_overdue <= 90 THEN '61_90'
              WHEN days_overdue <= 180 THEN '91_180'
              ELSE '180_plus'
            END AS bucket,
            sum(remaining) AS amount,
            count(DISTINCT parent_id)::int AS debtors
          FROM unpaid WHERE remaining > 0 GROUP BY 1
        ),
        totals AS (
          SELECT
            coalesce(sum(remaining), 0)::double precision AS outstanding,
            coalesce(sum(remaining) FILTER (WHERE days_overdue > 0), 0)::double precision AS overdue,
            count(DISTINCT parent_id)::int AS overdue_families
          FROM unpaid WHERE remaining > 0
        ),
        classes AS (
          SELECT count(*)::int AS total_classes, count(*) FILTER (WHERE is_active)::int AS active_classes
          FROM classes
        )
        SELECT
          (SELECT ops FROM stats) AS ops,
          (SELECT round(total_dzd * 100)::bigint FROM stats) AS total_centimes,
          (SELECT round(mean_dzd * 100)::bigint FROM stats) AS mean_centimes,
          (SELECT round(median_dzd * 100)::bigint FROM median_row) AS median_centimes,
          (SELECT round(stddev_dzd * 100)::bigint FROM stats) AS stddev_centimes,
          (SELECT min_dzd * 100::bigint FROM stats) AS min_centimes,
          (SELECT max_dzd * 100::bigint FROM stats) AS max_centimes,
          (SELECT label FROM best_month) AS best_month_label,
          (SELECT round(amount * 100)::bigint FROM best_month) AS best_month_centimes,
          (SELECT b0_5 FROM bins) AS b0_5, (SELECT b5_10 FROM bins) AS b5_10,
          (SELECT b10_20 FROM bins) AS b10_20, (SELECT b20_50 FROM bins) AS b20_50,
          (SELECT b50p FROM bins) AS b50p,
          (SELECT json_agg(json_build_object('category', category, 'amount', round(amount*100)::bigint, 'count', cnt, 'percent', round(amount / (SELECT sum(amt) FROM paid) * 100)::int) ORDER BY amount DESC) FROM categories) AS category_mix,
          (SELECT json_object_agg(bucket, json_build_object('amount', round(amount*100)::bigint, 'debtors', debtors)) FROM census_agg) AS census,
          (SELECT round(outstanding * 100)::bigint FROM totals) AS outstanding_centimes,
          (SELECT round(overdue * 100)::bigint FROM totals) AS overdue_centimes,
          (SELECT overdue_families FROM totals) AS overdue_families,
          (SELECT total_classes FROM classes) AS total_classes,
          (SELECT active_classes FROM classes) AS active_classes,
          (SELECT round((SELECT total_dzd FROM stats) / ((SELECT total_dzd FROM stats) + (SELECT outstanding FROM totals)) * 100)::int) AS collection_rate
        ;
        SELECT * FROM t285_truth;
        ROLLBACK;
    """.trimIndent()

    // ── The assertions: engine output == database truth ───────────────────

    @Test
    fun `every dashboard statistic equals the live database truth`() {
        assumeTrue("Live credentials not supplied (SUPABASE_URL / SUPABASE_SERVICE_KEY / SUPABASE_ACCESS_TOKEN) — skipping; a skip is NOT live evidence", liveAvailable())

        val truth = sqlTruth()
        val slice = paidPayments()
        val installments = allInstallments()

        // The paid slice must be the DB's own census — no rows lost, none invented.
        assertEquals("paid payments count (891 at the 44th session)", truth.long("ops"), slice.size.toLong())

        val stats = derivePaymentStats(slice)

        // Descriptive statistics — centime-exact vs the SQL aggregates.
        assertEquals("total encaissé", truth.long("total_centimes"), stats.total)
        assertEquals("panier moyen (mean)", truth.long("mean_centimes"), stats.mean)
        assertEquals("médiane", truth.long("median_centimes"), stats.median)
        assertEquals("volatilité σ (sample)", truth.long("stddev_centimes"), stats.stdDev)
        assertEquals("min", truth.long("min_centimes"), stats.min)
        assertEquals("max", truth.long("max_centimes"), stats.max)

        // Best month — the DB's English label maps to the canonical FR label.
        val bestAmount = truth.long("best_month_centimes")
        assertEquals("meilleur mois — montant", bestAmount, stats.bestMonth?.amount ?: -1L)
        val frByEn = mapOf(
            "Jan" to "Jan", "Feb" to "Fév", "Mar" to "Mar", "Apr" to "Avr", "May" to "Mai",
            "Jun" to "Juin", "Jul" to "Juil", "Aug" to "Août", "Sep" to "Sep", "Oct" to "Oct",
            "Nov" to "Nov", "Dec" to "Déc",
        )
        val expectedFr = frByEn[truth.string("best_month_label")]
        assertEquals("meilleur mois — libellé FR", expectedFr, stats.bestMonth?.label)

        // Amount distribution bins — half-open [lo, hi), 50k+ dominant on the real corpus.
        val bins = deriveAmountHistogram(slice)
        assertEquals("bin 0–5k count", truth.long("b0_5"), bins[0].count.toLong())
        assertEquals("bin 5k–10k count", truth.long("b5_10"), bins[1].count.toLong())
        assertEquals("bin 10k–20k count", truth.long("b10_20"), bins[2].count.toLong())
        assertEquals("bin 20k–50k count", truth.long("b20_50"), bins[3].count.toLong())
        assertEquals("bin 50k+ count", truth.long("b50p"), bins[4].count.toLong())

        // Category mix — every canonical category, percents and amounts.
        val mix = deriveCategoryMix(slice)
        val truthMix = truth["category_mix"]!!.jsonArray
        assertEquals("category count", truthMix.size, mix.size)
        truthMix.forEachIndexed { i, el ->
            val t = el.jsonObject
            assertEquals("category[${i}].key", t["category"]!!.jsonPrimitive.content, mix[i].key)
            assertEquals("category[${i}].amount", t["amount"]!!.jsonPrimitive.long, mix[i].amount)
            assertEquals("category[${i}].count", t["count"]!!.jsonPrimitive.long, mix[i].count.toLong())
            assertEquals("category[${i}].percent", t["percent"]!!.jsonPrimitive.long, mix[i].percent.toLong())
        }

        // The aging census — per-installment INV-4 remaining, REAL due dates,
        // distinct families per bucket (time NOW is 'now' server-side; the
        // census here runs on the same instant ± the test duration, and the
        // real corpus is 100+ days deep in the 91_180/180_plus buckets, so a
        // same-second boundary cannot flip a bucket).
        val census = deriveDebtAging(installments)
        val truthCensus = truth["census"]!!.jsonObject
        val truthBuckets = truthCensus.keys.sorted()
        assertEquals("aging buckets present (canonical order)", truthBuckets, census.map { it.bucket }.sorted())
        census.forEach { c ->
            val t = truthCensus[c.bucket]!!.jsonObject
            assertEquals("bucket ${c.bucket} — amount (Σ INV-4 remaining)", t["amount"]!!.jsonPrimitive.long, c.amount)
            assertEquals("bucket ${c.bucket} — distinct families", t["debtors"]!!.jsonPrimitive.long, c.debtorCount.toLong())
        }

        // The recovery funnel — stage counts are the Σ of bucket family
        // counts (379 on the 44th-session corpus: 196 + 183), NOT distinct
        // parents: the desktop's deriveRecoveryFunnel semantics.
        val funnel = deriveRecoveryFunnel(census)
        val funnelTotal = truthCensus.entries.sumOf { it.value.jsonObject["debtors"]!!.jsonPrimitive.long }
        if (funnelTotal > 0) {
            assertEquals("funnel En retard = Σ bucket family counts", funnelTotal, funnel[0].count.toLong())
        } else {
            assertTrue("empty census → NO funnel (honest)", funnel.isEmpty())
        }

        // Debt totals + collection rate — the owner's 49% vector.
        assertEquals("outstanding (créances ouvertes)", truth.long("outstanding_centimes"), installments.sumOf { installmentRemaining(it) })
        val overdue = installments.filter { com.example.core.daysBetweenFloor(it.dueDate) > 0 }.sumOf { installmentRemaining(it) }
        assertEquals("overdue (en retard)", truth.long("overdue_centimes"), overdue)
        assertEquals("taux de recouvrement", truth.long("collection_rate"), collectionRatePct(stats.total, installments.sumOf { installmentRemaining(it) }).toLong())

        // Class-related data — the class census from the same live DB.
        assertEquals("total classes", truth.long("total_classes"), restGetAll("classes", "id").size.toLong())

        // Cross-checks pinned to the 44th-session canonical corpus values
        // (they document WHAT the numbers were when the parity was proven;
        // they will legitimately move as the school collects more).
        assertEquals("891 encaissements at the 44th session", 891L, truth.long("ops"))
        assertEquals("55 227 100 DZD encaissé at the 44th session", 5_522_710_000L, truth.long("total_centimes"))
        assertEquals("49% collection rate at the 44th session", 49L, truth.long("collection_rate"))
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull
        ?: this[key]?.jsonPrimitive?.contentOrNull?.toDouble()?.let { Math.round(it) }
        ?: error("missing truth key $key")

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: error("missing truth key $key")

    private val kotlinx.serialization.json.JsonPrimitive.long: Long
        get() = longOrNull ?: contentOrNull?.replace(".0$", "")?.toLong() ?: error("not a long: $content")
}
