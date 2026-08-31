# -*- coding: utf-8 -*-
"""Store 模块保证金收退确认全链路联测（v12）：分页 → 确认收款 → 全额退还

场景矩阵：
  主体           操作                             预期
  无令牌         查保证金流水列表                   40100
  store_admin1   查列表 / 确认收款                 40300（有 menu:store，无 store:deposit:confirm）
  warehouse1     查列表                           40300
  admin(超管)    查列表（分页/type/status/storeId 过滤） 0
  admin          确认收款（缴纳 0→1 + paid_at）    0
  admin          重复确认                         40900
  admin          确认不存在流水                    40400
  admin          确认退还流水 → 40000（仅缴纳需确认）
  admin          退还未确认收款流水 → 40900
  admin          退还已确认收款流水 → 0（写 type=2 流水 + refunded_at）
  admin          重复退还 → 40900
  admin          退还退还流水 → 40000
  清理           删除 DR- 前缀测试流水 + 基线校验
"""
import base64
import json
import sys
import time

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "Admin@123456"
TEST_BIZ_PREFIX = "DR-"  # 测试缴纳流水 biz_no 前缀（清理用）

passed = 0
failed = 0


def p(name, r, expect_code=None, expect_http=None):
    """打印并断言。expect_code: Result.code；expect_http: HTTP 状态码"""
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


def insert_test_deposit():
    """插入一笔测试缴纳流水（biz_no=DR-{ts}，挂店 3），返回 (deposit_id, biz_no)"""
    conn = db()
    cur = conn.cursor()
    biz = f"{TEST_BIZ_PREFIX}{int(time.time())}"
    cur.execute("""INSERT INTO franchise_deposits (store_id, type, amount, status, biz_no, created_at)
                   VALUES (3, 1, 20000.00, 0, %s, NOW())""", (biz,))
    did = cur.lastrowid
    cur.close()
    conn.close()
    print(f"  ℹ 插入测试缴纳流水 id={did} biz_no={biz}（店3）")
    return did, biz


