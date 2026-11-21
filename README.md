# 西浦空间 · CampusSpace

CSI301 Mobile Computing 小组项目 —— 一个面向西浦（SIP 校区）的校内空间资源查询 App。

学生常见的三个痛点：**找自习室难、预约信息分散、找公共设备麻烦**。这个 App 把分散的信息聚到一处，
用「当前状态」帮你决定现在去哪，再用「空间纸条」把线下实体和人的经验连起来。

## 功能

底部三个 Tab + 一个全局辅助功能，共 6 个页面：

| 页面 | 功能 |
|---|---|
| 1 首页 / 教学楼 | 按楼栋（FB / CB / SA）和状态（全部 / 当前空闲 / 即将上课）筛选教室与公共空间 |
| 2 教室详情 | 设施标签、昨天~后天日期切换、**自定义 View 时间轴色带**（灰=空闲 蓝=有课 红线=现在）、本教室纸条 |
| 3 可预约空间 | 讨论室 / 静音仓 / 讨论区 / 图书馆座位 四类分类 + 区域联动筛选 |
| 4 可预约详情 | 讨论室与静音仓按时段列表展示；图书馆按六列座位网格展示，四种状态对齐官方图例 |
| 5 公共设备 | 楼栋 + 楼层双下拉，饮水机 / 打印机 / 公共休息处 / 开放座位区 分类，设备报修纸条 |
| 6 纸条发布 | 失物招领 / 空间碎碎念 / 设备报修 三类，可配图，位置强制绑定当前空间 |

**预约一律跳转学校官方系统**，本 App 只做信息聚合与状态展示，不内置预约交易。

## 技术栈

- Java + XML 布局（Empty Views Activity，minSdk 24 / targetSdk 37）
- 底部导航 `BottomNavigationView` + 3 个 Fragment
- `RecyclerView` + 5 个适配器
- **Room** 本地数据库（15 张表，版本 2）+ 演示模板初始化，支持版本 1 无损迁移
- 自定义 `View`（`TimelineView`）绘制时间轴色带
- 隐式 `Intent` + `ACTION_VIEW` 跳转官方预约系统
- Photo Picker 选图（无需相册权限），图片复制到应用私有目录

App **不声明任何权限**：不用 INTERNET（跳浏览器）、不用存储权限（Photo Picker）、不定位，飞行模式也能运行。

## 构建

```bash
# 需要 Android Studio（自带 JDK）与 Android SDK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 里有本机 SDK 路径，不纳入版本控制，由 Android Studio 自动生成。

## 数据说明

`app/src/main/assets/seed.json` 为**模拟数据模板**（3 栋楼 × 5 层 × 15 间教室 = 225 间、1815 条周课表、
10 个讨论室、26 个静音仓、720 个预约时段、100 个图书馆座位、70 台设备、50 条纸条）。
原始数据受课程豁免，线上版本将对接教务系统导出。

模板会转换为具体日期课次和预约状态批次。教室状态由有效课表批次与当前时间计算；缺失或过期数据显示未知。座位演示状态只覆盖今天。

`assets/catalog.json` 提供需求中的楼栋目录和图书馆，尚无资料的楼栋保留空列表。楼层、空间、预约分组与网格位置均从数据库读取。官网首页占位地址已清空，实际预约链接待配置。

数据库字段、完整 ER 图、批次导入及页面对应关系见 [数据库说明](docs/database.md)。Room 导出的版本结构位于 `app/schemas/`。

## 目录

```
app/src/main/java/com/xjtlu/campusspace/
├── core/        TimeProvider、StatusCalculator、TimeText（纯逻辑，可单测）
├── data/        Room 实体、DAO、数据库、SeedLoader、Repo（内存缓存）
├── ui/          MainActivity、home / booking / facility / note、widget（TimelineView）
├── adapter/     5 个 RecyclerView 适配器
└── util/        ImageStore、UrlOpener
```
