package bambamboole.pdfua.services

import com.openhtmltopdf.extend.FSObjectDrawer
import com.openhtmltopdf.extend.OutputDevice
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType
import com.openhtmltopdf.pdfboxout.PdfBoxOutputDevice
import com.openhtmltopdf.render.RenderingContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.PDXObject
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.awt.Shape
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.WeakHashMap

/**
 * Stamps a page background (image or PDF) onto every page: scaled to fill the page MediaBox,
 * wrapped as a PDF/UA artifact, and prepended so it sits behind content. CSS @page/background
 * techniques do not work for per-page full-bleed output in openhtmltopdf, so we stamp directly.
 */
object BackgroundObjectDrawer : FSObjectDrawer {
    const val OBJECT_TYPE = "x-page-background"

    private val logger = LoggerFactory.getLogger(BackgroundObjectDrawer::class.java)
    // Per-document XObject memo. Guarded for concurrent renders of different documents; a single document is rendered on one thread, so check-then-put need not be atomic.
    private val cache = WeakHashMap<PDDocument, MutableMap<String, PDXObject>>()

    override fun drawObject(
        e: Element,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        outputDevice: OutputDevice,
        ctx: RenderingContext,
        dotsPerPixel: Int,
    ): Map<Shape, String>? {
        if (outputDevice !is PdfBoxOutputDevice) return null
        val src = e.getAttribute("data-src").takeIf { it.isNotBlank() } ?: return null
        val kind = e.getAttribute("data-kind").lowercase()
        try {
            val xobject = resolveXObject(src, kind, ctx, outputDevice.writer) ?: return null
            stamp(outputDevice.page, xobject, outputDevice.writer)
        } catch (ex: Exception) {
            logger.warn("Failed to draw page background from {}: {}", src, ex.message)
        }
        return null
    }

    private fun resolveXObject(src: String, kind: String, ctx: RenderingContext, doc: PDDocument): PDXObject? {
        synchronized(cache) { cache[doc]?.get(src)?.let { return it } }

        val bytes = loadBytes(src, kind, ctx) ?: return null
        val isPdf = when (kind) {
            "pdf" -> true
            "image" -> false
            else -> bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        }
        val xobject: PDXObject = if (isPdf) {
            Loader.loadPDF(bytes).use { source ->
                if (source.numberOfPages < 1) return null
                LayerUtility(doc).importPageAsForm(source, source.getPage(0))
            }
        } else {
            PDImageXObject.createFromByteArray(doc, bytes, "PageBackground")
        }
        synchronized(cache) { cache.getOrPut(doc) { mutableMapOf() }[src] = xobject }
        return xobject
    }

    private fun loadBytes(src: String, kind: String, ctx: RenderingContext): ByteArray? {
        if (src.startsWith("data:")) {
            val comma = src.indexOf(',')
            if (comma < 0) return null
            if (!src.substring(0, comma).contains(";base64")) return null
            return Base64.getDecoder().decode(src.substring(comma + 1))
        }
        val type = if (kind == "pdf") ExternalResourceType.PDF else ExternalResourceType.IMAGE_RASTER
        return ctx.uac.getBinaryResource(src, type)
    }

    private fun stamp(page: PDPage, xobject: PDXObject, doc: PDDocument) {
        val box = page.mediaBox
        val ctm = ctm(xobject, box.lowerLeftX.toDouble(), box.lowerLeftY.toDouble(), box.width.toDouble(), box.height.toDouble())
            ?: return
        val name = when (xobject) {
            is PDFormXObject -> page.resources.add(xobject)
            is PDImageXObject -> page.resources.add(xobject)
            else -> return
        }

        LayerUtility(doc).wrapInSaveRestore(page)
        val contents = page.cosObject.getDictionaryObject(COSName.CONTENTS) as? COSArray ?: return
        val first = contents.getObject(0) as? COSStream ?: return
        first.createOutputStream().use { stream ->
            val ops = "/Artifact BMC\nq\n${ctm.a} 0 0 ${ctm.d} ${ctm.e} ${ctm.f} cm\n/${name.name} Do\nQ\nEMC\nq\n"
            stream.write(ops.toByteArray(StandardCharsets.US_ASCII))
        }
    }

    private data class Ctm(val a: Double, val d: Double, val e: Double, val f: Double)

    private fun ctm(xobject: PDXObject, mbX: Double, mbY: Double, mbW: Double, mbH: Double): Ctm? =
        when (xobject) {
            is PDImageXObject -> Ctm(mbW, mbH, mbX, mbY)
            is PDFormXObject -> {
                val bb = xobject.bBox
                val sx = mbW / bb.width
                val sy = mbH / bb.height
                Ctm(sx, sy, mbX - bb.lowerLeftX * sx, mbY - bb.lowerLeftY * sy)
            }
            else -> null
        }
}
