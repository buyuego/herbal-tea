# -*- coding: utf-8 -*-
"""结算管理接口全链路联测（v21）

场景矩阵：
  接口                        权限要求                    预期
  POST /admin/generate        menu:settlement(107)       admin 生成成功；重复生成报「无未结算订单」
  GET  /admin/page            menu:settlement            admin 分页 1 行 + 金额口径核验
  GET  /admin/{id}            menu:settlement            明细分行 D15（订单级 sales/commission/积分/券）
  POST /admin/{id}/confirm    menu:settlement            10→20；重复 confirm 状态机拒绝
  POST /admin/{id}/review     settlement:review(213)     20→30
  POST /admin/{id}/pay        settlement:payout(214敏感)  30→40 + payout_no
  越权矩阵                    店长/店员/仓管无 107        40300；财务(role2)有 107/213 无 214 → pay 40300

造数：订单 4/5/6（店1，status 70 已签收）→ 90 已完结 + finished_at=今日；
  订单5 模拟平台活动积分（points_source=2, pd=5.00, earned=300）
  订单6 模拟门店积分+本店券（pd=3.00, earned=200, coupon=10.00）
金额期望：total=528.00  commission=26.40  pointsDeduct(店)=3.00  pointsCostStore=2.00
  pointsCostPlatform=8.00  coupon=10.00  final=486.60
"""
import json
import sys

import bcrypt
import pymysql
import requests

BASE = "http://localhost:8080"
ADMIN_PW = "Admin@123456"
PW = "Store@123456"
PERIOD = "2026-08-31"
ORDERS = [4, 5, 6]

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea",
          password="herbal_tea_dev", database="herbal_tea",
          charset="utf8mb4", autocommit=True)

passed = 0
failed = 0


def db_rows(sql, args=None):
    conn = pymysql.connect(**DB)
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        return cur.fetchall()
    finally:
        conn.close()


def db_exec(sql, args=None):
    conn = pymysql.connect(**DB)
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        return cur.rowcount
    finally:
        conn.close()


def login(username, password=PW):
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": username, "password": password}, timeout=10)
    body = r.json()
    assert body.get("code") == 0, f"login {username} failed: {body}"
    return body["data"]["accessToken"] if isinstance(body["data"], dict) else body["data"]


def call(method, path, token, json_body=None, params=None):
    h = {"Authorization": f"Bearer {token}"}
    r = requests.request(method, f"{BASE}{path}", headers=h, json=json_body, params=params, timeout=15)
    return r.status_code, r.json()


