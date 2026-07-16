import Database from 'better-sqlite3';
import bcrypt from 'bcryptjs';
import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { schema } from './schema.js';

type CartLine = { productId: number; quantity: number };
type UserInput = { username: string; email: string; password?: string; role?: 'admin'|'employee'; firstName: string; lastName: string; phone?: string; hireDate?: string; active?: boolean };
type ProductInput = { name: string; hashtag?: string; category: string; description?: string; price: number; stockQuantity: number; minStockThreshold: number };

let db: Database.Database;
const now = () => new Date().toISOString();
const initials = (first: string, last: string) => `${first[0] ?? ''}${last[0] ?? ''}`.toUpperCase();

export function initDatabase() {
  const dir = app.getPath('userData');
  fs.mkdirSync(dir, { recursive: true });
  db = new Database(path.join(dir, 'store.db'));
  db.pragma('journal_mode = WAL');
  db.exec(schema);
  dailyBackup();
}

export const api = {
  needsSetup: () => (db.prepare('SELECT COUNT(*) count FROM users WHERE role = ?').get('admin') as {count:number}).count === 0,
  setupAdmin: (input: UserInput) => {
    if (!api.needsSetup()) throw new Error('SETUP_ALREADY_COMPLETED');
    validateUser(input, true);
    const hash = bcrypt.hashSync(input.password!, 10);
    const result = db.prepare(`INSERT INTO users(username,email,password_hash,role,first_name,last_name,initials,phone,hire_date)
      VALUES(?,?,?,?,?,?,?,?,?)`).run(input.username.trim(), input.email.trim().toLowerCase(), hash, 'admin', input.firstName.trim(), input.lastName.trim(), initials(input.firstName,input.lastName), input.phone ?? '', input.hireDate ?? null);
    return publicUser(Number(result.lastInsertRowid));
  },
  login: ({ identifier, password, role }: {identifier:string; password:string; role:string}) => {
    const user = db.prepare('SELECT * FROM users WHERE (username = ? OR email = ?) AND role = ? AND active = 1').get(identifier, identifier.toLowerCase(), role) as any;
    if (!user || !bcrypt.compareSync(password, user.password_hash)) throw new Error('INVALID_CREDENTIALS');
    return stripPassword(user);
  },
  verifyAdmin: ({ id, password }: {id:number; password:string}) => {
    const user = db.prepare("SELECT * FROM users WHERE id=? AND role='admin' AND active=1").get(id) as any;
    return Boolean(user && bcrypt.compareSync(password, user.password_hash));
  },
  users: () => db.prepare('SELECT id,username,email,role,first_name firstName,last_name lastName,initials,phone,hire_date hireDate,active FROM users ORDER BY active DESC,last_name').all(),
  saveUser: (input: UserInput & {id?:number}) => {
    validateUser(input, !input.id);
    const role = input.role ?? 'employee';
    if (input.id) {
      const fields = [input.username.trim(),input.email.trim().toLowerCase(),role,input.firstName.trim(),input.lastName.trim(),initials(input.firstName,input.lastName),input.phone??'',input.hireDate??null,input.active===false?0:1,input.id];
      db.prepare('UPDATE users SET username=?,email=?,role=?,first_name=?,last_name=?,initials=?,phone=?,hire_date=?,active=? WHERE id=?').run(...fields);
      if (input.password) db.prepare('UPDATE users SET password_hash=? WHERE id=?').run(bcrypt.hashSync(input.password,10),input.id);
      enforceSingleAdmin(input.id, role);
      return publicUser(input.id);
    }
    const password = input.password || temporaryPassword();
    const result = db.prepare(`INSERT INTO users(username,email,password_hash,role,first_name,last_name,initials,phone,hire_date,active) VALUES(?,?,?,?,?,?,?,?,?,?)`)
      .run(input.username.trim(),input.email.trim().toLowerCase(),bcrypt.hashSync(password,10),role,input.firstName.trim(),input.lastName.trim(),initials(input.firstName,input.lastName),input.phone??'',input.hireDate??null,1);
    enforceSingleAdmin(Number(result.lastInsertRowid), role);
    return { user: publicUser(Number(result.lastInsertRowid)), temporaryPassword: password };
  },
  resetPassword: (id:number) => { const password=temporaryPassword(); db.prepare('UPDATE users SET password_hash=? WHERE id=?').run(bcrypt.hashSync(password,10),id); return password; },
  products: ({search='',category=''}={}) => db.prepare(`SELECT id,name,hashtag,category,description,price,stock_quantity stockQuantity,min_stock_threshold minStockThreshold
    FROM products WHERE deleted_at IS NULL AND (?='' OR name LIKE ? OR category LIKE ? OR hashtag LIKE ?) AND (?='' OR category=?) ORDER BY name`)
    .all(search,`%${search}%`,`%${search}%`,`%${search}%`,category,category),
  saveProduct: (input: ProductInput & {id?:number}) => {
    if (!input.name.trim() || !input.category.trim() || input.price < 0 || input.stockQuantity < 0 || input.minStockThreshold < 0) throw new Error('INVALID_PRODUCT');
    if (input.id) {
      const old = db.prepare('SELECT stock_quantity stock FROM products WHERE id=?').get(input.id) as {stock:number};
      db.prepare(`UPDATE products SET name=?,hashtag=?,category=?,description=?,price=?,stock_quantity=?,min_stock_threshold=?,updated_at=? WHERE id=?`)
        .run(input.name,input.hashtag??'',input.category,input.description??'',input.price,input.stockQuantity,input.minStockThreshold,now(),input.id);
      const delta=input.stockQuantity-old.stock; if(delta) db.prepare('INSERT INTO stock_movements(product_id,quantity,reason) VALUES(?,?,?)').run(input.id,delta,'adjustment');
      checkStock(input.id); return input.id;
    }
    const r=db.prepare(`INSERT INTO products(name,hashtag,category,description,price,stock_quantity,min_stock_threshold) VALUES(?,?,?,?,?,?,?)`)
      .run(input.name,input.hashtag??'',input.category,input.description??'',input.price,input.stockQuantity,input.minStockThreshold);
    const id=Number(r.lastInsertRowid); if(input.stockQuantity) db.prepare('INSERT INTO stock_movements(product_id,quantity,reason) VALUES(?,?,?)').run(id,input.stockQuantity,'initial'); checkStock(id); return id;
  },
  deleteProduct: ({id,userId}:{id:number;userId:number}) => { db.prepare('UPDATE products SET deleted_at=?,stock_quantity=0 WHERE id=?').run(now(),id); audit(userId,'delete','product',String(id)); },
  createInvoice: ({employeeId,lines,discount=0}:{employeeId:number;lines:CartLine[];discount?:number}) => db.transaction(() => {
    if (!lines.length || discount < 0) throw new Error('INVALID_INVOICE');
    const products=lines.map(line => ({line, product:db.prepare('SELECT * FROM products WHERE id=? AND deleted_at IS NULL').get(line.productId) as any}));
    for(const {line,product} of products) if(!product || line.quantity<=0 || product.stock_quantity<line.quantity) throw new Error('INSUFFICIENT_STOCK');
    const subtotal=products.reduce((sum,x)=>sum+x.line.quantity*x.product.price,0); const applied=Math.min(discount,subtotal); const total=subtotal-applied;
    const base=invoiceId(); let id=base; let n=1; while(db.prepare('SELECT 1 FROM invoices WHERE id=?').get(id)) id=`${base}-${n++}`;
    db.prepare('INSERT INTO invoices(id,employee_id,invoice_date,subtotal,total_amount,discount) VALUES(?,?,?,?,?,?)').run(id,employeeId,now(),subtotal,total,applied);
    for(const {line,product} of products){ db.prepare('INSERT INTO invoice_lines(invoice_id,product_id,product_name,category,quantity,unit_price,total_line) VALUES(?,?,?,?,?,?,?)').run(id,product.id,product.name,product.category,line.quantity,product.price,line.quantity*product.price); db.prepare('UPDATE products SET stock_quantity=stock_quantity-?,updated_at=? WHERE id=?').run(line.quantity,now(),product.id); db.prepare('INSERT INTO stock_movements(product_id,quantity,reason,reference_id) VALUES(?,?,?,?)').run(product.id,-line.quantity,'sale',id); checkStock(product.id); }
    return invoiceDetail(id);
  })(),
  invoices: ({from='',to='',search=''}={}) => db.prepare(`SELECT i.id,i.invoice_date invoiceDate,i.total_amount totalAmount,i.discount,u.first_name||' '||u.last_name seller FROM invoices i JOIN users u ON u.id=i.employee_id WHERE (?='' OR date(i.invoice_date)>=date(?)) AND (?='' OR date(i.invoice_date)<=date(?)) AND (?='' OR i.id LIKE ? OR u.username LIKE ?) ORDER BY i.invoice_date DESC`).all(from,from,to,to,search,`%${search}%`,`%${search}%`),
  invoice: (id:string) => invoiceDetail(id),
  deleteInvoice: ({id,userId}:{id:string;userId:number}) => { db.transaction(()=>{ const lines=db.prepare('SELECT product_id productId,quantity FROM invoice_lines WHERE invoice_id=?').all(id) as any[]; for(const l of lines) if(l.productId) db.prepare('UPDATE products SET stock_quantity=stock_quantity+? WHERE id=?').run(l.quantity,l.productId); db.prepare('DELETE FROM invoices WHERE id=?').run(id); audit(userId,'delete','invoice',id); })(); },
  attendance: ({employeeId,present}:{employeeId:number;present:boolean}) => { const open=db.prepare('SELECT id FROM attendances WHERE employee_id=? AND end_time IS NULL ORDER BY id DESC LIMIT 1').get(employeeId) as any; if(present){if(open) throw new Error('SERVICE_ALREADY_OPEN'); db.prepare('INSERT INTO attendances(employee_id,start_time) VALUES(?,?)').run(employeeId,now());} else {if(!open) throw new Error('NO_OPEN_SERVICE'); db.prepare('UPDATE attendances SET end_time=? WHERE id=?').run(now(),open.id);} },
  messages: ({userId,role}:{userId:number;role:string}) => db.prepare(`SELECT m.*,u.first_name||' '||u.last_name sender FROM messages m LEFT JOIN users u ON u.id=m.sender_id WHERE (?='admin' AND m.recipient_type='admin') OR (?='employee' AND (m.recipient_type='all' OR m.recipient_id=?)) ORDER BY m.created_at DESC`).all(role,role,userId),
  sendMessage: ({senderId,recipientType,recipientId,subject,content}:{senderId:number;recipientType:string;recipientId?:number;subject:string;content:string}) => {if(!subject.trim()||!content.trim()) throw new Error('INVALID_MESSAGE'); return db.prepare('INSERT INTO messages(sender_id,recipient_type,recipient_id,subject,content) VALUES(?,?,?,?,?)').run(senderId,recipientType,recipientId??null,subject.trim(),content.trim()).lastInsertRowid;},
  markMessage: ({id,isRead}:{id:number;isRead:boolean}) => db.prepare('UPDATE messages SET is_read=? WHERE id=?').run(isRead?1:0,id),
  deleteMessage: (id:number) => db.prepare('DELETE FROM messages WHERE id=?').run(id),
  notifications: () => db.prepare('SELECT * FROM notifications ORDER BY created_at DESC').all(),
  dashboard: () => ({
    products:(db.prepare('SELECT COUNT(*) n FROM products WHERE deleted_at IS NULL').get() as any).n,
    lowStock:(db.prepare('SELECT COUNT(*) n FROM products WHERE deleted_at IS NULL AND stock_quantity<=min_stock_threshold').get() as any).n,
    salesToday:(db.prepare("SELECT COUNT(*) n FROM invoices WHERE date(invoice_date)=date('now','localtime')").get() as any).n,
    revenueToday:(db.prepare("SELECT COALESCE(SUM(total_amount),0) n FROM invoices WHERE date(invoice_date)=date('now','localtime')").get() as any).n,
    revenueMonth:(db.prepare("SELECT COALESCE(SUM(total_amount),0) n FROM invoices WHERE strftime('%Y-%m',invoice_date)=strftime('%Y-%m','now','localtime')").get() as any).n,
    salesChart:db.prepare("SELECT date(invoice_date) label,SUM(total_amount) value FROM invoices WHERE invoice_date>=datetime('now','-30 day') GROUP BY date(invoice_date) ORDER BY label").all(),
    attendanceChart:db.prepare("SELECT u.first_name||' '||u.last_name label,ROUND(SUM((julianday(a.end_time)-julianday(a.start_time))*24),2) value FROM attendances a JOIN users u ON u.id=a.employee_id WHERE a.end_time IS NOT NULL GROUP BY a.employee_id ORDER BY value DESC").all()
  }),
  settings: () => Object.fromEntries((db.prepare('SELECT key,value FROM settings').all() as any[]).map(x=>[x.key,x.value])),
  saveSettings: (values:Record<string,string>) => db.transaction(()=>{const stmt=db.prepare('INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value'); for(const [k,v] of Object.entries(values)) stmt.run(k,v);})(),
  backup: () => createBackup(),
  reset: ({adminId,password}:{adminId:number;password:string}) => { if(!api.verifyAdmin({id:adminId,password})) throw new Error('INVALID_CREDENTIALS'); createBackup(); db.transaction(()=>{for(const table of ['invoice_lines','invoices','attendances','messages','notifications','stock_movements','products','audit_logs','settings','users']) db.prepare(`DELETE FROM ${table}`).run();})(); return true; }
};

