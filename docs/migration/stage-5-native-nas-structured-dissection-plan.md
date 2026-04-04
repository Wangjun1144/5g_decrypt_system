# 阶段五：Native NAS 结构化解码方案

## 1. 目标

下一阶段不再继续以“只提业务字段”为中心，而是以 Wireshark `nas_5gs` dissector 的分层思路为参考，逐步把当前 native NAS 解码升级成结构驱动的消息级/IE 级解码器。

本阶段目标：

- 保留现有可用的 native NAS parity 链
- 新增一条隔离的“结构化 NAS 解码骨架”
- 先落消息级 dissector、IE reader、字段树模型
- 以 `Registration Request` 为第一条模板消息
- 后续继续按同样模式扩展到 `Identity Response`、`Authentication Request/Response`、`Security Mode Command`

## 2. 设计原则

1. 以 Wireshark 的 dissector 分层为行为参考
   - 参考 `wiretap -> epan -> dissector -> proto_tree`
   - 我们不复制源码，但要求分层和输出语义尽量一致

2. 先搭框架，再扩字段
   - 不再让 `Nas5gsHeaderParser` 无限增长
   - 把消息分支和 IE 解析独立成单独 dissector

3. 平铺字段和字段树并存
   - 平铺字段继续服务当前 parity 和业务桥接
   - 字段树服务后续更完整的协议展开和调试可视化

4. 继续双轨验证
   - synthetic parity：同一输入对比 tshark JSON
   - real sample parity：同一真实样本对比保存的 Wireshark 输出
   - structured tests：验证字段树结构和 IE 偏移逻辑

## 3. 建议架构

### 3.1 结构化 NAS 解码核心组件

- `Nas5gsStructuredDissector`
  - 负责外层 header、plain/security follow-up、消息分派

- `Nas5gsMessageDissector`
  - 按 message type 拆分的消息级 dissector 接口

- `Nas5gsIeReader`
  - 统一处理 U8/U16、TLV/LV、TBCD 等基础读取

- `DecodedFieldNode`
  - 表示结构化字段树节点

- `Nas5gsStructuredDecodeResult`
  - 同时承载：
    - `messageType`
    - `messageTypeName`
    - `decodedFields`
    - `fieldTree`

### 3.2 迁移关系

当前链路：

- `Nas5gsEntryDissector`
  - 直接调用 `Nas5gsHeaderParser`
  - 返回平铺字段

目标链路：

- `Nas5gsEntryDissector`
  - 先进入 `Nas5gsStructuredDissector`
  - 结构化结果中继续导出平铺字段
  - 后续再逐步淘汰旧的 header-only 解析器

这样能保证：

- 当前测试不被破坏
- 新框架可以持续长
- 最终能平滑切换

## 4. 测试策略

### 4.1 结构化单测

新增 `Nas5gsStructuredDissectorTests`：

- 验证 common header 解析
- 验证 `Registration Request` 的字段树
- 验证 `messageType` 分派行为
- 验证 IE 偏移读取

### 4.2 parity 测试

继续保留：

- `NasDissectorParityIT`
- `RealSampleNasParityIT`

要求：

- 当前已覆盖消息类型的公共字段保持通过
- 新增字段时，优先纳入 parity

### 4.3 业务模型桥接测试

继续保留并扩展：

- `NativeNasAssemblerTests`

保证结构化框架扩展后，现有 `NasInfo` 装配结果不回退。

## 5. 下一步落地顺序

1. 新增字段树模型
2. 新增 `Nas5gsIeReader`
3. 新增 `Nas5gsMessageDissector` 接口
4. 新增 `RegistrationRequestNasMessageDissector`
5. 新增 `Nas5gsStructuredDissector`
6. 新增结构化测试
7. 评估何时让 `Nas5gsEntryDissector` 切到新框架

## 6. 验收标准

满足以下条件视为本阶段基础完成：

- `Registration Request` 已经通过结构化框架解码
- 可同时得到平铺字段和字段树
- 现有 NAS parity 测试不回退
- 当前业务模型桥接仍然可用
