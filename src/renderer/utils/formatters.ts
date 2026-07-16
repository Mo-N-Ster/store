export const formatMoney=(value:number)=>new Intl.NumberFormat(undefined,{style:'currency',currency:'EUR'}).format(value||0);
export const todayIso=()=>new Date().toISOString().slice(0,10);
