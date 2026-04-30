# CONTEXT.md

---

## 一、主链路（唯一）

标准客资链路：

Clue → Customer → Order → PlanOrder

分销成交链路：

ExternalDistributionPaidOrder → Customer → Order(paid) → PlanOrder

规则：

- Clue 代表未成交或待转化客资，douyin / 普通客资必须先进入 Clue
- distribution 已成交 / 已支付订单不进入 Clue
- distribution 只能通过受控 Open API / Scheduler 入站事务创建或匹配 Customer，并创建 Order(paid)
- ExternalDistributionPaidOrder 是入站事件，不是核心模型
- 所有预约、到店、服务履约仍必须进入 PlanOrder
- 禁止在 Customer / Order / PlanOrder 之外建立平行履约链路

---

## 二、核心模型约束

### Clue

- 来源：douyin
- 必须统一入口
- 支持去重（phone / wechat）
- 字段：raw_data 必须存在
- 不承接 distribution 已成交 / 已支付订单
- 不参与 distribution 已支付订单的自动分配、公海、线索转化率统计

---

### Customer

- 标准创建时机：Order 创建时
- 分销创建时机：接收到 ExternalDistributionPaidOrder 时，在同一事务内直接创建或匹配 Customer
- 来源：douyin | distribution | manual | imported
- 唯一匹配字段：
  - phone（本地标准化手机号）
  - external_partner_code + external_member_id（外部会员身份）
- 禁止外部系统直接创建 Customer
- 允许受控 Open API / Scheduler 在 distribution paid order 入站事务中创建或匹配 Customer
- 字段：raw_data 必须存在
- 必须支持来源和身份标识：
  - source
  - source_channel
  - external_partner_code
  - external_member_id
  - external_member_role（仅展示 / 画像，不参与权限、分佣、提现规则）

---

### Order

- 必须绑定 Customer
- 类型：deposit | coupon | distribution_product
- 状态：paid | used | cancelled | refund_pending | refunded
- distribution 订单入库时必须为 paid
- distribution 订单不得先进入 Clue
- 必须支持幂等唯一键：external_partner_code + external_order_id
- 必须记录外部状态快照：
  - source
  - external_partner_code
  - external_order_id
  - external_trade_no
  - external_member_id
  - external_promoter_id
  - external_status
  - refund_status
  - raw_data
- 外部分销佣金、提现、退款资金流不在 SeedCRM 内处理

---

### PlanOrder

- 必须绑定 Order
- 数量限制：1:1
- 状态（顺序）：
  - arrived
  - servicing
  - finished
- distribution 订单只有进入预约 / 排档 / 门店服务流程后才能创建 PlanOrder
- 禁止外部系统直接创建 PlanOrder

---

### WeComRelation

- 仅绑定 Customer
- 禁止创建 Customer

---

### order_role_record

- 字段：
  - plan_order_id
  - role_code
  - user_id
  - start_time
  - is_current

- 规则：
  - 同角色同时间唯一
  - 必须全流程记录

---

## 三、调度中心（Scheduler）

### 功能集合

- 外部调用
- 数据同步
- 队列处理
- 失败重试
- 对外接口
- 接口调试

---

### 调度规则

- 抖音接口调用频率：1分钟
- 同步方式：增量
- 必须支持：
  - retry
  - queue
  - log
- V1 内置可配置调度任务：
  - DOUYIN_CLUE_INCREMENTAL：抖音客资增量拉取，只进入 Clue
  - DISTRIBUTION_OUTBOX_PROCESS：处理履约状态 Outbox 回推队列
  - DISTRIBUTION_EXCEPTION_RETRY：处理分销异常重试队列
  - DISTRIBUTION_STATUS_CHECK：回查外部分销订单状态，发现取消 / 退款状态后转成入站事件重放
  - DISTRIBUTION_RECONCILE_PULL：拉取外部分销对账记录，统一转成入站事件处理