def p(name, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  [PASS] {name}")
    else:
        failed += 1
        print(f"  [FAIL] {name}  {detail}")


def money(v):
    return round(float(v), 2)


def main():
    print("== 0. 前置快照（订单 4/5/6 原始字段，用于清理恢复） ==")
    snap = db_rows(
        "SELECT id, status, points_source, points_deduct_amount, coupon_amount, points_earned, finished_at "
        "FROM orders WHERE id IN (4,5,6)")
    assert len(snap) == 3, f"expect 3 orders, got {len(snap)}"
    orig = {r[0]: r for r in snap}
    for r in snap:
        print(f"  order {r[0]}: status={r[1]} ps={r[2]} pd={r[3]} coupon={r[4]} earned={r[5]} fin={r[6]}")

    print("== 1. 造数：置 90 已完结 + 积分/券多样化 ==")
    db_exec("UPDATE orders SET status=90, finished_at=%s WHERE id=4", (PERIOD + " 12:00:00",))
    db_exec("UPDATE orders SET status=90, finished_at=%s, points_source=2, points_deduct_amount=5.00, points_earned=300 WHERE id=5", (PERIOD + " 13:00:00",))
    db_exec("UPDATE orders SET status=90, finished_at=%s, points_source=1, points_deduct_amount=3.00, points_earned=200, coupon_amount=10.00 WHERE id=6", (PERIOD + " 14:00:00",))
    p("造数 3 单已完结", len(db_rows("SELECT id FROM orders WHERE id IN (4,5,6) AND status=90")) == 3)

    print("== 2. 登录 ==")
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    staff_t = login("staff1")
    wh_t = login("warehouse1")
    p("五账号登录（admin/店长/店员/仓管）", all([admin_t, sa1_t, staff_t, wh_t]))

    print("== 3. 生成结算单（admin, storeId=1, period=%s） ==")
    st, body = call("POST", "/api/settlement/admin/generate", admin_t,
                    params={"storeId": 1, "period": PERIOD})
    p("generate 成功", st == 200 and body.get("code") == 0, f"{st} {body}")

    st, body = call("POST", "/api/settlement/admin/generate", admin_t,
                    params={"storeId": 1, "period": PERIOD})
    p("重复生成被拒（无未结算订单）", body.get("code") != 0, f"{st} {body}")

    print("== 4. 分页 + 金额口径 ==")
    st, body = call("GET", "/api/settlement/admin/page", admin_t,
                    params={"storeId": 1, "period": PERIOD, "page": 1, "size": 10})
    rows = body.get("data", {}).get("records", []) if body.get("code") == 0 else []
    p("分页返回 1 行", st == 200 and body.get("code") == 0 and len(rows) == 1, f"{st} {body}")
    if rows:
        s = rows[0]
        sid = s["id"]
        # 订单4 门店积分成本 1.76 + 订单6 2.00 = 3.76；final = 528-26.40-3.00-3.76-10.00
        exp = dict(totalAmount=528.00, commissionAmount=26.40, pointsDeductAmount=3.00,
                   pointsCostStore=3.76, pointsCostPlatform=8.00, couponCostStore=10.00,
                   finalAmount=484.84, orderCount=3, status=10)
        for k, v in exp.items():
            got = s.get(k)
            ok = (money(got) == v) if isinstance(v, float) else (got == v)
            p(f"金额口径 {k}={v}", ok, f"got={got}")
    else:
        sid = None
        p("拿到结算单 id", False)

    if not sid:
        print("!! 无结算单，终止"); cleanup(orig); sys.exit(1)

    print("== 5. 详情明细分行（D15） ==")
    st, body = call("GET", f"/api/settlement/admin/{sid}", admin_t)
    d = body.get("data") or {}
    items = d.get("items") or []
    # 期望 12 行：o4(sales+comm+cost店)=3 o5(sales+comm+pd平台+cost平台)=4 o6(sales+comm+pd店+cost店+券)=5
    p("明细共 12 行", st == 200 and len(items) == 12, f"got {len(items)}")
    types = {}
    for it in items:
        key = (it["itemType"], it["direction"])
        types[key] = types.get(key, 0) + 1
    p("销售额行×3(direction=1)", types.get((1, 1)) == 3, str(types))
    p("佣金行×3(direction=2)", types.get((2, 2)) == 3, str(types))
    p("平台积分抵扣×1(direction=3)", types.get((3, 3)) == 1, str(types))
    p("平台积分成本×1(direction=3)", types.get((5, 3)) == 1, str(types))
    p("门店积分抵扣×1(direction=2)", types.get((3, 2)) == 1, str(types))
    p("门店积分成本×2(direction=2)", types.get((4, 2)) == 2, str(types))
    p("本店券成本×1(direction=2)", types.get((6, 2)) == 1, str(types))
    amt_platform = sum(float(i["amount"]) for i in items if i["direction"] == 3)
    p("平台承担合计 8.00", money(amt_platform) == 8.00, f"got {amt_platform}")

    print("== 6. 状态机全链路 confirm→review→pay ==")
    st, body = call("POST", f"/api/settlement/admin/{sid}/confirm", admin_t)
    p("confirm 10→20", st == 200 and body.get("code") == 0, f"{st} {body}")
    st, body = call("POST", f"/api/settlement/admin/{sid}/confirm", admin_t)
    p("重复 confirm 拒绝", body.get("code") != 0, f"{st} {body}")
    row = db_rows("SELECT status, confirm_status, confirmed_at, version FROM settlements WHERE id=%s", (sid,))[0]
    p("落库 status=20 confirm_status=2(人工确认)", row[0] == 20 and row[1] == 2 and row[2] is not None, str(row))

    st, body = call("POST", f"/api/settlement/admin/{sid}/review", admin_t)
    p("review 20→30", st == 200 and body.get("code") == 0, f"{st} {body}")
    row = db_rows("SELECT status, reviewed_by FROM settlements WHERE id=%s", (sid,))[0]
    p("落库 status=30 reviewed_by=1", row[0] == 30 and row[1] == 1, str(row))

    st, body = call("POST", f"/api/settlement/admin/{sid}/pay", admin_t)
    p("pay 30→40", st == 200 and body.get("code") == 0, f"{st} {body}")
    row = db_rows("SELECT status, payout_no, paid_at FROM settlements WHERE id=%s", (sid,))[0]
    p("落库 status=40 payout_no=PO* paid_at 非空",
      row[0] == 40 and row[1] and row[1].startswith("PO") and row[2] is not None, str(row))

    print("== 7. 越权矩阵（107/213/214） ==")
    for name, tk in [("店长 store_admin1", sa1_t), ("店员 staff1", staff_t), ("仓管 warehouse1", wh_t)]:
        st, body = call("GET", "/api/settlement/admin/page", tk)
        p(f"{name} 无 menu:settlement → 40300", body.get("code") == 40300, f"{st} {body}")
        st, body = call("POST", f"/api/settlement/admin/{sid}/pay", tk)
        p(f"{name} pay 越权 → 40300", body.get("code") == 40300, f"{st} {body}")

    print("== 8. 平台财务（临时号 role=2：107+213 无 214） ==")
    db_exec("DELETE FROM admin_users WHERE username='fin_tmp1'")
    db_exec(
        "INSERT INTO admin_users (username, password_hash, real_name, phone, role_id, status, token_version) "
        "VALUES ('fin_tmp1', %s, '临时财务', '13800000099', 2, 1, 0)",
        (bcrypt.hashpw(PW.encode(), bcrypt.gensalt()).decode(),))
    fin_t = login("fin_tmp1", PW)
    st, body = call("GET", "/api/settlement/admin/page", fin_t)
    p("财务分页 OK", st == 200 and body.get("code") == 0, f"{st} {body}")
    # 已付款单，review/pay 均应被状态机拒；用新造单验证 pay 40300 需再生成一张 → 用无权限验证代替
    st, body = call("POST", f"/api/settlement/admin/{sid}/review", fin_t)
    p("财务 review 已付单 → 状态机拒绝(非 40300)", body.get("code") != 0 and body.get("code") != 40300, f"{st} {body}")
    st, body = call("POST", f"/api/settlement/admin/{sid}/pay", fin_t)
    p("财务 pay → 40300（无 214 敏感权限）", body.get("code") == 40300, f"{st} {body}")

    print("== 9. 清理回基线 ==")
    cleanup(orig)
    print(f"\n===== 结果: PASS {passed} / FAIL {failed} =====")
    sys.exit(1 if failed else 0)


def cleanup(orig):
    db_exec("DELETE FROM settlement_items")
    db_exec("DELETE FROM settlements")
    for oid, r in orig.items():
        db_exec(
            "UPDATE orders SET status=%s, points_source=%s, points_deduct_amount=%s, "
            "coupon_amount=%s, points_earned=%s, finished_at=%s WHERE id=%s",
            (r[1], r[2], r[3], r[4], r[5], r[6], oid))
    db_exec("DELETE FROM admin_users WHERE username='fin_tmp1'")
    n = db_rows("SELECT COUNT(*) FROM settlements")[0][0] + db_rows("SELECT COUNT(*) FROM settlement_items")[0][0]
    f = db_rows("SELECT COUNT(*) FROM admin_users WHERE username='fin_tmp1'")[0][0]
    print(f"  清理完成：settlements/items 残留={n} 临时财务残留={f}")
    p("清理回基线", n == 0 and f == 0)


if __name__ == "__main__":
    try:
        main()
    finally:
        pass
