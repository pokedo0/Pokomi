# Following Page 并发限流与缓存设计

## Context

Following 页面订阅大量作者时，现有并发模型会打穿源站限流。问题集中在 `FollowingScreenModel.kt`：

```kotlin
private val searchDispatcher = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS).asCoroutineDispatcher() // 5
private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS) // 5
```

现有缺陷：
1. **无 429 处理** — `loadSubscription` 的 catch 块直接抛 `SearchItemResult.Error`，不重试。
2. **缓存字段未用** — `AuthorSubscription.lastRefreshAt` 已存在，但 load 时不检查，每次都重拉。
3. **无全局刷新模式** — 只能滚动按需加载，无法一次性刷全部。
4. **无进度反馈** — 大批加载时用户看不到进度。

## 已确认的产品决策

经过 brainstorming，以下决策已锁定：

- **限流策略：撞了再退避为主。** 不预设速率、不限制内部并发。源限速只是部分源的特性，且 20-30 req/min 不精确、卡不准点，主动 pacing 性价比低。
- **不按源串行、不限内部并发。** 之前考虑的 per-source channel/semaphore 方案已否决——同源串行对非限速源平白拖慢，对限速源也不能精确控速。
- **保留全局并发上限（资源保护，非速率控制）。** 几十个请求同时 fire 会堵死线程池、涨内存、卡 UI。这与源限速无关，是 app 自身资源问题。
- **刷新模式：页面内后台队列。** 切 tab、app 进后台时队列续跑（Voyager tab 保活 ScreenModel，`screenModelScope` 撑过这些场景）。不引入 WorkManager。
- **全局刷新：settings 开关 + 顶部按钮。** 开关 ON = 打开页面自动全量排队加载；OFF = 滚动按需加载。顶部常驻"全部刷新"按钮。
- **缓存：settings 可配置 TTL（默认 24h）。**

## 设计原则

1. **缓存优先（主力）** — 首次加载后，稳态根本不发请求，429 只在首次大批加载时出现。
2. **429 退避（兜底）** — typed `HttpException(429)` 精确捕获，指数退避重试，耗尽则挂起非死态。
3. **全局并发 cap（资源保护）** — 限制同时在飞的请求数，与速率无关。

## 关键技术依据（recon 确认）

- **429 是 typed 异常，可精确捕获。** `getSearchManga` 失败走 `awaitSuccess()`/`asObservableSuccess()`，抛 `HttpException(val code: Int)`（`OkHttpExtensions.kt:159`）。判 429 = `e is HttpException && e.code == 429`。注意是属性 `e.code`，不是方法 `e.code()`。
- **ScreenModel 切 tab 不 dispose。** `FollowingTab` 用 `rememberScreenModel`，Voyager tab navigator 保活；`screenModelScope` 撑过切 tab + app 进后台，仅 app 被杀才停。符合"页面内后台队列"需求，无需 app 级 scope 或 WorkManager。
- **网络层已有 per-source `RateLimitInterceptor`。** 部分源自己在 OkHttpClient 声明 `rateLimit(N, period)`，OkHttp 层自动排队。我们的应用层退避与之叠加：源声明了就网络层先挡，没声明就靠我们的 429 退避兜底。

## 架构

三个改动面，从下到上：

```
[settings] FollowingPreferences (新增)
    ├── autoLoadAll: Boolean        全局加载开关
    └── cacheTtlHours: Int          缓存 TTL (默认 24)
              │
[逻辑] FollowingScreenModel (改造)
    ├── 缓存判断: shouldRefresh(sub, force) → 三态
    ├── 429 退避: loadWithRetry() 协程内退避循环
    ├── 全局并发 cap: Semaphore (资源保护，提到 ~10)
    └── 状态机扩展: SearchItemResult 增加 RateLimited / Stalled
              │
[UI] FollowingScreen (改造)
    ├── 顶部"全部刷新"按钮 + 进度
    └── RateLimited / Stalled 状态显示文案
```

