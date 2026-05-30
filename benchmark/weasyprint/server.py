from fastapi import FastAPI, Request, Response
from weasyprint import HTML, __version__ as weasyprint_version

# WeasyPrint's best accessibility mode. Note: WeasyPrint 63.1 cannot emit pdf/a-3a
# (archival + accessible in one file), which is what pdf-ua-api produces — it offers
# either pdf/a-3b (archival, untagged) or pdf/ua-1 (tagged, no ICC), not both. pdf/ua-1
# is the fair match for a PDF/UA API. custom_metadata carries the HTML meta tags into
# the PDF info/XMP, which PDF/UA needs for the document title and language.
PDF_VARIANT = "pdf/ua-1"

app = FastAPI()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "weasyprintVersion": weasyprint_version, "pdfVariant": PDF_VARIANT}


@app.post("/convert")
async def convert(request: Request) -> Response:
    body = await request.json()
    html = (body.get("html") or "").strip()
    if not html:
        return Response(status_code=400, content="HTML content cannot be empty")
    pdf = HTML(string=html).write_pdf(pdf_variant=PDF_VARIANT, custom_metadata=True)
    return Response(content=pdf, media_type="application/pdf")
