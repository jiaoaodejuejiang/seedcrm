# 分销系统方案 B 落地设计

---

## 一、方案边界

方案 B 的定位是：外部电商 / 分销系统负责分销、分佣、提现、退款资金流，SeedCRM 负责承接已成交 / 已支付订单，并完成预约排档、门店服务、核销履约、状态回推。

必须遵守：

- 不建设 SeedCRM 内部分销商城
- 不建设 SeedCRM 内部分销者提现系统
- 不在 SeedCRM 内计算外部分销佣金
- 不让外部系统直接写核心表
- 不让 distribution 已支付订单进入 Clue
- 不新增核心业务模型
- 不用手机号作为唯一幂等依据
- 不将外部分销者默认映射为内部员工
- 不让 API Controller 或 Scheduler 绕过统一入站服务直接写核心表

允许的受控入口：

- 外部分销 paid event 通过 Open API / Scheduler 入站
- 入站事务内创建或匹配 Customer
- 入站事务内创建或更新 Order(paid)
- 后续仍通过预约 / 排档 / 门店服务创建 PlanOrder

---

## 二、数据结构

### Customer 扩展字段

Customer 是会员信息视角的主体，不新增 Member 核心模型。

建议字段：

- `source`：来源大类，示例 `douyin | distribution | manual | imported`
- `source_channel`：来源渠道，继续兼容现有字段
- `external_partner_code`：外部合作方编码
- `external_member_id`：外部会员 ID
- `external_member_role`：外部会员身份，示例 `leader | member | buyer`，仅展示 / 标签使用
- `raw_data`：外部会员原始数据

匹配优先级：

1. `external_partner_code + external_member_id`
2. 标准化手机号 `phone`
3. 进入异常队列，等待人工处理

约束：

- `external_partner_code + external_member_id` 应可建立唯一约束
- `external_member_role` 不参与 RBAC、薪酬、分佣、提现规则
- 外部会员字段只能通过受控入站服务写入

### Order 扩展字段

Order 是外部分销成交在 SeedCRM 内的承接结果。

建议字段：

- `source`：来源大类
- `external_partner_code`：外部合作方编码
- `external_order_id`：外部订单 ID
- `external_trade_no`：外部支付流水号
- `external_member_id`：外部购买会员 ID
- `external_promoter_id`：外部分销者 / 团长 / 团员 ID
- `external_status`：外部订单状态快照
- `refund_status`：外部退款状态快照
- `raw_data`：外部订单原始数据

幂等约束：

- `external_partner_code + external_order_id` 必须唯一
- 同一外部订单重复推送只能更新快照和日志，不能重复创建 Order

状态约束：

- distribution 入库初始状态必须是 `paid`
- `used` 只能由 SeedCRM 门店核销 / 服务履约流程推进
- `cancelled | refund_pending | refunded` 只能记录外部状态变化和业务影响，不处理资金

### 入站事件日志

入站事件日志不是核心模型，但必须作为接入基础设施存在。

建议字段：

- `partner_code`
- `event_type`
- `idempotency_key`
- `external_order_id`
- `request_headers`
- `request_query`
- `request_body`
- `signature_result`
- `process_status`
- `internal_customer_id`
- `internal_order_id`
- `error_message`
- `retry_count`
- `received_at`

状态：

- `RECEIVED`
- `PROCESSING`
- `SUCCESS`
- `FAILED`
- `IGNORED_DUPLICATE`
- `DEAD_LETTER`

### 配置结构

为了避免每次业务调整都改代码，需要系统化配置：

- 渠道配置：`provider_code`、名称、启用状态、运行模式、AppId、Secret、签名算法、IP 白名单
- 字段映射：手机号、姓名、外部会员 ID、外部订单 ID、金额、支付时间、门店、商品、状态、raw_data 路径
- 状态映射：外部支付 / 核销 / 退款 / 取消状态到 SeedCRM 内部状态
- 来源字典：`source` 显示名、排序、启停、是否已支付来源
- 来源 tab 分组：页面 tab 名称、包含 source、可见角色、默认筛选
- 脱敏配置：手机号、raw_data、支付流水、外部会员 ID 的展示权限

---

## 三、接口定义

### 外部分销已支付订单入站

接口：

```http
POST /open/distribution/events
```

事件类型白名单：

- `distribution.order.paid`
- `distribution.order.cancelled`
- `distribution.order.refund_pending`
- `distribution.order.refunded`
- `distribution.promoter.synced`
- `distribution.member.updated`

请求头：

- `X-Partner-Code`
- `X-Timestamp`
- `X-Nonce`
- `X-Signature`
- `X-Idempotency-Key`

`distribution.order.paid` 必填字段：

- `partnerCode`
- `eventId`
- `eventType`
- `occurredAt`
- `externalOrderId`
- `externalTradeNo`
- `externalMemberId`
- `externalPromoterId`
- `member.phone`
- `order.amount`
- `order.paidAt`
- `order.type`
- `order.storeCode`
- `rawData`

请求体建议：

