const { chromium, request } = require('playwright')
const fs = require('fs')
const path = require('path')

async function main() {
  const baseUrl = 'http://127.0.0.1:5173'
  const apiBase = 'http://127.0.0.1:8080'
  const stamp = String(Date.now()).slice(-8)
  const leadName = `Rerun-${stamp}`
  const phone = `1398${stamp.padStart(7, '0').slice(-7)}`
  const wechat = `rerun${stamp}`
  const outDir = process.cwd()
  const shots = []
  const warnings = []

  function log(type, message) {
    console.log(`[${type}] ${message}`)
  }

  async function shot(page, name) {
    const file = path.join(outDir, name)
    await page.screenshot({ path: file, fullPage: true })
    shots.push(file)
    log('shot', file)
  }

  async function waitForToast(page) {
    const toast = page.locator('.el-message').last()
    await toast.waitFor({ state: 'visible', timeout: 15000 }).catch(() => null)
  }

  async function waitForApi(page, keyword, action) {
    const responsePromise = page.waitForResponse(
      (resp) => resp.url().includes(keyword) && resp.request().method() === 'POST',
      { timeout: 20000 }
    ).catch(() => null)
    await action()
    await responsePromise
    await page.waitForLoadState('networkidle').catch(() => null)
    await page.waitForTimeout(800)
  }

  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1024 } })
  const page = await context.newPage()

  page.on('console', (msg) => {
    const type = msg.type()
    const text = msg.text()
    if (type === 'warning' || type === 'error') {
      warnings.push(`${type}: ${text}`)
    }
  })
  page.on('pageerror', (err) => warnings.push(`pageerror: ${err.message}`))

  const api = await request.newContext({ baseURL: apiBase })

  log('step', 'Open clue page')
  await page.goto(`${baseUrl}/clues`, { waitUntil: 'networkidle' })
  await shot(page, '01-clues.png')

  log('step', `Create clue ${leadName}`)
  await page.getByRole('button', { name: '新增线索', exact: true }).click()
  const clueDialog = page.locator('.el-dialog').filter({ hasText: '新增线索' }).last()
  await clueDialog.waitFor({ state: 'visible', timeout: 10000 })
  const clueInputs = clueDialog.locator('input')
  await clueInputs.nth(0).fill(leadName)
  await clueInputs.nth(1).fill(phone)
  await clueInputs.nth(2).fill(wechat)
  await waitForApi(page, '/clue/add', async () => {
    await clueDialog.getByRole('button', { name: '保存线索', exact: true }).click()
  })
  await waitForToast(page)

  const clueRow = page.locator('.el-table__row').filter({ hasText: leadName }).first()
  await clueRow.waitFor({ state: 'visible', timeout: 15000 })
  await shot(page, '02-clue-created.png')

  log('step', 'Assign clue')
  await waitForApi(page, '/clue/assign', async () => {
    await clueRow.getByRole('button', { name: '分给王顾问', exact: true }).click()
  })
  await waitForToast(page)

  log('step', 'Convert clue to order')
  await clueRow.getByRole('button', { name: '转订单', exact: true }).click()
  const orderDialog = page.locator('.el-dialog').filter({ hasText: '从线索创建订单' }).last()
  await orderDialog.waitFor({ state: 'visible', timeout: 10000 })
  await waitForApi(page, '/order/create', async () => {
    await orderDialog.getByRole('button', { name: '创建订单', exact: true }).click()
  })
  await waitForToast(page)
  await page.waitForTimeout(1000)
  await shot(page, '03-clue-converted.png')

  log('step', 'Fetch created order from API')
  let createdOrder
  for (let i = 0; i < 10; i += 1) {
    const resp = await api.get('/workbench/orders')
    const json = await resp.json()
    createdOrder = (json.data || []).find((item) => item.customerName === leadName || item.customerPhone === phone)
    if (createdOrder) {
      break
    }
    await page.waitForTimeout(1000)
  }
  if (!createdOrder) {
    throw new Error(`Unable to locate created order for ${leadName}`)
  }
  log('data', `orderId=${createdOrder.id}, orderNo=${createdOrder.orderNo}, customerId=${createdOrder.customerId || 'n/a'}`)

  log('step', 'Open order page')
  await page.goto(`${baseUrl}/orders`, { waitUntil: 'networkidle' })
  const orderRow = page.locator('.el-table__row').filter({ hasText: leadName }).first()
  await orderRow.waitFor({ state: 'visible', timeout: 15000 })
  await shot(page, '04-orders.png')

  log('step', 'Enter service page')
  await orderRow.getByRole('button', { name: '进入到店服务', exact: true }).click()
  await page.waitForURL(/\/plan-orders\/\d+/, { timeout: 20000 })
  await page.waitForLoadState('networkidle').catch(() => null)
  await page.waitForTimeout(1200)
  const planOrderId = Number(page.url().split('/').pop())
  log('data', `planOrderId=${planOrderId}`)
  await shot(page, '05-service-initial.png')

  async function clickRole(roleName, staffName) {
    const card = page.locator('.role-card').filter({ hasText: roleName }).first()
    await card.waitFor({ state: 'visible', timeout: 10000 })
    await waitForApi(page, '/planOrder/assignRole', async () => {
      await card.getByRole('button', { name: staffName, exact: true }).click()
    })
    await waitForToast(page)
    log('role', `${roleName} -> ${staffName}`)
  }

  log('step', 'Assign service roles')
  await clickRole('咨询师', '王顾问')
  await clickRole('医生', '张医生')
  await clickRole('助理', '陈助理')
  await shot(page, '06-service-roles.png')

  async function clickAction(label, apiPath) {
    const button = page.getByRole('button', { name: label, exact: true })
    await button.waitFor({ state: 'visible', timeout: 10000 })
    if (await button.isDisabled()) {
      throw new Error(`Button ${label} is disabled`)
    }
    await waitForApi(page, apiPath, async () => {
      await button.click()
    })
    await waitForToast(page)
    log('action', label)
  }

  log('step', 'Advance service flow')
  await clickAction('到店', '/planOrder/arrive')
  await clickAction('开始服务', '/planOrder/start')
  await clickAction('完成服务', '/planOrder/finish')
  await shot(page, '07-service-finished.png')

  log('step', 'Open customer detail')
  const customerButton = page.getByRole('button', { name: '查看客户详情', exact: true })
  if (await customerButton.isVisible()) {
    await customerButton.click()
  } else if (createdOrder.customerId) {
    await page.goto(`${baseUrl}/customers/${createdOrder.customerId}`, { waitUntil: 'networkidle' })
  } else {
    throw new Error('No customer detail entry available')
  }
  await page.waitForLoadState('networkidle').catch(() => null)
  await page.waitForTimeout(800)
  await shot(page, '08-customer-detail.png')

  log('step', 'Open distributor page')
  await page.goto(`${baseUrl}/distributors`, { waitUntil: 'networkidle' })
  await shot(page, '09-distributors.png')

  log('step', 'Open finance page')
  await page.goto(`${baseUrl}/finance`, { waitUntil: 'networkidle' })
  await shot(page, '10-finance.png')

  log('step', 'Verify final backend state')
  const orderResp = await api.get('/workbench/orders')
  const orderJson = await orderResp.json()
  const finalOrder = (orderJson.data || []).find((item) => item.id === createdOrder.id)
  const planResp = await api.get(`/workbench/plan-orders/${planOrderId}`)
  const planJson = await planResp.json()

  const summary = {
    leadName,
    phone,
    wechat,
    orderId: createdOrder.id,
    orderNo: createdOrder.orderNo,
    customerId: createdOrder.customerId,
    planOrderId,
    finalOrderStatus: finalOrder?.status,
    finalPlanOrderStatus: planJson?.data?.summary?.planOrderStatus,
    warnings,
    screenshots: shots
  }

  fs.writeFileSync(path.join(outDir, 'rerun-summary.json'), JSON.stringify(summary, null, 2))
  console.log('SUMMARY_START')
  console.log(JSON.stringify(summary, null, 2))
  console.log('SUMMARY_END')

  await api.dispose()
  await browser.close()
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
