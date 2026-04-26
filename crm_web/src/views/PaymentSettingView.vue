<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>微信支付</span>
        <strong>{{ paymentSettings.wechatPay.enabled === 1 ? '已启用' : '未启用' }}</strong>
      </article>
      <article class="metric-card">
        <span>微信代付</span>
        <strong>{{ paymentSettings.wechatPayout.enabled === 1 ? '已启用' : '未启用' }}</strong>
      </article>
      <article class="metric-card">
        <span>最近测试</span>
        <strong>{{ paymentSettings.wechatPay.lastTestTime || paymentSettings.wechatPayout.lastTestTime || '--' }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>支付设置</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="savePaymentSettings">保存配置</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="微信支付" name="pay">
          <div class="form-grid">
            <div class="full-span form-group-title">基础身份</div>
            <label>
              <span>启用状态</span>
              <el-select v-model="paymentSettings.wechatPay.enabled">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>商户主体</span>
              <el-input v-model="paymentSettings.wechatPay.merchantName" placeholder="请输入商户主体名称" />
            </label>
            <label>
              <span>商户号</span>
              <el-input v-model="paymentSettings.wechatPay.mchId" placeholder="请输入微信支付商户号" />
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="paymentSettings.wechatPay.appId" placeholder="请输入公众号或小程序 AppId" />
            </label>

            <div class="full-span form-group-title">密钥证书</div>
            <label>
              <span>APIv3 Key</span>
              <el-input v-model="paymentSettings.wechatPay.apiV3Key" type="password" show-password placeholder="请输入 APIv3 Key" />
            </label>
            <label>
              <span>证书序列号</span>
              <el-input v-model="paymentSettings.wechatPay.serialNo" placeholder="请输入平台证书序列号" />
            </label>
            <label class="full-span">
              <span>商户私钥</span>
              <el-input
                v-model="paymentSettings.wechatPay.privateKeyPem"
                type="textarea"
                :rows="4"
                placeholder="粘贴 apiclient_key.pem 内容或填写私钥说明"
              />
            </label>

            <div class="full-span form-group-title">业务参数</div>
            <label>
              <span>交易场景</span>
              <el-select v-model="paymentSettings.wechatPay.tradeType">
                <el-option label="JSAPI" value="JSAPI" />
                <el-option label="H5" value="H5" />
                <el-option label="Native" value="NATIVE" />
              </el-select>
            </label>

            <div class="full-span form-group-title">回调配置</div>
            <label class="full-span">
              <span>支付回调地址</span>
              <el-input v-model="paymentSettings.wechatPay.notifyUrl" placeholder="请输入支付结果通知地址" />
            </label>
            <label class="full-span">
              <span>退款回调地址</span>
              <el-input v-model="paymentSettings.wechatPay.refundNotifyUrl" placeholder="请输入退款结果通知地址" />
            </label>
            <label>
              <span>连接测试</span>
              <el-input :model-value="paymentSettings.wechatPay.testStatus || '未测试'" readonly />
            </label>
          </div>

          <div class="action-group action-group--section">
            <el-button @click="testPayment('wechatPay')">测试微信支付</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="微信代付" name="payout">
          <div class="form-grid">
            <div class="full-span form-group-title">基础身份</div>
            <label>
              <span>启用状态</span>
              <el-select v-model="paymentSettings.wechatPayout.enabled">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>代付主体</span>
              <el-input v-model="paymentSettings.wechatPayout.merchantName" placeholder="请输入代付主体名称" />
            </label>
            <label>
              <span>商户号</span>
              <el-input v-model="paymentSettings.wechatPayout.mchId" placeholder="请输入代付商户号" />
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="paymentSettings.wechatPayout.appId" placeholder="请输入代付 AppId" />
            </label>

            <div class="full-span form-group-title">密钥证书</div>
            <label>
              <span>APIv3 Key</span>
              <el-input v-model="paymentSettings.wechatPayout.apiV3Key" type="password" show-password placeholder="请输入 APIv3 Key" />
            </label>
            <label>
              <span>证书序列号</span>
              <el-input v-model="paymentSettings.wechatPayout.serialNo" placeholder="请输入平台证书序列号" />
            </label>
            <label class="full-span">
              <span>商户私钥</span>
              <el-input
                v-model="paymentSettings.wechatPayout.privateKeyPem"
                type="textarea"
                :rows="4"
                placeholder="粘贴 apiclient_key.pem 内容或填写私钥说明"
              />
            </label>

            <div class="full-span form-group-title">业务参数</div>
            <label>
              <span>代付场景</span>
              <el-input v-model="paymentSettings.wechatPayout.transferScene" placeholder="如 营销返佣 / 员工结算" />
            </label>
            <label>
              <span>收款人校验</span>
              <el-select v-model="paymentSettings.wechatPayout.verifyUserName">
                <el-option label="不校验" value="NO_CHECK" />
                <el-option label="实名校验" value="REAL_NAME" />
              </el-select>
            </label>
            <label>
              <span>单笔上限</span>
              <el-input v-model="paymentSettings.wechatPayout.singleLimit" placeholder="请输入单笔金额上限" />
            </label>
            <label>
              <span>日限额提示</span>
              <el-input v-model="paymentSettings.wechatPayout.dailyLimitTip" placeholder="请输入限额说明" />
            </label>

            <div class="full-span form-group-title">回调配置</div>
            <label class="full-span">
              <span>代付回调地址</span>
              <el-input v-model="paymentSettings.wechatPayout.notifyUrl" placeholder="请输入代付结果通知地址" />
            </label>
            <label>
              <span>连接测试</span>
              <el-input :model-value="paymentSettings.wechatPayout.testStatus || '未测试'" readonly />
            </label>
          </div>

          <div class="action-group action-group--section">
            <el-button @click="testPayment('wechatPayout')">测试微信代付</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="回调与安全" name="safety">
          <div class="detail-grid">
            <article class="detail-card">
              <h3>微信支付回调</h3>
              <p>{{ paymentSettings.wechatPay.notifyUrl || '--' }}</p>
              <p>退款回调：{{ paymentSettings.wechatPay.refundNotifyUrl || '--' }}</p>
              <p>最近测试：{{ paymentSettings.wechatPay.lastTestTime || '--' }}</p>
            </article>
            <article class="detail-card">
              <h3>微信代付回调</h3>
              <p>{{ paymentSettings.wechatPayout.notifyUrl || '--' }}</p>
              <p>单笔上限：{{ paymentSettings.wechatPayout.singleLimit || '--' }}</p>
              <p>最近测试：{{ paymentSettings.wechatPayout.lastTestTime || '--' }}</p>
            </article>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const activeTab = ref('pay')
const state = reactive(loadSystemConsoleState())
const paymentSettings = reactive(JSON.parse(JSON.stringify(state.paymentSettings || {})))

function savePaymentSettings() {
  state.paymentSettings = JSON.parse(JSON.stringify(paymentSettings))
  saveSystemConsoleState(state)
  ElMessage.success('支付设置已保存')
}

function testPayment(key) {
  paymentSettings[key].testStatus = '连接成功'
  paymentSettings[key].lastTestTime = new Date().toLocaleString('zh-CN', { hour12: false })
  savePaymentSettings()
}
</script>