```json
{
  "eventType": "distribution.order.paid",
  "eventId": "evt_202604290001",
  "partnerCode": "external_mall",
  "occurredAt": "2026-04-29T10:00:00+08:00",
  "member": {
    "externalMemberId": "m_10001",
    "name": "张三",
    "phone": "13800000000",
    "role": "member"
  },
  "promoter": {
    "externalPromoterId": "p_90001",
    "role": "leader"
  },
  "order": {
    "externalOrderId": "o_20001",
    "externalTradeNo": "pay_30001",
    "type": "coupon",
    "amount": 19900,
    "paidAt": "2026-04-29T09:58:00+08:00",
    "storeCode": "store_001",
    "status": "paid"
  },
  "rawData": {}
}
```

响应体：

```json
{
  "traceId": "trace_001",
  "idempotencyResult": "CREATED",
  "customerId": 10001,
  "orderId": 20001,
  "processStatus": "SUCCESS"
}
```

规则：

- `distribution.order.paid` 必须创建或匹配 Customer，并创建 Order(paid)
- `distribution.order.cancelled` 不得反向创建 Order
- `distribution.order.refund_pending` 不得反向创建 Order
- `distribution.order.refunded` 只更新退款状态和业务提示，不处理资金退款
- `distribution.order.cancelled` 只更新外部取消状态，不删除 Customer / Order
- 所有事件必须先落日志，再进入业务事务
- 重复事件必须返回幂等结果，不重复建单
- `idempotency_key` 缺失时必须拒绝，不允许系统生成随机值替代
- 外部状态必须先做状态映射，不允许外部状态字符串直接驱动内部核心流程

### 入站事务规则

`distribution.order.paid` 必须在单事务内完成：

1. 写入 / 锁定幂等记录
2. 校验 partner、事件类型、签名、timestamp、nonce
3. 匹配或创建 Customer
4. 创建或更新 Order(paid)
5. 写入外部关系和来源快照
6. 写入 integration_log
7. 写入必要 outbox 事件
8. 提交事务

禁止：

- Customer 创建成功但 Order 创建失败
- 先响应成功再异步创建 Customer / Order
- 业务事务内同步调用外部系统
- API Controller 直接写 Customer / Order / PlanOrder
- Scheduler 直接写 Customer / Order / PlanOrder

### 接口调试 dry-run

接口：

```http
POST /scheduler/integration/debug/distribution/dry-run
```

输入：

- provider
- eventType
- payload
- headers
- query

输出：

- 验签结果
- 字段映射结果
- 状态映射结果
- Customer 匹配结果
- Order 幂等结果
- 是否会落异常队列

规则：

- dry-run 不落核心业务表
- 只有 `integration_admin | integration_operator` 可用
- raw_data 默认脱敏展示

### 会员信息列表

接口：

```http
GET /members
```

查询参数：

- `sourceTab`：`distribution | douyin`
- `phone`
- `name`
- `externalMemberId`
- `storeId`
- `wecomBound`
- `privateOwnerId`
- `createdAtStart`
- `createdAtEnd`
- `page`
- `pageSize`

返回字段：

- Customer 基础信息
- 来源显示名
- 外部会员身份
- 最近 paid Order
- 累计订单数
- 累计成交金额
- 企微绑定状态
- 标签
- 私域负责人
- 最近同步时间

规则：

- 该接口读取 Customer 视图，不读取 Clue 列表
- tab 由 source_tab 配置控制，不在前端硬编码
- 字段脱敏由 Permission 控制
- 不提供手动创建 Customer 的按钮

### 履约状态回推

接口由 Scheduler Outbox 异步执行，不在业务事务内同步请求外部系统。

事件：

- `crm.plan_order.created`
- `crm.plan_order.arrived`
- `crm.plan_order.finished`
- `crm.order.used`

Outbox 状态：

- `pending`
- `processing`
- `success`
- `failed`
- `dead_letter`

规则：

- 回推失败进入 retry / dead-letter
- 每次回推必须记录请求、响应、traceId
- 外部失败不能回滚本地门店履约事务

---

## 四、权限规则

### RBAC

新增或明确权限：

- `member:view`
- `member:tag`
- `integration:config`
- `integration:log:view`
- `integration:log:raw_view`
- `integration:debug`
- `integration:retry`
- `integration:reconcile`
- `distribution_order:view`
- `distribution_order:retry`
- `distribution_order:reconcile`
- `distribution_order:exception_handle`
- `outbox:view`
- `outbox:retry`

角色建议：

- `admin`：全部可见
- `private_domain_service`：可见绑定客户 / 分配客户的会员信息
- `integration_admin`：可配置渠道、签名、字段映射、状态映射
- `integration_operator`：可查看日志、dry-run、重试，不可改核心业务数据
- `partner_app`：外部应用身份，不是后台用户

### ABAC

数据范围：

- `self`：本人绑定或负责客户
- `team`：本团队客户
- `store`：本门店订单
- `all`：全量
- `partner`：外部应用只能访问自身 `partner_code` 数据

