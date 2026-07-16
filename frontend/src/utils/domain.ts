export type PricedLine={quantity:number;unitPrice:number};
export function calculateInvoice(lines:PricedLine[],discount=0){
  if(lines.some(x=>!Number.isInteger(x.quantity)||x.quantity<=0||x.unitPrice<0))throw new Error('INVALID_LINE');
  const subtotal=Math.round(lines.reduce((sum,x)=>sum+x.quantity*x.unitPrice,0)*100)/100;
  const appliedDiscount=Math.min(Math.max(discount,0),subtotal);
  return{subtotal,discount:appliedDiscount,total:Math.round((subtotal-appliedDiscount)*100)/100};
}
export function validateStock(requested:number,available:number){return Number.isInteger(requested)&&requested>0&&requested<=available;}
export function csvEscape(value:unknown){const text=String(value??'');return /[",\n]/.test(text)?`"${text.replace(/"/g,'""')}"`:text;}
