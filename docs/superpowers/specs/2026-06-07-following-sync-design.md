# Following Sync Design

## Context

Komikku 的云同步基于现有备份/恢复数据流。Google Drive、WebDAV 和 SyncYomi 都会上传同一份同步数据，再由本地恢复流程应用到设备。

新增 following 页面后，目前同步内容还没有覆盖关注作者列表、作者排序和置顶状态。关注页缓存只是本机加载优化，且依赖本机漫画数据，不适合跨设备同步。

## Goals

- 让云同步和手动备份都能包含 following 页面配置。
- 让用户可以在“选择同步内容”中选择是否同步 following。
- 同步关注作者列表、排序、置顶和作者来源/搜索信息。
- 不同步 following 缓存。
- 尽量兼容旧版本 APP，避免旧版本读取新同步数据时崩溃。

## Non-Goals

- 不同步关注页缓存结果。
- 不做逐作者冲突合并。
- 不引入复杂删除记录。
- 不改变 library 当前同步策略。

## Current Behavior

Library 同步当前更接近混合策略：

- 远端有、本地没有的漫画会添加到本地。
- 两边都有的漫画会按版本和状态合并。
- 部分字段会采用远端状态，部分字段会保留本地或远端任一有效状态。
- 本地额外存在的收藏不会因为远端没有就直接删除。

Following 的需求不同：用户希望每次操作后立即同步，并希望新增、删除、排序、置顶都能在其他设备得到同一结果。因此 following 更适合作为一整份配置同步。

## Design

Following 作为独立同步内容加入备份和云同步。

同步内容包括：

- 关注作者列表。
- 每个作者的来源和搜索信息。
- 显示名称。
- 排序。
- 置顶状态。
- 用于判断新旧的 following 整体更新时间。

同步内容不包括：

- 页面缓存。
- 缓存中的漫画结果。
- 缓存加载时间。

## Conflict Rule

Following 作为一整份列表处理，较新的完整列表胜出。

规则：

- 新机首次同步时，如果本机没有 following 同步状态，直接拉取远端完整列表。
- 普通同步时，比较本机 following 整体更新时间和远端 following 整体更新时间。
- 本机更新，则保留本机完整 following，并上传到远端。
- 远端更新，则用远端完整 following 应用到本机。
- 时间相同，则保留本机，避免无意义写入。

这个规则要求每次 following 增删、排序、置顶变化后更新整体时间。

示例：

1. A 设备新增作者 A 并同步。
2. B 设备同步，获得作者 A。
3. B 设备删除作者 C 并同步。
4. A 设备再次同步，发现远端 following 更新，于是采用 B 的完整列表，作者 C 也在 A 上删除。

代价：

- 如果两台设备离线分别修改 following，后同步的完整列表会覆盖先同步的完整列表。
- 这是为保持删除、排序和置顶一致性接受的简化策略。

## Manual Restore

手动恢复备份时，如果用户勾选 following，也使用同一套新旧判断：

- 备份中的 following 更新，则应用备份完整列表。
- 本机 following 更新，则保留本机。
- 新设备或本机没有 following 同步状态时，应用备份完整列表。

这样手动恢复和云同步行为一致。

## Sync Selection

“选择同步内容”中新增 following 选项，默认开启。

手动备份/恢复界面也新增 following 选项。用户未勾选时，不创建或恢复 following 数据。

## Compatibility

新版本会在同步数据中加入新的 following 部分。旧版本 APP 应能忽略未知部分，因此读取新备份或同步文件时不应崩溃。

已知风险：

- 旧版本 APP 不会保留未知 following 数据。
- 如果旧版本 APP 在云端已有新 following 数据后再次同步上传，它可能把云端 following 数据丢掉。
- 这个风险无法通过新版本完全解决，只能通过设备升级到支持 following 同步的版本规避。

## Existing Sync Gap

调研发现，现有 feed 数据已经进入备份和恢复，但同步合并路径可能没有完整保留它。实现 following 同步时应一并修复该缺口，避免同类数据在云同步合并后丢失。

## Testing

需要覆盖：

- following 被选中时会进入备份/同步数据。
- following 未选中时不会进入备份/同步数据。
- 新机首次同步会拉取远端 following。
- 本机 following 更新时，本机完整列表胜出并上传。
- 远端 following 更新时，远端完整列表胜出并应用到本机。
- 删除作者后同步，其他设备会删除同一作者。
- 排序和置顶同步后保持一致。
- 缓存不进入备份/同步数据。
- 旧长度的选项数据不会导致恢复任务崩溃。
- 现有 feed 数据在同步合并后不丢失。

## Open Decisions

No open decisions. User approved whole-list latest-wins behavior for following.