## 组件设计

### 1. FollowingPreferences（新增）

新建 preference store，注册进 Injekt。两个字段：

| 字段 | 类型 | 默认 | 含义 |
|------|------|------|------|
| `autoLoadAll` | Boolean | false | ON=打开页面全量排队；OFF=滚动按需 |
| `cacheTtlHours` | Int | 24 | 缓存有效期，0=从不（每次刷新都重拉） |

放在 `domain` 或现有 source preferences 旁，跟随项目现有 preference 模式。settings UI 加在 Following 相关设置项下（具体位置实现时定）。

### 2. 缓存判断（FollowingScreenModel）

`AuthorSubscription.lastRefreshAt` 已存在，无需改数据库。新增判断函数返回三态：

```kotlin
enum class LoadDecision { Skip, UseCache, Refresh }

private fun decideLoad(sub: AuthorSubscription, force: Boolean): LoadDecision {
    if (force) return LoadDecision.Refresh
    val hasResult = state.value.results[sub.id] is SearchItemResult.Success
    val ttlMs = preferences.cacheTtlHours().get() * 3600_000L
    val last = sub.lastRefreshAt
    return when {
        ttlMs == 0L -> LoadDecision.Refresh           // TTL=从不 → 总刷新
        last == null -> LoadDecision.Refresh           // 从没刷过 → 刷
        (now() - last) >= ttlMs -> LoadDecision.Refresh // 过期 → 刷
        hasResult -> LoadDecision.Skip                  // 未过期且内存有结果 → 跳过
        else -> LoadDecision.Refresh                    // 未过期但内存无结果(重启后) → 刷一次
    }
}
```

注意：MVP 不持久化搜索结果（design 原 spec 决定），所以"未过期但内存无结果"（app 重启）仍需重刷一次。`lastRefreshAt` 用于"距上次刷新多久"，不是"结果是否还在"。这是已知取舍——真正的结果缓存是未来工作。

### 3. 429 退避（FollowingScreenModel 核心）

替换现有 `loadSubscription` 的 catch 块。退避序列固定：

```
重试间隔(秒): 10 → 20 → 40 → 80 → 160 → 300
共 6 次重试，累计约 10 分钟
到 300s 仍失败 → Stalled (挂起，非死态)
```

退避循环关键逻辑（伪代码）：

```kotlin
private suspend fun loadWithRetry(sub: AuthorSubscription): SearchItemResult {
    val backoff = longArrayOf(10, 20, 40, 80, 160, 300) // 秒
    for (attempt in 0..backoff.size) {  // 0=首发, 1..6=重试
        try {
            globalSemaphore.acquire()
            val result = try { fetch(sub) } finally { globalSemaphore.release() }
            updateAuthorSubscriptionRefreshTime.await(sub.id) // 写 lastRefreshAt
            return SearchItemResult.Success(result)
        } catch (e: HttpException) {
            if (e.code != 429) return SearchItemResult.Error(e)   // 非429 直接失败
            if (attempt >= backoff.size) return SearchItemResult.Stalled // 6次耗尽
            updateResult(sub.id, SearchItemResult.RateLimited(attempt + 1, backoff.size))
            delay(backoff[attempt] * 1000L)  // 退避期间 semaphore 已释放，不占槽
        } catch (e: Exception) {
            return SearchItemResult.Error(e)  // 网络/其他错误，不重试
        }
    }
    return SearchItemResult.Stalled
}
```

**核心点：退避 delay 时 semaphore 已释放。** 否则一个作者退避 5min 白占一个并发槽，拖死整个队列。fetch 占槽，等待不占槽。

**为什么不加抖动：** 退避序列固定即可。几十个作者撞 429 后会因各自首次失败的时间点不同而自然错开，且 fetch 本身耗时有差异，序列固定不会产生严格同步的惊群。保持简单。（若实测出现同步惊群，再补 ±jitter。）

