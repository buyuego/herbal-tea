# -*- coding: utf-8 -*-
"""User 模块会员管理联测（v26）：分页 → 筛选 → 详情（地址/积分流水） → 启停 → 越权 → 清理

场景矩阵：
  主体           操作                             预期
  无令牌         查会员列表                       40100
  staff1        查会员列表                       40300（无 menu:member）
  warehouse1    查会员列表                       40300
  store_admin1  查会员列表                       0（V2 已授 109，只读）
  store_admin1  启停会员                         40300（无 member:edit 220）
  admin(超管)   查列表/详情/启停                  0（220 敏感仅超管）
  校验           分页结构；关键词（昵称/openid）过滤；状态过滤；size 钳 100
  校验           订单聚合口径：仅统计已支付及之后（20/30/40/50/90），排除 10/60/70/80
  校验           手机号脱敏（138****1234）；积分余额联查（无账户记 0）
  校验           详情：地址列表默认置顶 + 积分流水（changeTypeDesc/sourceTypeDesc 由服务端填充）
  异常           详情不存在 → 40400；启停状态值非法 → 50000；重复置同状态 → 40900
  校验           禁用触发 token_version +1（R9 即时吊销）
  清理           恢复测试会员状态/手机号/token_version；删除造数积分账户与流水
"""
import sys
import time

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "Admin@123456"

passed = 0
failed = 0


def p(name, r, expect_code=None, expect_http=None):
    global passed, failed
    try:
        body = r.json()
        code = body.get("code")
        msg = body.get("message", body.get("msg", ""))
    except Exception:
        body, code, msg = None, None, r.text[:120]
    ok = True
    detail = f"http={r.status_code} code={code} {msg[:60]}"
    if expect_http is not None and r.status_code != expect_http:
        ok = False
        detail += f" | 期望 HTTP {expect_http}"
    if expect_code is not None and code != expect_code:
        ok = False
        detail += f" | 期望 code={expect_code}"
    mark = "✅" if ok else "❌"
    print(f"  {mark} {name}: {detail}")
    if ok:
        passed += 1
    else:
        failed += 1
    return body


def ok(name, cond, extra=""):
    global passed, failed
    mark = "✅" if cond else "❌"
    print(f"  {mark} {name}{(' | ' + extra) if extra else ''}")
    if cond:
        passed += 1
    else:
        failed += 1


def login(username, password=PW):
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": username, "password": password})
    body = r.json()
    assert body.get("code") == 0, f"{username} 登录失败: {body}"
    return body["data"]["accessToken"]


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def db():
    return pymysql.connect(host="127.0.0.1", port=3306, user="herbal_tea",
                           password="herbal_tea_dev", database="herbal_tea",
                           charset="utf8mb4", autocommit=True)


def db_one(sql, args=()):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    row = cur.fetchone()
    cur.close()
    conn.close()
    return row


def db_exec(sql, args=()):
    conn = db()
    cur = conn.cursor()
    n = cur.execute(sql, args)
    cur.close()
    conn.close()
    return n