- Distribution 调度任务只能处理队列、重试、补偿和对账，不允许直接写 Customer / Order / PlanOrder

---

### 分销接入

- Open API：接收外部分销已支付订单、会员、分销者、退款 / 取消状态
- Scheduler：负责补偿同步、失败重试、状态回查、对账拉取、履约状态回推
- Open API 与 Scheduler 必须复用同一个入站业务服务
- API Controller 禁止直接写 Customer / Order / PlanOrder
- Scheduler 禁止绕过入站业务服务直接写 Customer / Order / PlanOrder
- 只有 event_type = distribution.order.paid 才允许创建或匹配 Customer + Order(paid)
- cancelled / refund_pending / refunded 只能更新已有 Order 的外部状态和履约影响，不得反向创建 Order
- 入站必须支持：
  - signature
  - timestamp
  - nonce
  - idempotency_key
  - partner_code
  - retry
  - queue
  - log
  - raw_data
- 出站必须通过 outbox 异步回推
- 禁止业务事务同步依赖外部系统成功响应
- 禁止无幂等键创建 Order
- 禁止使用手机号作为唯一幂等依据
- 接口调试必须支持 dry-run，不落库预览字段映射、状态映射、Customer 匹配结果、Order 幂等结果

### 分销履约回推与异常队列

- PlanOrder finished 后，Order 完成状态变化必须先写入 scheduler_outbox_event，再由 Scheduler 异步回推外部分销系统
- 回推事件类型 V1 固定为 crm.order.used
- Outbox 状态：
  - PENDING
  - PROCESSING
  - SUCCESS
  - FAILED
  - DEAD_LETTER
- LIVE 模式回推必须携带 partner_code、idempotency_key、timestamp、nonce、signature
- MOCK 模式只模拟成功，不调用外部平台
- 分销入站失败必须同时保留 callback log 和 distribution_exception_record
- 异常队列状态：
  - OPEN
  - RETRY_QUEUED
  - HANDLED
- 异常队列只用于追踪、人工处理和重试，不允许绕过入站服务直接写 Customer / Order / PlanOrder
- 异常队列重试必须由 Scheduler 以可信重放方式重新调用 DistributionEventIngestService
- 可信重放可以跳过外部请求签名头校验，但仍必须保留 partner_code、idempotency_key、raw_data、callback log
- 重放成功后异常记录标记为 HANDLED；重放失败后回到 OPEN，等待人工修复配置或数据后再次入队
- 分销状态回查 / 对账拉取只能读取外部状态或本地 MOCK 数据；如需改变订单状态，必须生成 distribution.order.* 事件并调用 DistributionEventIngestService.replayFromScheduler
- 分销状态回查 / 对账拉取不得直接更新 Customer / Order / PlanOrder；paid 事件可由对账拉取创建或匹配 Customer + Order(paid)，本地已有订单的 paid 状态回查视为无变化
- LIVE 模式下分销状态回查、对账拉取接口路径必须可配置；MOCK 模式必须可独立运行，便于联调和回归测试

---

## 四、权限模型（强制）

### RBAC

角色集合：

- online_customer_service
- clue_manager
- store_service
- finance
- private_domain_service
- admin
- integration_admin
- integration_operator
- partner_app

权限格式：

module:action

示例：

- clue:view
- clue:assign
- clue:recycle
- order:view
- order:update
- order:finish
- finance:approve
- integration:config
- integration:debug
- integration:retry
- integration:reconcile
- distribution_order:view
- distribution_order:retry
- distribution_order:reconcile
- member:view
- member:tag

---

### ABAC

数据范围：

- self
- team
- store
- all
- partner

---

### 权限规则

