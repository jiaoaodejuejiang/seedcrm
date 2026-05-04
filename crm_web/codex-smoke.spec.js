import { test, expect } from '@playwright/test'

const base = 'http://127.0.0.1:8003'

test('service form design and confirmation pages smoke', async ({ page }) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(`console: ${message.text()}`)
    }
  })

  const loginResponse = await page.request.post(`${base}/api/auth/login`, {
    data: { username: 'admin', password: '123456', loginMode: 'hq' }
  })
  expect(loginResponse.ok()).toBeTruthy()
  const loginJson = await loginResponse.json()
  const token = loginJson?.data?.token
  expect(token).toBeTruthy()
  await page.addInitScript((value) => window.localStorage.setItem('seedcrm.auth-token', value), token)

  for (const route of [
    { path: '/store-service/service-design', text: '服务单设计' },
    { path: '/store-service/plan-orders/1', text: '服务确认单' },
    { path: '/settings/base/config-audit', text: '配置' }
  ]) {
    await page.goto(`${base}${route.path}`, { waitUntil: 'networkidle' })
    await expect(page.locator('body')).toContainText(route.text)
  }

  await page.goto(`${base}/store-service/service-design`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '新增模板' }).click()
  await expect(page.locator('.el-dialog')).toContainText('第三方设计器导入')
  await expect(page.locator('.el-dialog')).toContainText('纸质签名')
  await expect(page.locator('.el-dialog')).toContainText('电子签名组件会拦截')

  expect(errors).toEqual([])
})
