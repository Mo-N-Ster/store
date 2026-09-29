"""Visual QA of PDFs generated ON Android; host PDFium is not the codec."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parent
sys.path.insert(0, str(root.parent / 'sp02-pdf-codec/.deps'))
import pypdfium2 as pdfium

for name in ('android-visible', 'android-structured'):
    with pdfium.PdfDocument(root / 'out' / f'{name}.pdf') as pdf:
        assert len(pdf) == 1
        page = pdf[0]
        bitmap = page.render(scale=1.4, rev_byteorder=True)
        bitmap.to_pil().save(root / 'out' / f'{name}.png')
        print(f'{name}: {bitmap.width}x{bitmap.height}')
        bitmap.close()
        page.close()