### 4. SearchItemResult 状态扩展

现有 `SearchItemResult`（`SearchScreenModel.kt:263`）是 sealed interface，被 GlobalSearch 共用。**不能直接改共享类型**，否则影响 GlobalSearch。两个选项：

- **选项 a（推荐）**：Following 用自己的状态类型 `FollowingItemResult`，新增 `RateLimited(attempt, max)` 和 `Stalled`。UI 层 `FollowingScreen` 已是独立文件，改动隔离。
- **选项 b**：给共享 `SearchItemResult` 加 `RateLimited`/`Stalled`，GlobalSearch 的 `when` 分支补 `else`。改动面大、影响共享代码。

采用选项 a。`FollowingScreenModel.State.results` 类型从 `PersistentMap<Long, SearchItemResult>` 改为 `PersistentMap<Long, FollowingItemResult>`。

```kotlin
sealed interface FollowingItemResult {
    data object Loading : FollowingItemResult
    data class RateLimited(val attempt: Int, val max: Int) : FollowingItemResult
    data object Stalled : FollowingItemResult
    data class Error(val throwable: Throwable) : FollowingItemResult
    data class Success(val result: List<Manga>) : FollowingItemResult
}
```

### 5. 全局并发 cap（资源保护）

保留 `Semaphore(5)`，但语义改为**纯资源保护**，不再按源细分。值维持 5——去掉按源限制后，5 个全局并发对资源足够，且越保守 = 越少 429。提高反而增加首次加载的 429 churn，无必要。`searchDispatcher` 线程池维持 5。

退避中的作者**不占槽**（见上），所以 cap 始终服务于真正在飞的请求。

### 6. 全局刷新模式 + 进度（FollowingScreenModel + UI）

两个入口：

- **settings `autoLoadAll` 开关 ON**：`init` 时不再只 `loadInitial()`（前5个），而是把全部订阅入队（`decideLoad` 过滤掉缓存未过期的）。
- **顶部"全部刷新"按钮**：常驻，无视开关。`force=false`——尊重 TTL，只补过期的，相当于"全范围下拉刷新"。不是强制全量重拉（避免一键 churn 几十个请求）。

State 新增进度字段：

```kotlin
data class State(
    // ...现有字段
    val refreshProgress: RefreshProgress? = null,
)
data class RefreshProgress(val completed: Int, val total: Int, val rateLimited: Int)
```

进度由 results 状态推导：`completed = 非Loading/非RateLimited 的数量`，`total = 本轮排队数`，`rateLimited = RateLimited 数量`。UI 显示 "12/45（3 限流中）"。

### 7. UI 改动（FollowingScreen）

- 顶部 AppBar 增加"全部刷新"IconButton（已有排序按钮旁）。
- 进度：`refreshProgress != null` 时在 AppBar 副标题或顶部条显示 "已完成/总数"。
- 作者 section 的 `when(result)` 增加两个分支：
  - `RateLimited` → 显示 "限流中，重试 {attempt}/{max}…"（loading 变体，非错误红）
  - `Stalled` → 显示 "暂缓，下拉或点刷新重试"（可点击重试）
- `isRefreshing`（PullRefresh）判定增加：`RateLimited` 也算 refreshing（仍在进行）。

## 刷新层级（重要语义）

全局动作尊重 TTL，只有单作者强制。这样下拉/全部刷新不会误触 churn 几十个请求。

| 动作 | 语义 | force | 429 风险 |
|------|------|-------|---------|
| **单作者刷新按钮** | 我就要这个最新 | `true` 无视 TTL | 单个，低 |
| **下拉刷新** | 检查并补过期的 | `false` TTL 内跳过 | 低（只拉过期） |
| **全部刷新按钮** | 同上，范围=全部 | `false` TTL 内跳过 | 低（只拉过期） |

推论：**没有"强制全量重拉所有"的一键入口**（除非逐个单点）。这是有意为之——全量强制重拉正是 429 灾难场景，不该给一键入口。想强制看某作者最新就单点它。