- online_customer_service → self | team（clue）
- clue_manager → all（clue 分配 / 回收）
- store_service → store（order）
- finance → order(status=finished)
- private_domain_service → 绑定客户（member / customer）
- integration_operator → 查看接口日志、异常、重试结果，不可修改核心业务数据
- integration_admin → 配置渠道、签名、字段映射、状态映射、调度策略
- partner_app → 非后台用户，只能访问自身 partner_code 范围数据
- raw_data 默认脱敏，只有授权角色可查看完整内容

---

## 五、客资分配规则

- 支持：
  - auto_assign
  - manual_assign
  - public_pool
  - recycle

- 权限：
  - clue_manager 才能配置自动分配
  - 普通客服只能操作已分配数据

- distribution 已支付订单不参与客资分配、公海、回收、线索转化率统计

---

## 六、薪酬规则

### 数据来源

order_role_record

---

### 角色

- normal_cs
- senior_cs
- leader

---

### 统计维度

- monthly
- team
- conversion

---

### 档位

个人：

- standard
- excellent
- champion
- explosive

团队：

- team_standard
- team_excellent

---

### 规则约束

- 必须可配置
- 禁止写死逻辑
- 基于角色记录计算
- 外部分销佣金、提现、退款资金流不进入 SeedCRM 薪酬计算
- distribution 订单只有进入 PlanOrder 并产生 order_role_record 后，才参与内部门店服务薪酬

---

## 七、流程规则

### 标准客资流程

1. Clue 入库（Scheduler）
2. 分配 / 公海
3. 跟进
4. 创建 Order → Customer
5. 创建 PlanOrder
6. 状态流转：
   arrived → servicing → finished
7. Order → used
8. 薪酬计算

### 分销成交流程

1. ExternalDistributionPaidOrder 入库（Open API / Scheduler）
2. 验签、防重放、幂等校验
3. 字段映射、状态映射、金额校验
4. 创建或匹配 Customer
5. 创建或更新 Order(paid)
6. 预约 / 排档创建 PlanOrder
7. 门店核销 / 服务确认
8. PlanOrder → finished
9. Order → used
10. 回推履约状态给外部分销系统
11. 外部分销系统负责分佣、提现、退款资金流

---

## 八、模块边界（固定）

- Clue
- Customer
- Order
- PlanOrder
- Permission
- Scheduler
- Salary
- WeCom

---

## 九、会员信息视角

- 【会员信息】是 Customer 视图，不是新的核心模型
- 菜单暂放在【私域客服】下
- 默认仅 admin / private_domain_service 可见，后续通过菜单管理和权限策略配置
- 页面以 Customer 为主行，聚合最近 Order、来源、外部身份、企微绑定、标签、私域负责人
- 使用 tab 区分来源视角：
  - 分销来源
  - 抖音来源
- 分销来源展示的是外部分销已支付成交客户，不是分销后台、佣金中心或提现中心
- 抖音来源必须区分“抖音客资 Clue”和“抖音成交 Customer + Order”
- 页面文案必须使用“会员 / 客户 / 成交”，禁止使用“客资 / 线索 / 公海 / 分配”
- 会员信息页禁止直接创建 Customer、直接创建 Order、触发分销佣金、触发提现、触发退款资金处理
- 可配置项：
  - source 字典
  - source_tab 分组
  - member_role 展示字典
  - 字段脱敏规则
  - 角色可见 tab

---

## 十、技术约束

- Spring Boot 3
- MySQL
- MyBatis Plus
- 必须事务一致性
- 外部数据保留 raw_data
- 外部入站必须可追踪、可重试、可补偿、可对账
- 金额、手机号、raw_data 等敏感字段必须按权限脱敏

---

## 十一、全局约束

- 约束优先
- 模型不可变
- 权限必须存在
- 流程必须可验证
- 分销系统选择方案 B
- SeedCRM 不负责外部分销分佣、提现、退款资金流
- SeedCRM 只承接已成交 / 已支付数据、预约排档、门店履约、状态回推
- distribution paid order 不进入 Clue
- 外部接入必须受控、幂等、可追踪、可补偿
