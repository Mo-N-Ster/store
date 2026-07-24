import { describe, expect, it } from 'vitest';
import { PDFDocument } from 'pdf-lib';
import { embedStoreData, readStoreData } from '../../../backend/src/services/stockPdfService';

describe('stock PDF structured payload', () => {
  it('round-trips STORE stock data through a valid PDF', async () => {
    const document = await PDFDocument.create();
    document.addPage([500, 300]);
    const source = await document.save();
    const payload = {
      kind: 'stocks',
      version: 1,
      products: [
        {
          name: 'Poivre noir',
          category: 'Épices',
          hashtag: '#poivre',
          description: 'Grains',
          price: 8.5,
          stockQuantity: 12,
          minStockThreshold: 3,
        },
      ],
    };

    const exported = await embedStoreData(source, payload);
    await expect(readStoreData(exported)).resolves.toEqual(payload);
  });

  it('rejects a regular PDF without STORE metadata', async () => {
    const document = await PDFDocument.create();
    document.addPage();
    await expect(readStoreData(await document.save())).rejects.toThrow('INVALID_STORE_PDF');
  });
});
