# TradeExample 贸易业务示例

用三项业务演示库存怎么扣和还、产品图片怎么批量同步，以及外部报价暂时不可用时怎么查汇率。

技术栈：Java 17、Maven、Spring Boot 3.5.7、MyBatis-Plus 3.5.14、MySQL、Redis。图片服务和汇率来源默认使用项目自带的模拟接口。

## 核心流程

1. **出库与库存**：创建草稿时，先分配最早入库的批次，提交时才扣库存并记录流水。作废已提交的单据时，按原流水把箱数加回对应批次。重新生成时，先还回已扣库存，再按新需求分配。库存不足或版本冲突时，整笔操作回滚。
2. **产品图片同步**：自有产品、客户产品和配件各建一个任务，交给线程池并行处理。每页最多 500 条，默认每页最多等 30 秒，每个任务限时 3 分钟。单张图片失败会记录原因，其他图片继续处理。上传失败和旧图清理失败可以分别重试。
3. **汇率查询**：查询时只读 Redis。后台每 30 分钟尝试刷新，用 Redisson 锁避免多个实例同时抓取。抓取超时或报价校验失败时保留上次报价。尚无报价，或任一币种的报价发布时间已超过 72 小时，查询返回 HTTP 503。

## 项目结构

| 目录 | 负责什么 |
| --- | --- |
| [wms](src/main/java/com/tradeexample/wms) | 创建、提交、作废和重新生成出库单，记录库存变动 |
| [imagesync](src/main/java/com/tradeexample/imagesync) | 同步产品图片、查询进度、重试失败记录 |
| [exchange](src/main/java/com/tradeexample/exchange) | 抓取、校验、缓存和查询汇率 |
| [common](src/main/java/com/tradeexample/common) | 健康检查、公共配置和错误处理 |
| [db/migration](src/main/resources/db/migration) | 建表、更新表结构、写入示例数据 |

## 启动

**1. 初始化数据库**

先启动 MySQL 和 Redis。默认连接本机 MySQL 的 3306 端口和 Redis 的 6379 端口。从项目根目录打开 MySQL 客户端，执行建库脚本：

```sql
SOURCE database/bootstrap.sql;
```

脚本创建 `tradeexample` 库。应用首次启动时，Flyway 自动建表并写入示例数据。

**2. 配置并运行**

在项目根目录打开 PowerShell，输入有权在该库建表和读写数据的 MySQL 账号：

```powershell
$env:TRADEEXAMPLE_DB_USERNAME = Read-Host 'MySQL 用户名'
$dbPassword = Read-Host 'MySQL 密码' -AsSecureString
$env:TRADEEXAMPLE_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password

mvn clean package
java -jar .\target\tradeexample-0.0.1-SNAPSHOT.jar
```

服务默认监听 `http://127.0.0.1:18080`。如需更换 Redis 地址或设置密码，启动前设置 `TRADEEXAMPLE_REDIS_ADDRESS` 和 `TRADEEXAMPLE_REDIS_PASSWORD`。其他配置见 [application.yml](src/main/resources/application.yml)。

## 常用接口

以下路径都以 `/api/v1` 开头。有请求正文时使用 `Content-Type: application/json`。

| 方法 | 路径（省略 `/api/v1`） | 用途 |
| --- | --- | --- |
| GET | `/health` | 检查服务 |
| POST | `/outbound-orders` | 创建出库草稿，请求示例见下方 |
| GET | `/outbound-orders/{id}` | 查看单据和当前分配明细 |
| POST | `/outbound-orders/{id}/submit` | 提交并扣库存，传当前 `revision` 和 `version` |
| POST | `/outbound-orders/{id}/cancel` | 作废单据，传当前 `revision` 和 `version` |
| POST | `/outbound-orders/{id}/regenerate` | 重新生成草稿，传当前 `revision`、`version` 和新的 `demands` |
| POST | `/image-products/{productId}/sync` | 同步单个产品的图片 |
| POST | `/image-sync-tasks` | 启动批量同步，返回三类任务的编号 |
| GET | `/image-sync-tasks/{taskId}` | 查看任务进度 |
| GET | `/image-sync-tasks/{taskId}/items` | 查看明细，按 `afterProductId` 和 `limit` 翻页 |
| POST | `/image-sync-tasks/{taskId}/items/{itemId}/retry` | 重试同步，查询参数 `expectedExecutionNo` 取明细中的 `executionNo` |
| POST | `/image-sync-tasks/{taskId}/items/{itemId}/cleanup-retry` | 重试清理旧图，查询参数 `expectedRetryCount` 取明细中的 `cleanupRetryCount` |
| GET | `/exchange-rates` | 查询最近一次仍有效的汇率报价 |

**出库示例**：初始库存里，`SKU-A` 有两批，分别为 10 箱和 8 箱。向 `POST /api/v1/outbound-orders` 发送以下正文，申请出库 12 箱：

```json
{
  "warehouseCode": "WH-MAIN",
  "outboundDate": "2026-11-25",
  "demands": [
    {"purchaseOrderNo": "PO-202611-0001", "productNo": "SKU-A", "boxCount": 12}
  ]
}
```

草稿会从第一批分配 10 箱、第二批分配 2 箱。用返回的 `data.order.id` 调用提交接口，正文传入同一响应中的 `revision` 和 `version`。提交后两批分别剩 0 箱和 6 箱。用最新版本作废单据后，两批恢复为 10 箱和 8 箱。