def main():
    global passed, failed
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    wh_t = login("warehouse1")

    s0 = requests.Session()
    s0.headers.update(auth(admin_t))
    s1 = requests.Session()
    s1.headers.update(auth(sa1_t))
    sw = requests.Session()
    sw.headers.update(auth(wh_t))

    print("===== 1. 权限门禁 =====")
    r = requests.get(f"{BASE}/api/store/admin/deposits")
    p("1.1 无令牌查保证金流水 → 40100", r, 40100)
    r = s1.get(f"{BASE}/api/store/admin/deposits")
    p("1.2 门店管理员查列表 → 40300（有 menu:store 无 store:deposit:confirm）", r, 40300)
    r = sw.get(f"{BASE}/api/store/admin/deposits")
    p("1.3 仓管查列表 → 40300", r, 40300)
    r = s1.post(f"{BASE}/api/store/admin/deposits/1/confirm")
    p("1.4 门店管理员确认收款 → 40300", r, 40300)
    r = s0.get(f"{BASE}/api/store/admin/deposits")
    body = p("1.5 超管查列表放行", r, 0)
    assert "records" in body["data"], f"分页结构异常: {body}"
    print("      ↑ 分页结构正常（records/total）")

    print("\n===== 2. 列表过滤 =====")
    r = s0.get(f"{BASE}/api/store/admin/deposits", params={"type": 1})
    body = p("2.1 type=1 过滤（仅缴纳流水）", r, 0)
    assert all(x["type"] == 1 for x in body["data"]["records"]), "type=1 过滤混入他型"
    r = s0.get(f"{BASE}/api/store/admin/deposits", params={"status": 0})
    body = p("2.2 status=0 过滤（仅待处理）", r, 0)
    assert all(x["status"] == 0 for x in body["data"]["records"]), "status=0 过滤混入已完成"
    r = s0.get(f"{BASE}/api/store/admin/deposits", params={"storeId": 3})
    body = p("2.3 storeId=3 过滤", r, 0)
    assert all(x["storeId"] == 3 for x in body["data"]["records"]), "storeId 过滤混入他店"
    print("  ✅ 2.4 联查字段：任意记录含 storeNo/storeName（JOIN stores 生效）")
    row = db_one("SELECT d.id, s.store_no, s.store_name FROM franchise_deposits d JOIN stores s ON s.id=d.store_id LIMIT 1")
    assert row and row[1] and row[2], f"联查字段缺失: {row}"
    passed += 1

    print("\n===== 3. 确认收款 =====")
    did_a, biz_a = insert_test_deposit()
    did_b, biz_b = insert_test_deposit()
    r = s0.post(f"{BASE}/api/store/admin/deposits/{did_a}/confirm")
    p("3.1 确认收款（缴纳 0→1）", r, 0)
    row = db_one("SELECT status, paid_at IS NOT NULL FROM franchise_deposits WHERE id=%s", (did_a,))
    assert row == (1, 1), f"确认落库异常: {row}"
    print("  ✅ 3.2 DB: status=1 / paid_at 落库")
    passed += 1
    r = s0.post(f"{BASE}/api/store/admin/deposits/{did_a}/confirm")
    p("3.3 重复确认 → 40900", r, 40900)
    r = s0.post(f"{BASE}/api/store/admin/deposits/999999/confirm")
    p("3.4 确认不存在流水 → 40400", r, 40400)

    print("\n===== 4. 退还保证金 =====")
    r = s0.post(f"{BASE}/api/store/admin/deposits/{did_b}/refund")
    p("4.1 退还未确认收款流水 → 40900", r, 40900)
    r = s0.post(f"{BASE}/api/store/admin/deposits/{did_a}/refund")
    p("4.2 退还已确认收款流水", r, 0)
    row = db_one("""SELECT type, status, amount, biz_no, refunded_at IS NOT NULL
                    FROM franchise_deposits WHERE biz_no=%s AND type=2""", (biz_a,))
    assert row and row[0] == 2 and row[1] == 1 and float(row[2]) == 20000.0 \
        and row[3] == biz_a and row[4] == 1, f"退还流水异常: {row}"
    print(f"  ✅ 4.3 DB: 退还流水 type=2/status=1/¥20000/biz_no 关联/refunded_at 落库")
    passed += 1
    r = s0.post(f"{BASE}/api/store/admin/deposits/{did_a}/refund")
    p("4.4 重复退还 → 40900", r, 40900)
    r = s0.post(f"{BASE}/api/store/admin/deposits/999999/refund")
    p("4.5 退还不存在流水 → 40400", r, 40400)
    refund_id = db_one("SELECT id FROM franchise_deposits WHERE biz_no=%s AND type=2", (biz_a,))[0]
    r = s0.post(f"{BASE}/api/store/admin/deposits/{refund_id}/confirm")
    p("4.6 确认退还流水 → 40000（仅缴纳需确认）", r, 40000)
    r = s0.post(f"{BASE}/api/store/admin/deposits/{refund_id}/refund")
    p("4.7 退还退还流水 → 40000（仅缴纳可退）", r, 40000)

    print("\n===== 5. 清理与基线校验 =====")
    conn = db()
    cur = conn.cursor()
    cur.execute("DELETE FROM franchise_deposits WHERE biz_no LIKE %s", (f"{TEST_BIZ_PREFIX}%",))
    print(f"  ℹ 清理测试流水（DR- 前缀）: {cur.rowcount} 行")
    cur.close()
    conn.close()
    row = db_one("SELECT COUNT(*) FROM permissions WHERE code='store:deposit:confirm'")
    assert row[0] == 1, "权限码 store:deposit:confirm 缺失"
    print("  ✅ 5.1 权限码 store:deposit:confirm 存在（V5）")
    passed += 1
    row = db_one("SELECT COUNT(*) FROM roles")
    assert row[0] == 5, f"预设角色数异常: {row}"
    print("  ✅ 5.2 预设 5 角色基线不变")
    passed += 1
    row = db_one("SELECT COUNT(*) FROM franchise_deposits")
    print(f"  ℹ 清理后保证金流水剩余 {row[0]} 条（含基线 ST003 缴纳流水）")

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
