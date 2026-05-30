from fastapi import FastAPI, Request, Response
from weasyprint import HTML, __version__ as weasyprint_version

app = FastAPI()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "weasyprintVersion": weasyprint_version}


@app.post("/convert")
async def convert(request: Request) -> Response:
    body = await request.json()
    html = (body.get("html") or "").strip()
    if not html:
        return Response(status_code=400, content="HTML content cannot be empty")
    pdf = HTML(string=html).write_pdf(pdf_variant="pdf/ua-1")
    return Response(content=pdf, media_type="application/pdf")
