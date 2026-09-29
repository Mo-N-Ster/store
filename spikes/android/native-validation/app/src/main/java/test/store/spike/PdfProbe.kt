package test.store.spike

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// Disposable parser/format evidence only; no database or privileged import call.
object PdfProbe {
    private const val PREFIX = "STORE_DATA_V1:"
    private val fields = listOf("name", "hashtag", "category", "description", "price", "stockQuantity", "minStockThreshold")
    private fun canonical(value: JSONObject): String {
        require(value.opt("kind") == "stocks" && value.opt("version") == 1)
        val products = value.getJSONArray("products")
        require(products.length() <= 10000)
        val rows = (0 until products.length()).map { i ->
            val row = products.getJSONObject(i)
            val pieces = fields.map { key ->
                val v = row.get(key)
                val encoded = if (key in fields.take(4)) {
                    require(v is String && v.length <= 64000)
                    if (key == "name" || key == "category") require(v.trim().isNotEmpty())
                    JSONObject.quote(v)
                } else {
                    require(v is Number)
                    val n = v.toDouble()
                    require(n.isFinite() && n >= 0)
                    if (key == "price") require(kotlin.math.abs(n * 100 - kotlin.math.round(n * 100)) < 1e-8)
                    else require(n <= 9007199254740991.0 && n == kotlin.math.floor(n))
                    java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
                }
                JSONObject.quote(key) + ":" + encoded
            }
            "{" + pieces.joinToString(",") + "}"
        }
        return ("{\"kind\":\"stocks\",\"version\":1,\"products\":[" + rows.joinToString(",") + "]}")
            .also { require(it.toByteArray(Charsets.UTF_8).size <= 2000000) }
    }
    private fun envelope(json: String) = PREFIX + Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun extract(file: File): String {
        require(file.length() in 1..25000000)
        PDDocument.load(file).use { pdf ->
            require(!pdf.isEncrypted && pdf.numberOfPages > 0)
            val subject = pdf.documentInformation.subject ?: error("MISSING_PAYLOAD")
            require(subject.startsWith(PREFIX))
            val encoded = subject.removePrefix(PREFIX)
            require(encoded.length <= 2666668 && encoded.matches(Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")))
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size <= 2000000 && Base64.encodeToString(bytes, Base64.NO_WRAP) == encoded)
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val json = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            return canonical(JSONObject(json))
        }
    }
    private fun inject(base: File, dest: File, subject: String?) {
        PDDocument.load(base).use { pdf -> pdf.documentInformation.subject = subject; pdf.save(dest) }
    }
    fun run(context: Context): JSONObject {
        PDFBoxResourceLoader.init(context)
        val dir = File(context.filesDir, "pdf").apply { mkdirs() }
        val payload = JSONObject("""{"kind":"stocks","version":1,"products":[
          {"name":"Café torréfié","hashtag":"#café","category":"Épices","description":"Récolte locale","price":1250.25,"stockQuantity":21,"minStockThreshold":3},
          {"name":"Piment séché","hashtag":"#piment","category":"Aliments","description":"Goût relevé","price":0.5,"stockQuantity":0,"minStockThreshold":1},
          {"name":"Thé vert","hashtag":"#thé","category":"Boissons","description":"Données synthétiques","price":999.99,"stockQuantity":7,"minStockThreshold":2}]}""")
        val expected = canonical(payload)
        val base = File(dir, "android-visible.pdf")
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = Paint().apply { color = Color.BLACK; textSize = 18f; isAntiAlias = true }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("STORE — preuve Android native", 36f, 50f, paint)
            paint.textSize = 10f
            page.canvas.drawText("PdfDocument / données synthétiques / aucune donnée utilisateur", 36f, 75f, paint)
            val products = payload.getJSONArray("products")
            for (i in 0 until products.length()) {
                val row = products.getJSONObject(i); val y = 130f + 38f * i
                paint.textSize = 13f
                page.canvas.drawText(row.getString("name"), 36f, y, paint)
                page.canvas.drawText(row.getDouble("price").toString() + " FCFA", 300f, y, paint)
                page.canvas.drawText("Stock " + row.getInt("stockQuantity"), 465f, y, paint)
            }
            doc.finishPage(page)
            FileOutputStream(base).use { doc.writeTo(it); it.fd.sync() }
        } finally { doc.close() }
        val tests = JSONArray()
        fun pass(name: String, body: () -> Unit) { body(); tests.put(name) }
        fun reject(name: String, subject: String?) = pass(name) {
            val file = File(dir, "negative.pdf"); inject(base, file, subject)
            var rejected = false
            try { extract(file) } catch (_: Exception) { rejected = true }
            check(rejected) { "ACCEPTED_$name" }
        }
        val final = File(dir, "android-structured.pdf")
        inject(base, final, envelope(expected))
        pass("native_persisted_roundtrip_3_records_unicode_money") { check(extract(final) == expected) }
        pass("canonical_key_order") {
            val reordered = JSONObject().put("products", payload.getJSONArray("products")).put("version", 1).put("kind", "stocks")
            check(canonical(reordered) == expected)
        }
        reject("malformed_json", envelope("{broken"))
        reject("missing_payload", null)
        reject("unsupported_prefix", envelope(expected).replace("V1:", "V2:"))
        reject("unsupported_version", envelope(expected.replace("\"version\":1", "\"version\":2")))
        reject("corrupt_base64", PREFIX + "%%%")
        reject("invalid_negative_price", envelope(expected.replace("1250.25", "-1")))
        reject("invalid_fractional_quantity", envelope(expected.replace("\"stockQuantity\":21", "\"stockQuantity\":1.5")))
        reject("invalid_subcent_price", envelope(expected.replace("1250.25", "1.001")))
        pass("truncated_pdf") {
            val truncated = File(dir, "truncated.pdf"); truncated.writeBytes(final.readBytes().take(32).toByteArray())
            var rejected = false; try { extract(truncated) } catch (_: Exception) { rejected = true }; check(rejected)
        }
        pass("synthetic_2_0_1_compatibility") {
            val fixture = File(dir, "source-compatible.pdf")
            context.assets.open("synthetic-2.0.1-envelope.pdf").use { input -> fixture.outputStream().use { input.copyTo(it) } }
            check(extract(fixture) == expected)
        }
        pass("valid_tampering_not_authenticated_V1") {
            val file = File(dir, "valid-alteration.pdf")
            val changed = expected.replace("1250.25", "100")
            inject(base, file, envelope(changed)); check(extract(file) == canonical(JSONObject(changed)))
        }
        return JSONObject().put("tests", tests).put("count", tests.length())
            .put("pdf", final.name).put("renderer", "android.graphics.pdf.PdfDocument")
            .put("codec", "pdfbox-android 2.0.27.0").put("authenticity", "untrusted V1; validation, not authentication")
    }
}
