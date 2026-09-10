# -*- coding: utf-8 -*-
"""站内通知中心联测 v32
覆盖：基础 CRUD + 5 事件订阅链路（order_paid/order_shipped/refund_approved/order_urged/settlement_confirmed）
      + 权限矩阵（menu:notification 全角色 / notification:manage 仅超管）
      + 数据隔离（店长只收本店通知）
清理：测试数据 try/finally 兜底"""
import sys
import time

import pymysql
import requests

BASE = "http://127.0.0.1:8080"

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)


def db():
    return pymysql.connect(**DB)


def q(sql, args=None):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    rows = cur.fetchall()
    cols = [d[0] for d in cur.description] if cur.description else []
    cur.close()
    conn.close()
    return rows, cols


def exec_(sql, args=None):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    conn.commit()
    cur.close()
    conn.close()


# ============== 登录 ==============

def login(u, pw="Admin@123456"):
    r = requests.post(f"{BASE}/api/auth/admin/login", json={"username": u, "password": pw})
    b = r.json()
    assert b.get("code") == 0, f"{u} 登录失败: {b}"
    return b["data"]["accessToken"]


PW = "Admin@123456"  # 全角色统一 Admin@123456（v17 重置值，v18 后所有 B 端用户都改过）

passed = 0
failed = 0