function validateUser(x:UserInput, passwordRequired:boolean){if(!x.username?.trim()||!x.email?.includes('@')||!x.firstName?.trim()||!x.lastName?.trim()||(passwordRequired&&(!x.password||x.password.length<8))) throw new Error('INVALID_USER');}
function stripPassword(u:any){const safe={...u};delete safe.password_hash;return safe;}
function publicUser(id:number){return stripPassword(db.prepare('SELECT * FROM users WHERE id=?').get(id));}
function enforceSingleAdmin(id:number,role:string){if(role==='admin') db.prepare("UPDATE users SET role='employee' WHERE role='admin' AND id<>?").run(id);}
function temporaryPassword(){return `Store-${Math.random().toString(36).slice(2,8)}!`;}
function invoiceId(){const d=new Date(); const p=(n:number)=>String(n).padStart(2,'0'); return `FACT-${d.getFullYear()}${p(d.getMonth()+1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;}
function invoiceDetail(id:string){return {invoice:db.prepare(`SELECT i.*,u.first_name||' '||u.last_name seller,u.initials FROM invoices i JOIN users u ON u.id=i.employee_id WHERE i.id=?`).get(id),lines:db.prepare('SELECT * FROM invoice_lines WHERE invoice_id=?').all(id)};}
function checkStock(id:number){const p=db.prepare('SELECT * FROM products WHERE id=?').get(id) as any;if(!p)return; if(p.stock_quantity<=p.min_stock_threshold){const exists=db.prepare("SELECT 1 FROM notifications WHERE product_id=? AND type='stock_alert' AND resolved_at IS NULL").get(id);if(!exists)db.prepare("INSERT INTO notifications(type,product_id,message) VALUES('stock_alert',?,?)").run(id,`${p.name}: stock ${p.stock_quantity}, seuil ${p.min_stock_threshold}`);}else db.prepare("UPDATE notifications SET resolved_at=? WHERE product_id=? AND resolved_at IS NULL").run(now(),id);}
function audit(userId:number,action:string,entity:string,entityId:string){db.prepare('INSERT INTO audit_logs(user_id,action,entity,entity_id) VALUES(?,?,?,?)').run(userId,action,entity,entityId);}
function backupDir(){const d=path.join(app.getPath('userData'),'backups');fs.mkdirSync(d,{recursive:true});return d;}
function createBackup(){const name=`store-${new Date().toISOString().replace(/[:.]/g,'-')}.db`; const target=path.join(backupDir(),name); db.backup(target); return target;}
function dailyBackup(){const dir=backupDir();const files=fs.readdirSync(dir).filter(f=>f.endsWith('.db')).sort().reverse();const today=new Date().toISOString().slice(0,10);if(!files.some(f=>f.includes(today)))createBackup();for(const f of files.slice(7))fs.rmSync(path.join(dir,f));}
