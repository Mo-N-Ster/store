export interface ProductInput {
  name: string;
  hashtag?: string;
  category: string;
  description?: string;
  price: number;
  stockQuantity: number;
  minStockThreshold: number;
}