## 交互总表

| 场景 | 触发 | force | 行为 |
|------|------|-------|------|
| 打开页面 (开关OFF) | `loadInitial` | false | 前5个，`decideLoad` 过滤 |
| 打开页面 (开关ON) | `loadAll` | false | 全部入队，缓存未过期跳过 |
| 滚动可见 | `loadVisible` | false | 单个，`decideLoad` 判断 |
| 作者刷新按钮 | `refresh(id)` | true | 单个强制重拉 |
| 下拉刷新 | `refreshLoaded` | false | 已加载的，TTL 内跳过，只补过期 |
| 顶部全部刷新 | `refreshAll` | false | 全部，TTL 内跳过，只补过期 + 进度 |
| 撞 429 | 退避循环 | — | 10→20→40→80→160→300，释放槽 |
| 6 次耗尽 | — | — | Stalled，下轮可复活 |
| Stalled 复活 | 下拉/全刷/单刷 | false/true | 重置 attempt 重新排队（单点=true） |
| 切 tab / 进后台 | — | — | 队列续跑（screenModelScope 存活） |
| app 被杀 | — | — | 队列停（可接受） |

## 不做的（YAGNI）

- **不引入 WorkManager** — 页面内 scope 已满足"切 tab/进后台续跑"。
- **不按源串行/不限内部并发** — 已否决。
- **不主动 pacing/令牌桶** — 速率不精确，性价比低。
- **不加退避抖动** — 序列固定够用，惊群风险低。
- **不改共享 `SearchItemResult`** — Following 用独立 `FollowingItemResult`。
- **不改数据库** — `lastRefreshAt` 已存在。
- **不持久化搜索结果** — 沿用原 MVP 决定，结果缓存留作未来工作。
- **不做 Stalled 自动定时复活** — 避免后台静默 churn，靠用户主动刷新。

## 变更文件

| 文件 | 类型 | 变更 |
|------|------|------|
| `FollowingPreferences.kt` | 新增 | `autoLoadAll` + `cacheTtlHours` |
| `FollowingScreenModel.kt` | 改造 | `decideLoad`、`loadWithRetry`、全局cap、`refreshAll`、进度、`FollowingItemResult` |
| `FollowingScreen.kt` | 改造 | 全部刷新按钮、进度显示、RateLimited/Stalled 分支 |
| settings screen | 改造 | 加 Following 设置项（开关 + TTL） |
| `i18n-kmk/.../strings.xml` | 改造 | 新增 KMR 文案（限流中/暂缓/全部刷新/TTL标签等） |
| Injekt module | 改造 | 注册 `FollowingPreferences` |
| `AuthorSubscription.kt` | 不改 | `lastRefreshAt` 已有 |
| `UpdateAuthorSubscriptionRefreshTime.kt` | 不改 | 已有 |
| `SearchItemResult` | 不改 | 用独立类型 |

## 测试验证

单元/逻辑：
- `decideLoad` 三态：force→Refresh；无 lastRefreshAt→Refresh；过期→Refresh；未过期+有结果→Skip；未过期+无结果→Refresh；TTL=0→Refresh。
- 退避序列正确：429 走 10→20→40→80→160→300，6 次后 Stalled。
- 非 429 异常直接 Error，不重试。
- 退避期间 semaphore 已释放（不占槽）。

手动：
1. 全源X 几十作者，开关ON 打开页面 → 观察分批加载、429 退避、进度推进。
2. 等待退避，确认 RateLimited 文案显示 attempt/max。
3. 制造持续 429（mock）→ 确认 6 次后 Stalled，文案正确。
4. Stalled 后下拉 → 确认重新排队。
5. 切 tab 再回来 → 确认队列没断。
6. settings 调 TTL=6h → 确认 6h 内不重拉。
7. 开关 OFF → 确认回到滚动按需加载。

仓库验证：
```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
```
