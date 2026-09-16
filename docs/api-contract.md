# 课程注册系统 · 前后端接口契约 v1

> 供后端同学实现参考。前端已按此契约封装在 `src/api/`，并内置 Mock（`src/api/mockdb.ts`）。
> 后端就绪后：在项目根目录 `.env` 中设置 `VITE_USE_MOCK=false`，服务跑在 `http://localhost:8000`（端口可在 `vite.config.ts` 代理中修改）。

## 通用约定

- 基础路径：`/api`；请求/响应均为 JSON
- 鉴权：除登录外均需请求头 `Authorization: Bearer <token>`
- 统一错误格式（非 2xx）：

```json
{ "message": "错误描述", "errors": [ { "offeringId": "CS-401", "code": "full|prerequisite|conflict", "message": "具体原因" } ] }
```

- 角色枚举：`STUDENT`（学生）/ `PROFESSOR`（教授）/ `REGISTRAR`（教务）

## 1. 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | body `{username, password}` → `200 {token, user:{id, name, role}}` / `401 用户名或密码错误` |
| POST | `/auth/logout` | → `204` |

## 2. 课程目录（学生）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/offerings?keyword=&dept=` | → `200 Offering[]` |

Offering 结构：

```json
{ "id": "CS-301", "code": "CS-301", "title": "软件工程", "dept": "计算机科学",
  "professor": "Morgan 博士", "days": ["周一", "周三"], "start": 540, "end": 630,
  "room": "科学楼 204", "seatsTotal": 10, "seatsTaken": 8, "prerequisites": ["CS-201"] }
```

> `start`/`end` 为距 00:00 的分钟数（09:00 → 540）；`days` 为"周一~周五"子集。
> 学生视图中 `professor` 在注册关闭前一律返回空串（关闭后才公开真实教授名）；关闭注册后被取消的课程带 `"cancelled": true`。

## 3. 课表（学生，核心用例 Register for Courses）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/schedules/me` | → `Schedule` |
| PUT | `/schedules/me` | 暂存（校验数量/时间冲突/重复课程，不占座；已注册名额按新主选集合对账释放）→ `Schedule`；注册已关闭 `409` |
| POST | `/schedules/me/submit` | 提交注册 → `200 Schedule`；校验失败 `422 {errors}`；注册已关闭 `409` |
| DELETE | `/schedules/me` | 删除课表 → `204`；注册已关闭 `409` |

```json
// Schedule
{ "status": "draft | saved | submitted",
  "items": [ { "offeringId": "CS-301", "type": "primary | alternate", "status": "selected | enrolled" } ] }
```

业务规则：主选 ≤ 4 门、备选 ≤ 2 门；提交时校验先修课、容量（满员）、时间冲突；课程容量上限 10 人、开课下限 3 人。

## 4. 成绩

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/report-card/me` | → `{semesters:[{term, items:[{code,title,credits,grade}]}]}` |
| GET | `/professor/offerings` | 教授上学期待录成绩课程 → `[{id,title,term,students}]` |
| GET | `/professor/offerings/{id}/roster` | → `[{studentId,name,grade}]` |
| PUT | `/professor/offerings/{id}/grades` | body `{grades:[{studentId, grade|null}]}`，grade ∈ A/B/C/D/F/I |

## 5. 教授任教（Select Courses to Teach）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/professor/eligible` | → `Offering[]`（附加字段 `teaching:boolean` 表示当前是否已选） |
| PUT | `/professor/teaching` | body `{offeringIds}` → `200`；时间冲突 `409 {message, errors:[{offeringId, message}]}` |

## 6. 教务（Registrar）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/registrar/offerings` | 课程情况总览 → `Offering[]`（始终含真实教授名，关闭后含 `cancelled` 标记） |
| GET / POST | `/registrar/students` | 列表 / 新增（POST 返回含系统生成的 id） |
| PUT / DELETE | `/registrar/students/{id}` | 修改 / 删除 |
| GET / POST | `/registrar/professors` | 同上 |
| PUT / DELETE | `/registrar/professors/{id}` | 同上 |
| POST | `/registrar/close-registration` | 关闭注册 → `200 {cancelled:["ART-120"], billed:26}`；注册进行中/已关闭 `409` |

Student 字段：`{id?, name, dob, ssn, status, graduationDate}`；Professor 字段：`{id?, name, dob, ssn, status, dept}`。

## 7. 安全要求（后端必须实现）

- 学生只能读取/操作**自己的**课表与成绩单
- 只有教授能录入成绩；只有教务能维护师生信息、关闭注册
- 关闭注册后：学生/教授的选课与任教操作返回 `409`