强制规则：

- 外部 Open API 不以 admin 身份执行
- partner_app 只能写入自身 partner_code 范围事件
- 门店服务人员只能查看本门店可履约订单
- 私域客服只能查看绑定或授权客户
- raw_data 默认脱敏
- 接口调试、重放、补偿必须记录操作审计
- 人工重试、人工补偿、状态修正必须记录操作者和原因

---

## 五、流程

### 入站处理流程

1. 接收 Open API / Scheduler 事件
2. 记录入站事件日志
3. 验证 partner、签名、timestamp、nonce、IP 白名单
4. 校验 idempotency_key
5. 执行字段映射和状态映射
6. 校验金额、手机号、门店、商品
7. 匹配或创建 Customer
8. 创建或更新 Order(paid)
9. 写入处理结果
10. 返回 traceId 和幂等结果

### 预约履约流程

1. Order(paid) 进入顾客排档可预约池
2. 客服按门店档期创建 PlanOrder
3. 门店人员核销线上团购券 / 定金
4. 客户确认服务单
5. PlanOrder 状态进入 servicing
6. 门店完成服务
7. PlanOrder → finished
8. Order → used
9. Scheduler Outbox 回推履约状态

### 异常处理流程

进入异常队列的情况：

- 缺手机号且缺 external_member_id
- 外部门店编码无法映射
- 金额为空或非法
- 外部订单号重复但金额 / 门店冲突
- 状态倒退
- 已进入服务中的订单收到外部退款 / 取消
- 同一个外部订单号重复但外部支付流水不一致
- 手机号命中多个 Customer

处理要求：

- 不静默覆盖核心数据
- 不自动删除 Customer / Order
- 必须可人工查看、重试、标记已处理
- 必须保留 raw_data 和处理日志

### 退款 / 取消处理流程

SeedCRM 不处理退款资金，但必须记录退款对履约的影响。

规则：

- `refund_pending / refunded / cancelled` 不允许删除 Order
- 未预约订单收到退款：标记不可预约
- 已预约未服务订单收到退款：进入待取消 / 门店确认
- 已服务 / 已核销订单收到退款：进入异常处理
- 本地 `used / finished` 与外部 `refunded` 冲突时，不自动反向改履约结果
- 退款状态必须保留外部退款单号、退款时间、退款金额、raw_data

---

## 六、页面方案

### 菜单位置

菜单：

```text
私域客服
  会员信息
```

默认可见：

- admin
- private_domain_service

暂不开放：

- store_service
- finance
- online_customer_service
- clue_manager

### 页面结构

页面顶部只保留必要筛选，不放大面积统计卡片。

tab：

- 全部会员
- 分销来源
- 抖音来源

筛选：

- 姓名
- 手机号
- 外部会员 ID
- 企微绑定状态
- 私域负责人
- 门店
- 最近成交时间

列表字段：

- 姓名
- 手机号
- 首次来源
- 成交来源
- 来源
- 外部会员身份
- 外部订单号
- 外部会员 ID
- 最近订单
- 最近成交金额
- 最近成交时间
- 累计订单数
- 企微绑定
- 标签
- 私域负责人
- 最近同步时间
- 操作

操作：

- 查看详情
- 打标签
- 企微触达
- 查看订单

禁止操作：

- 新增会员
- 转客户
- 转订单
- 分佣配置
- 提现处理
- 退款资金处理
- 客资分配
- 公海回收

文案约束：

- 页面使用“会员 / 客户 / 成交”，不使用“客资 / 线索 / 公海 / 分配”
- 分销 tab 文案使用“分销成交”或“外部分销成交”，禁止使用“分销客资”
- 抖音必须区分“抖音客资”与“抖音成交”
- 同一客户既有抖音 Clue 又有分销 Order 时，列表按成交来源归类，详情展示完整来源轨迹

### 详情页

详情页只展示：

- 客户基础信息
- 来源归因
- 外部身份绑定
- 订单记录
- 企微关系
- 标签画像
- 同步日志
- 来源轨迹

不展示：

- 外部分销佣金
- 外部分销提现
- 外部分销层级树
- 外部资金退款操作

---

## 七、验收清单

- distribution 已支付事件不创建 Clue
- distribution 已支付事件能原子创建 / 匹配 Customer + Order(paid)
- 重复 eventId / external_order_id 不重复建单
- Customer 匹配优先使用 external_member_id，其次 phone
- Order 必须绑定 Customer
- PlanOrder 仍只能由预约 / 排档 / 门店服务流程创建
- 会员信息列表读取 Customer 视图，不混入 Clue 列表
- 分销来源 tab 不展示分佣、提现、退款资金操作
- 抖音来源 tab 能区分抖音客资和抖音成交
- private_domain_service 只能看绑定 / 授权客户
- partner_app 只能访问自身 partner_code 数据
- raw_data 默认脱敏
- 接口调试 dry-run 不落业务表
- 履约状态回推失败不回滚本地事务
- 异常数据可查看、可重试、可人工处理

