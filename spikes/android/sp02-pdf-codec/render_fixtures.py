"""Host visual QA only; isolated PDFium is NOT the chosen Android codec."""
from pathlib import Path
import sys

base = Path(__file__).resolve().parent
sys.path.insert(0, str(base / '.deps'))
import pypdfium2 as pdfium


for name in ('synthetic-catalog', 'synthetic-2.0.1-envelope'):
    with pdfium.PdfDocument(base / 'out' / f'{name}.pdf') as pdf:
        assert len(pdf) == 1
        page = pdf[0]
        bitmap = page.render(scale=1.4, rev_byteorder=True)
        bitmap.to_pil().save(base / 'out' / f'{name}.png')
        print(f'{name}: rendered {bitmap.width}x{bitmap.height}, HOST PDFium')
        bitmap.close()
        page.close()
