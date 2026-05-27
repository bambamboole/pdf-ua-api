package bambamboole.pdf.api.services

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
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.awt.Shape
import java.nio.charset.StandardCharsets
import java.util.WeakHashMap

object PdfBackgroundObjectDrawer : FSObjectDrawer {
    private val logger = LoggerFactory.getLogger(PdfBackgroundObjectDrawer::class.java)
    private val formCache = WeakHashMap<PDDocument, MutableMap<BackgroundPdfKey, PDFormXObject>>()

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

        val src = e.getAttribute("pdfsrc").takeIf { it.isNotBlank() } ?: return null
        val pageNumber = e.getAttribute("pdfpage").toIntOrNull()?.takeIf { it >= 1 } ?: 1

        try {
            val layerUtility = LayerUtility(outputDevice.writer)
            val form = importPageAsForm(src, pageNumber, outputDevice.writer, ctx, layerUtility) ?: return null
            val page = outputDevice.page

            layerUtility.wrapInSaveRestore(page)
            val contents = page.cosObject.getDictionaryObject(COSName.CONTENTS) as? COSArray ?: return null
            val firstContentStream = contents.getObject(0) as? COSStream ?: return null

            firstContentStream.createOutputStream().use { stream ->
                stream.write("q\n".toByteArray(StandardCharsets.US_ASCII))
                val name = page.resources.add(form)
                name.writePDF(stream)
                stream.write(' '.code)
                stream.write("Do\nQ\nq\n".toByteArray(StandardCharsets.US_ASCII))
            }
        } catch (e: Exception) {
            logger.warn("Failed to merge PDF page background: {}", e.message)
        }

        return null
    }

    private fun importPageAsForm(
        src: String,
        pageNumber: Int,
        target: PDDocument,
        ctx: RenderingContext,
        layerUtility: LayerUtility,
    ): PDFormXObject? {
        val key = BackgroundPdfKey(ctx.uac.resolveURI(src) ?: src, pageNumber)
        synchronized(formCache) {
            formCache[target]?.get(key)?.let { return it }
        }

        val bytes = ctx.uac.getBinaryResource(src, ExternalResourceType.PDF) ?: return null
        val form = Loader.loadPDF(bytes).use { source ->
            if (pageNumber > source.numberOfPages) {
                logger.warn("PDF background page {} does not exist in {}", pageNumber, key.src)
                return null
            }
            layerUtility.importPageAsForm(source, source.getPage(pageNumber - 1))
        }

        synchronized(formCache) {
            formCache.getOrPut(target) { mutableMapOf() }[key] = form
        }
        return form
    }

    private data class BackgroundPdfKey(val src: String, val page: Int)
}