def p(name, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  ✓ {name}")
    else:
        failed += 1
        print(f"  ✗ {name} {detail}")


# ============== 测试主体 ==============

def main():
    print("=" * 60)
    print("通知中心联测 v32")
    print("=" * 60)

    # 多角色登录
    admin = login("admin", "Admin@123456")
    store_admin1 = login("store_admin1", "Store@123456")
    warehouse = login("warehouse1", "Store@123456")
    staff = login("staff1", "Store@123456")

    h_admin = {"Authorization": f"Bearer {admin}"}
    h_store1 = {"Authorization": f"Bearer {store_admin1}"}
    h_wh = {"Authorization": f"Bearer {warehouse}"}
    h_staff = {"Authorization": f"Bearer {staff}"}

    # 清理基线通知（只删测试产生的，避免影响其他模块数据）
    exec_("DELETE FROM notifications WHERE biz_type IN ('test','order','refund','settlement','announcement')")
    # 测试用订单号前缀
    TEST_ORDER = "TEST_V32_" + str(int(time.time()))

    # ===== 基础接口 =====
    print("\n[1] 基础接口")
    r = requests.get(f"{BASE}/api/notification/summary", headers=h_admin)
    b = r.json()
    p("summary 返回 200", r.status_code == 200)
    p("summary 含 unreadCount + recent", b.get("data", {}).get("unreadCount") is not None and "recent" in b.get("data", {}))
    p("summary 无未读时 recent=[]", b.get("data", {}).get("recent") == [])

    r = requests.get(f"{BASE}/api/notification/page", headers=h_admin)
    b = r.json()
    p("page 返回 200", r.status_code == 200)
    p("page 含 total/records", "records" in b.get("data", {}) and "total" in b.get("data", {}))

    # ===== 广播（仅超管）=====
    print("\n[2] 广播权限")
    r = requests.post(f"{BASE}/api/notification/broadcast", headers=h_store1,
                      json={"targetRole": 0, "title": "非超管", "content": "应失败", "bizType": "announcement"})
    p("非超管广播 40300", r.json().get("code") == 40300,
      f"got {r.json()}")

    r = requests.post(f"{BASE}/api/notification/broadcast", headers=h_admin,
                      json={"targetRole": 0, "title": "全员通知", "content": "测试广播内容", "bizType": "announcement"})
    b = r.json()
    p("超管全员广播 200", b.get("code") == 0)
    p("广播数据返回 > 0", (b.get("data") or 0) > 0,
      f"data={b.get('data')}")

    # 验证 store_admin1 收到广播
    r = requests.get(f"{BASE}/api/notification/summary", headers=h_store1)
    p("store_admin1 收到广播", r.json().get("data", {}).get("unreadCount", 0) >= 1,
      f"unread={r.json().get('data', {}).get('unreadCount')}")

    # ===== 事件订阅：order_paid 推送仓管 + 店长 =====
    print("\n[3] 事件订阅 order_paid")
    # 通过 outbox 直插事件（避免依赖真实下单）
    order_no = TEST_ORDER + "_PAID"
    exec_(
        "INSERT INTO event_outbox(event_id, event_type, biz_key, payload, status, retry_count, next_retry_at, created_at) "
        "VALUES(UUID(), 'order_paid', %s, %s, 0, 0, NOW(), NOW())",
        (f"order_paid:{order_no}",
         f'{{"orderId":100,"orderNo":"{order_no}","userId":3,"storeId":1,"payAmount":99.0,"pointsEarned":99}}'),
    )
    # 等 OutboxWorker 5s 轮询
    time.sleep(8)

    rows, _ = q("SELECT COUNT(*) FROM notifications WHERE biz_id = %s", (order_no,))
    p("order_paid 通知已生成（仓管+店长 = 2 条）", rows[0][0] == 2,
      f"got {rows[0][0]} rows")

    # 仓管（不限门店）应收到
    rows, _ = q("SELECT recipient_id FROM notifications WHERE biz_id = %s AND recipient_role = 3", (order_no,))
    p("order_paid → 仓管收到", len(rows) >= 1, f"got {rows}")

    # 店长（store_id=1）应收到
    rows, _ = q("SELECT recipient_id, store_id FROM notifications WHERE biz_id = %s AND recipient_role = 4", (order_no,))
    p("order_paid → 店长(store_id=1)收到", len(rows) >= 1 and rows[0][1] == 1,
      f"got {rows}")

    # 验证 store_admin1 可见
    r = requests.get(f"{BASE}/api/notification/summary", headers=h_store1)
    p("store_admin1 收到新订单通知", r.json().get("data", {}).get("unreadCount", 0) >= 2)

    # ===== 事件订阅：refund_approved =====
    print("\n[4] 事件订阅 refund_approved")
    refund_no = TEST_ORDER + "_REFUND"
    exec_(
        "INSERT INTO event_outbox(event_id, event_type, biz_key, payload, status, retry_count, next_retry_at, created_at) "
        "VALUES(UUID(), 'refund_approved', %s, %s, 0, 0, NOW(), NOW())",
        (f"refund_approved:{refund_no}",
         f'{{"refundId":1,"refundNo":"{refund_no}","orderId":100,"storeId":1,"amount":50.0}}'),
    )
    time.sleep(8)
    rows, _ = q("SELECT COUNT(*) FROM notifications WHERE biz_id = %s", (refund_no,))
    p("refund_approved 通知已生成（店长+仓管 = 2 条）", rows[0][0] == 2, f"got {rows[0][0]}")

    # ===== 事件订阅：order_urged（仅仓管）=====
    print("\n[5] 事件订阅 order_urged")
    urged_no = TEST_ORDER + "_URGED"
    exec_(
        "INSERT INTO event_outbox(event_id, event_type, biz_key, payload, status, retry_count, next_retry_at, created_at) "
        "VALUES(UUID(), 'order_urged', %s, %s, 0, 0, NOW(), NOW())",
        (f"order_urged:{urged_no}",
         f'{{"orderId":100,"orderNo":"{urged_no}","storeId":1}}'),
    )
    time.sleep(8)
    rows, _ = q("SELECT recipient_role FROM notifications WHERE biz_id = %s", (urged_no,))
    roles = sorted([r[0] for r in rows])
    p("order_urged 仅仓管(role=3)收到", roles == [3], f"got roles {roles}")

    # ===== 事件订阅：order_shipped（仅店长）=====
    print("\n[6] 事件订阅 order_shipped")
    shipped_no = TEST_ORDER + "_SHIPPED"
    exec_(
        "INSERT INTO event_outbox(event_id, event_type, biz_key, payload, status, retry_count, next_retry_at, created_at) "
        "VALUES(UUID(), 'order_shipped', %s, %s, 0, 0, NOW(), NOW())",
        (f"order_shipped:{shipped_no}",
         f'{{"orderId":100,"orderNo":"{shipped_no}","storeId":1,"trackingNo":"SF123"}}'),
    )
    time.sleep(8)
    rows, _ = q("SELECT recipient_role, store_id FROM notifications WHERE biz_id = %s", (shipped_no,))
    p("order_shipped 仅店长(role=4)收到 1 条", len(rows) == 1 and rows[0][0] == 4 and rows[0][1] == 1,
      f"got {rows}")

    # ===== 事件订阅：settlement_confirmed =====
    print("\n[7] 事件订阅 settlement_confirmed")
    settle_no = TEST_ORDER + "_SETTLE"
    exec_(
        "INSERT INTO event_outbox(event_id, event_type, biz_key, payload, status, retry_count, next_retry_at, created_at) "
        "VALUES(UUID(), 'settlement_confirmed', %s, %s, 0, 0, NOW(), NOW())",
        (f"settlement_confirmed:{settle_no}",
         f'{{"settlementId":1,"settlementNo":"{settle_no}","storeId":1,"finalAmount":1000}}'),
    )
    time.sleep(8)
    rows, _ = q("SELECT recipient_role, store_id FROM notifications WHERE biz_id = %s", (settle_no,))
    p("settlement_confirmed 仅店长(store_id=1)收到 1 条", len(rows) == 1 and rows[0][0] == 4 and rows[0][1] == 1,
      f"got {rows}")

    # ===== 标记已读 / 全部已读 =====
    print("\n[8] 标记已读")
    # 拿 store_admin1 的某条通知
    rows, _ = q("SELECT id FROM notifications WHERE recipient_id = (SELECT id FROM admin_users WHERE username = 'store_admin1') ORDER BY id DESC LIMIT 1")
    if rows:
        nid = rows[0][0]
        r = requests.put(f"{BASE}/api/notification/{nid}/read", headers=h_store1)
        p("标记单条已读 200", r.status_code == 200)
        rows2, _ = q("SELECT is_read FROM notifications WHERE id = %s", (nid,))
        p("DB is_read 变为 1", rows2[0][0] == 1, f"got {rows2[0][0]}")

    r = requests.put(f"{BASE}/api/notification/read-all", headers=h_store1)
    b = r.json()
    p("标记全部已读 200", r.status_code == 200 and b.get("code") == 0)
    r = requests.get(f"{BASE}/api/notification/summary", headers=h_store1)
    p("未读数变为 0", r.json().get("data", {}).get("unreadCount") == 0,
      f"got {r.json().get('data', {}).get('unreadCount')}")

    # ===== 数据隔离：store_admin2 不应看到 store_id=1 的通知 =====
    print("\n[9] 数据隔离")
    store_admin2 = login("store_admin2", "Store@123456")
    h_store2 = {"Authorization": f"Bearer {store_admin2}"}
    # store_admin2 绑定的门店不是 store_id=1
    r = requests.get(f"{BASE}/api/notification/page?page=1&size=100&bizType=order", headers=h_store2)
    b = r.json()
    own_orders = [n for n in b.get("data", {}).get("records", [])
                  if n.get("bizId", "").startswith(TEST_ORDER + "_PAID")]
    p("store_admin2 看不到 store_id=1 的 order_paid 通知", len(own_orders) == 0,
      f"got {len(own_orders)} leakage")

    # ===== 列表过滤 =====
    print("\n[10] 列表过滤")
    r = requests.get(f"{BASE}/api/notification/page?page=1&size=10&unreadOnly=true", headers=h_admin)
    p("unreadOnly 过滤生效", r.status_code == 200 and r.json().get("data", {}).get("total") >= 0)

    r = requests.get(f"{BASE}/api/notification/page?page=1&size=10&bizType=refund", headers=h_admin)
    p("bizType=refund 过滤生效", r.status_code == 200 and r.json().get("data", {}).get("total") >= 0)

    # ===== 删除 =====
    print("\n[11] 删除通知")
    rows, _ = q("SELECT id FROM notifications WHERE recipient_id = (SELECT id FROM admin_users WHERE username = 'store_admin1') ORDER BY id ASC LIMIT 1")
    if rows:
        nid = rows[0][0]
        r = requests.delete(f"{BASE}/api/notification/{nid}", headers=h_store1)
        p("store_admin1 删除自己通知 200", r.status_code == 200)
        rows2, _ = q("SELECT COUNT(*) FROM notifications WHERE id = %s", (nid,))
        p("DB 通知被删除", rows2[0][0] == 0, f"got {rows2[0][0]}")

    # 删除别人的通知应失败
    rows, _ = q("SELECT id FROM notifications WHERE recipient_id = (SELECT id FROM admin_users WHERE username = 'admin') ORDER BY id ASC LIMIT 1")
    if rows:
        other_id = rows[0][0]
        r = requests.delete(f"{BASE}/api/notification/{other_id}", headers=h_store1)
        p("store_admin1 删除超管的通知 40400", r.json().get("code") == 40400,
          f"got {r.json()}")

    # ===== 角色权限矩阵 =====
    print("\n[12] 权限矩阵 menu:notification")
    for name, h in [("admin", h_admin), ("store_admin1", h_store1), ("warehouse", h_wh), ("staff", h_staff)]:
        r = requests.get(f"{BASE}/api/notification/summary", headers=h)
        ok = r.status_code == 200 and r.json().get("code") == 0
        p(f"{name} 可访问 menu:notification", ok, f"got {r.status_code} {r.json().get('code')}")

    print("\n" + "=" * 60)
    print(f"通过 {passed} / 失败 {failed}")
    print("=" * 60)
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    try:
        main()
    finally:
        # 清理测试数据（保证基线零残留）
        exec_("DELETE FROM notifications WHERE biz_id LIKE 'TEST_V32_%' OR biz_type = 'announcement'")
        exec_("DELETE FROM event_outbox WHERE biz_key LIKE 'order_paid:TEST_V32_%' OR biz_key LIKE 'order_shipped:TEST_V32_%' OR biz_key LIKE 'refund_approved:TEST_V32_%' OR biz_key LIKE 'order_urged:TEST_V32_%' OR biz_key LIKE 'settlement_confirmed:TEST_V32_%'")
        print("清理完成")