def main():
    global passed, failed
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    wh_t = login("warehouse1")
    st1_t = login("staff1")

    s0 = requests.Session()
    s0.headers.update(auth(admin_t))
    s1 = requests.Session()
    s1.headers.update(auth(sa1_t))
    sw = requests.Session()
    sw.headers.update(auth(wh_t))
    s5 = requests.Session()
    s5.headers.update(auth(st1_t))

    # 测试靶子：取一个正常会员
    row = db_one("SELECT id, nickname, phone, status, token_version FROM users WHERE status=1 ORDER BY id LIMIT 1")
    assert row, "无可用会员，请先初始化用户数据"
    uid, nickname, phone, status0, token_ver0 = row
    total_users = db_one("SELECT COUNT(*) FROM users")[0]
    print(f"  ℹ 测试会员 id={uid} nickname={nickname} status={status0} token_version={token_ver0}（会员总数 {total_users}）")

    print("===== 1. 权限门禁 =====")
    r = requests.get(f"{BASE}/api/user/admin/members")
    p("1.1 无令牌查会员列表 → 40100", r, 40100)
    r = s5.get(f"{BASE}/api/user/admin/members")
    p("1.2 店铺员工查列表 → 40300（无 menu:member）", r, 40300)
    r = sw.get(f"{BASE}/api/user/admin/members")
    p("1.3 仓管查列表 → 40300", r, 40300)
    r = s1.get(f"{BASE}/api/user/admin/members")
    body = p("1.4 门店管理员查列表放行（V2 已授 109）", r, 0)
    ok("1.5 分页结构正常", body["data"]["total"] == total_users,
       f"total={body['data']['total']} / 会员数 {total_users}")
    r = s1.put(f"{BASE}/api/user/admin/members/{uid}/status", params={"status": 0})
    p("1.6 门店管理员启停 → 40300（无 member:edit 220）", r, 40300)
    r = sw.put(f"{BASE}/api/user/admin/members/{uid}/status", params={"status": 0})
    p("1.7 仓管启停 → 40300", r, 40300)
    r = s0.get(f"{BASE}/api/user/admin/members")
    p("1.8 超管查列表放行", r, 0)

    print("\n===== 2. 列表筛选与分页 =====")
    r = s0.get(f"{BASE}/api/user/admin/members", params={"keyword": "mock-openid"})
    body = p("2.1 关键词（openid 片段）过滤", r, 0)
    ok("2.2 命中全部（openid 均含 mock-openid）", body["data"]["total"] == total_users,
       f"total={body['data']['total']}")
    if nickname:
        r = s0.get(f"{BASE}/api/user/admin/members", params={"keyword": nickname})
        body = p(f"2.3 关键词（昵称 {nickname}）过滤", r, 0)
        ok("2.4 昵称过滤命中", any(x["id"] == uid for x in body["data"]["records"]))
    r = s0.get(f"{BASE}/api/user/admin/members", params={"keyword": "不存在的会员XYZ"})
    body = p("2.5 关键词无命中", r, 0)
    ok("2.6 空结果", body["data"]["total"] == 0)
    r = s0.get(f"{BASE}/api/user/admin/members", params={"status": 1})
    body = p("2.7 status=1 过滤（正常会员）", r, 0)
    ok("2.8 状态过滤生效", all(x["status"] == 1 for x in body["data"]["records"]))
    r = s0.get(f"{BASE}/api/user/admin/members", params={"status": 0})
    body = p("2.9 status=0 过滤（禁用会员）", r, 0)
    ok("2.10 禁用过滤生效（当前应为空）", all(x["status"] == 0 for x in body["data"]["records"]),
       f"共 {body['data']['total']} 条")
    r = s0.get(f"{BASE}/api/user/admin/members", params={"size": 2, "page": 1})
    body = p("2.11 分页 size=2", r, 0)
    ok("2.12 每页 2 条", len(body["data"]["records"]) == min(2, total_users))
    r = s0.get(f"{BASE}/api/user/admin/members", params={"size": 999})
    body = p("2.13 size 越界钳到 100", r, 0)
    ok("2.14 单页上限 100", body["data"]["size"] == 100, f"size={body['data']['size']}")

    print("\n===== 3. 字段口径（脱敏 / 积分 / 订单聚合） =====")
    r = s0.get(f"{BASE}/api/user/admin/members", params={"keyword": nickname or "mock-openid"})
    body = p("3.1 查询目标会员", r, 0)
    target = [x for x in body["data"]["records"] if x["id"] == uid]
    ok("3.2 命中目标会员", len(target) == 1)
    if target:
        m = target[0]
        ok("3.3 手机号脱敏（无手机号或 138****1234 形态）",
           m["phone"] is None or ("****" in m["phone"] and len(m["phone"]) == 11),
           f"phone={m['phone']}")
        ok("3.4 积分余额字段存在（无积分账户记 0）",
           m["pointsBalance"] is not None and m["pointsBalance"] >= 0,
           f"pointsBalance={m['pointsBalance']}")
        ok("3.5 订单聚合字段齐全",
           all(k in m for k in ("orderCount", "payTotalAmount", "lastOrderAt")),
           f"orderCount={m['orderCount']} payTotal={m['payTotalAmount']}")
    # 订单统计口径：与 SQL 直接聚合比对（仅 20/30/40/50/90）
    row = db_one("""SELECT COUNT(*), COALESCE(SUM(pay_amount),0)
                    FROM orders WHERE user_id=%s AND status IN (20,30,40,50,90)""", (uid,))
    ok("3.6 有效订单统计口径一致（排除 10/60/70/80）",
       target and target[0]["orderCount"] == row[0] and float(target[0]["payTotalAmount"]) == float(row[1]),
       f"接口 {target[0]['orderCount']} 单 / ¥{target[0]['payTotalAmount']} vs SQL {row[0]} 单 / ¥{row[1]}")

    print("\n===== 4. 会员详情 =====")
    # 造数：积分账户 + 积分流水（验证联查），测试后清理
    db_exec("""INSERT INTO user_points_accounts (user_id, balance, total_earned, total_used, total_expired)
               VALUES (%s, 120, 200, 80, 0)
               ON DUPLICATE KEY UPDATE balance=120, total_earned=200, total_used=80""", (uid,))
    db_exec("""INSERT INTO point_records (user_id, store_id, order_id, change_type, source_type,
                                          points, biz_key, created_at)
               VALUES (%s, 1, NULL, 1, 1, 50, %s, NOW()),
                      (%s, NULL, NULL, 5, 2, 10, %s, NOW())""",
            (uid, f"MT-GRANT-{int(time.time())}", uid, f"MT-SIGN-{int(time.time())}"))
    addr_n = db_one("SELECT COUNT(*) FROM user_addresses WHERE user_id=%s", (uid,))[0]

    r = s0.get(f"{BASE}/api/user/admin/members/{uid}")
    body = p("4.1 超管查会员详情", r, 0)
    d = body["data"]
    ok("4.2 概览字段（积分余额 120）", d["member"]["pointsBalance"] == 120,
       f"pointsBalance={d['member']['pointsBalance']}")
    ok("4.3 累计获得/使用（200 / 80）",
       d["member"]["totalEarned"] == 200 and d["member"]["totalUsed"] == 80,
       f"{d['member']['totalEarned']} / {d['member']['totalUsed']}")
    ok("4.4 地址列表条数一致", len(d["addresses"]) == addr_n, f"{len(d['addresses'])} / {addr_n}")
    ok("4.5 积分流水返回（新增 2 条）", len(d["pointRecords"]) >= 2, f"{len(d['pointRecords'])} 条")
    recs = d["pointRecords"]
    ok("4.6 流水文案由服务端填充（changeTypeDesc / sourceTypeDesc）",
       all(x.get("changeTypeDesc") and x.get("sourceTypeDesc") for x in recs),
       f"首行 {recs[0].get('changeTypeDesc')} / {recs[0].get('sourceTypeDesc')}" if recs else "无数据")
    ok("4.7 门店营销积分带门店名、平台活动积分为空",
       any(x["sourceType"] == 1 and x["storeName"] for x in recs)
       and any(x["sourceType"] == 2 and x["storeName"] is None for x in recs))
    r = s0.get(f"{BASE}/api/user/admin/members/999999")
    p("4.8 详情不存在 → 40400", r, 40400)
    r = s1.get(f"{BASE}/api/user/admin/members/{uid}")
    p("4.9 门店管理员查详情放行（只读 109）", r, 0)
    r = s5.get(f"{BASE}/api/user/admin/members/{uid}")
    p("4.10 店铺员工查详情 → 40300", r, 40300)

    print("\n===== 5. 启停与 R9 吊销 =====")
    r = s0.put(f"{BASE}/api/user/admin/members/{uid}/status", params={"status": 0})
    p("5.1 超管禁用会员", r, 0)
    row = db_one("SELECT status, token_version FROM users WHERE id=%s", (uid,))
    ok("5.2 DB status=0", row[0] == 0, f"status={row[0]}")
    ok("5.3 禁用触发 token_version +1（R9 即时吊销）", row[1] == token_ver0 + 1,
       f"{token_ver0} → {row[1]}")
    r = s0.put(f"{BASE}/api/user/admin/members/{uid}/status", params={"status": 0})
    p("5.4 重复禁用 → 40900", r, 40900)
    r = s0.put(f"{BASE}/api/user/admin/members/{uid}/status", params={"status": 2})
    p("5.5 状态值非法 → 50000", r, 50000)
    r = s0.put(f"{BASE}/api/user/admin/members/999999/status", params={"status": 0})
    p("5.6 会员不存在 → 40400", r, 40400)
    r = s0.get(f"{BASE}/api/user/admin/members", params={"status": 0})
    body = p("5.7 禁用后出现在 status=0 列表", r, 0)
    ok("5.8 列表状态过滤命中禁用会员", any(x["id"] == uid for x in body["data"]["records"]))
    r = s0.put(f"{BASE}/api/user/admin/members/{uid}/status", params={"status": 1})
    p("5.9 重新启用", r, 0)
    row = db_one("SELECT status, token_version FROM users WHERE id=%s", (uid,))
    ok("5.10 启用恢复 status=1 且不再 bump token_version", row[0] == 1 and row[1] == token_ver0 + 1,
       f"status={row[0]} token_version={row[1]}")

    print("\n===== 6. 数据清理与基线校验 =====")
    n1 = db_exec("DELETE FROM point_records WHERE biz_key LIKE %s", ("MT-%",))
    n2 = db_exec("DELETE FROM user_points_accounts WHERE user_id=%s", (uid,))
    db_exec("UPDATE users SET status=%s, token_version=%s WHERE id=%s", (status0, token_ver0, uid))
    print(f"  ℹ 清理积分流水 {n1} 行 / 积分账户 {n2} 行")
    row = db_one("SELECT status, token_version, phone FROM users WHERE id=%s", (uid,))
    ok("6.1 会员状态/token_version/手机号恢复基线",
       row[0] == status0 and row[1] == token_ver0 and row[2] == phone, f"{row}")
    ok("6.2 测试积分流水已清空",
       db_one("SELECT COUNT(*) FROM point_records WHERE biz_key LIKE %s", ("MT-%",))[0] == 0)
    ok("6.3 会员总数不变", db_one("SELECT COUNT(*) FROM users")[0] == total_users)
    row = db_one("SELECT COUNT(*) FROM permissions WHERE code='member:edit'")
    ok("6.4 V12 权限码 member:edit 存在", row[0] == 1)
    row = db_one("""SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id
                    WHERE p.code='member:edit'""")
    ok("6.5 member:edit 仅授权超管（1 条）", row[0] == 1, f"授权数={row[0]}")
    row = db_one("""SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id
                    WHERE p.code='menu:member'""")
    ok("6.6 menu:member 授权 3 个角色（超管/财务/店长）", row[0] == 3, f"授权数={row[0]}")

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    finally:
        try:
            db_exec("DELETE FROM point_records WHERE biz_key LIKE %s", ("MT-%",))
            db_exec("DELETE FROM user_points_accounts WHERE total_earned=200 AND total_used=80")
        except Exception as e:
            print(f"  ⚠ 兜底清理失败: {e}")
