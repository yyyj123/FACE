import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

const requiredFiles = [
  'PRODUCT.md',
  'DESIGN.md',
  'docs/database/face_salon_mysql8.sql',
  'docs/architecture/api-contract.md',
  'docs/architecture/migration-plan.md'
]

for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(root, file))) throw new Error(`missing ${file}`)
}

const sql = read('docs/database/face_salon_mysql8.sql')
const requiredTables = [
  'shop', 'member', 'staff', 'account', 'service_category', 'service_item', 'care_package',
  'staff_service', 'staff_schedule', 'appointment', 'appointment_item',
  'service_record', 'sales_order', 'sales_order_item', 'product',
  'inventory_movement', 'review', 'care_record', 'audit_log'
]

for (const table of requiredTables) {
  if (!sql.includes(`CREATE TABLE \`${table}\``)) throw new Error(`database missing table ${table}`)
}

if (!sql.includes('start_at < :newEndAt AND end_at > :newStartAt')) {
  throw new Error('database docs missing overlap conflict rule')
}

const visibleRoots = ['front/src', 'front/public/index.html', 'admin/src', 'admin/public/index.html']
const extensions = new Set(['.vue', '.js', '.html'])
const legacyPattern = /(新能源汽车|维修服务|维修技师|车主|故障排查|配件出库|车辆记录)/
const duplicatePattern = /(套餐套餐|美容美容|护理护理|项目项目|服务服务)/

function collect(target, output = []) {
  const absolute = path.join(root, target)
  const stat = fs.statSync(absolute)
  if (stat.isFile()) return extensions.has(path.extname(absolute)) ? [absolute] : []
  for (const entry of fs.readdirSync(absolute, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'dist') continue
    const child = path.join(absolute, entry.name)
    if (entry.isDirectory()) collect(path.relative(root, child), output)
    else if (extensions.has(path.extname(child))) output.push(child)
  }
  return output
}

const sourceFiles = visibleRoots.flatMap(target => collect(target))
for (const file of sourceFiles) {
  const source = fs.readFileSync(file, 'utf8')
  const relative = path.relative(root, file)
  if (legacyPattern.test(source)) throw new Error(`${relative} retains legacy customer-facing language`)
  if (duplicatePattern.test(source)) throw new Error(`${relative} contains duplicated migrated language`)
}

if (!read('front/public/index.html').includes('FACE 美容护理预约')) throw new Error('front title not migrated')
if (!read('admin/public/index.html').includes('FACE 美容院店铺管理系统')) throw new Error('admin title not migrated')
if (!read('admin/src/utils/menu.js').includes('项目预约管理')) throw new Error('admin menu not migrated')

console.log(`FACE domain verification passed: ${requiredTables.length} tables, ${sourceFiles.length} source files checked`)
