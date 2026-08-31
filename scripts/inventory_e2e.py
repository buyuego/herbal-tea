# -*- coding: utf-8 -*-
"""Product 模块库存管理全链路联测（v25）：总览分页 → 筛选 → 入库/盘点 → 预警阈值 → 流水

场景矩阵：
  主体           操作                             预期
  无令牌         查库存总览                       40100
  staff1        查库存总览                       40300（无 menu:inventory）
  store_admin1  查库存总览                       40300
  staff1        库存调整                         40300（无 inventory:manage）
  warehouse1    查库存总览                       0（有 108 + 216）
  warehouse1    库存调整/设阈值/流水              0
  admin(超管)   全量通过                         0
  校验           总览分页/关键词/分类/状态/仅预警过滤 + 预警行优先排序
  校验           入库(+N)/盘点(-N)：库存落库 + 流水 before/after 一致
  异常           changeType=2 出库 / qty=0 / 库存为负 / SKU 不存在 → 50000
  校验           阈值设置生效（lowStock 计算 + lowStockOnly 过滤）
  异常           阈值负数 → 50000；流水 changeType=9 → 50000
  校验           流水联查 productName/skuCode/operatorName（JOIN 生效）
  清理           删除 IT- 前缀测试流水 + 恢复 SKU 库存/阈值基线
"""
import sys
import time

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "Admin@123456"
TEST_BIZ_PREFIX = "IT-"  # 测试库存流水 biz_no 前缀（清理用）

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


def ok(name, cond, extra=""):
    """非 HTTP 断言（DB / 结构校验）"""
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
    wh_t = login("warehouse1")
    sa1_t = login("store_admin1")
    st1_t = login("staff1")

    s0 = requests.Session()
    s0.headers.update(auth(admin_t))
    sw = requests.Session()
    sw.headers.update(auth(wh_t))
    s1 = requests.Session()
    s1.headers.update(auth(sa1_t))
    s5 = requests.Session()
    s5.headers.update(auth(st1_t))

    # 取一个启用中的 SKU 作为测试靶子
    row = db_one("""SELECT s.id, s.sku_code, s.stock, s.alert_stock, p.name
                    FROM product_skus s JOIN products p ON p.id = s.product_id
                    WHERE s.status = 1 ORDER BY s.id LIMIT 1""")
    assert row, "无可用 SKU，请先初始化商品数据"
    sku_id, sku_code, base_stock, base_alert, product_name = row
    print(f"  ℹ 测试 SKU id={sku_id} code={sku_code} 库存={base_stock} 阈值={base_alert}（{product_name}）")

    total_sku = db_one("SELECT COUNT(*) FROM product_skus")[0]
    biz_in = f"{TEST_BIZ_PREFIX}IN-{int(time.time())}"
    biz_adj = f"{TEST_BIZ_PREFIX}ADJ-{int(time.time())}"

    print("===== 1. 权限门禁 =====")
    r = requests.get(f"{BASE}/api/product/inventory/skus")
    p("1.1 无令牌查库存总览 → 40100", r, 40100)
    r = s5.get(f"{BASE}/api/product/inventory/skus")
    p("1.2 店铺员工查总览 → 40300（无 menu:inventory）", r, 40300)
    r = s1.get(f"{BASE}/api/product/inventory/skus")
    p("1.3 门店管理员查总览 → 40300", r, 40300)
    r = s5.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": sku_id, "changeType": 1, "changeQty": 1})
    p("1.4 店铺员工调整库存 → 40300（无 inventory:manage）", r, 40300)
    r = s1.put(f"{BASE}/api/product/inventory/skus/{sku_id}/alert", params={"alertStock": 5})
    p("1.5 门店管理员设阈值 → 40300", r, 40300)
    r = sw.get(f"{BASE}/api/product/inventory/skus")
    body = p("1.6 仓管查总览放行（108 + 216）", r, 0)
    assert "records" in body["data"], f"分页结构异常: {body}"
    ok("1.7 分页结构正常（records/total）", body["data"]["total"] == total_sku,
       f"total={body['data']['total']} / SKU 总数 {total_sku}")

    print("\n===== 2. 总览筛选与排序 =====")
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"keyword": sku_code})
    body = p("2.1 关键词（SKU 编码）过滤", r, 0)
    ok("2.2 命中唯一目标 SKU",
       len(body["data"]["records"]) == 1 and body["data"]["records"][0]["skuId"] == sku_id)
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"keyword": product_name})
    body = p("2.3 关键词（商品名）过滤", r, 0)
    ok("2.4 商品名命中非空", body["data"]["total"] >= 1, f"total={body['data']['total']}")
    cat = db_one("SELECT category_id FROM products WHERE id=(SELECT product_id FROM product_skus WHERE id=%s)",
                 (sku_id,))
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"categoryId": cat[0]})
    body = p(f"2.5 分类过滤（categoryId={cat[0]}）", r, 0)
    ok("2.6 分类过滤全部命中", all(x["categoryId"] == cat[0] for x in body["data"]["records"]))
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"status": 1})
    body = p("2.7 status=1 过滤（仅启用 SKU）", r, 0)
    ok("2.8 状态过滤生效", all(x["status"] == 1 for x in body["data"]["records"]))
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"size": 3, "page": 1})
    body = p("2.9 分页 size=3", r, 0)
    ok("2.10 每页 3 条", len(body["data"]["records"]) == min(3, total_sku),
       f"实际 {len(body['data']['records'])} 条 / SKU 总数 {total_sku}")
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"size": 999})
    body = p("2.11 size 越界被钳到 100", r, 0)
    ok("2.12 单页上限 100", body["data"]["size"] == 100, f"size={body['data']['size']}")

    print("\n===== 3. 库存调整（入库 / 盘点） =====")
    r = sw.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": sku_id, "changeType": 1, "changeQty": 10,
                      "bizNo": biz_in, "note": "v25 联测入库"})
    p("3.1 仓管入库 +10", r, 0)
    stock_after_in = db_one("SELECT stock FROM product_skus WHERE id=%s", (sku_id,))[0]
    ok("3.2 DB 库存 +10", stock_after_in == base_stock + 10, f"{base_stock} → {stock_after_in}")
    rec = db_one("""SELECT change_type, change_qty, before_stock, after_stock, biz_no, note, operator_id
                    FROM inventory_records WHERE biz_no=%s""", (biz_in,))
    ok("3.3 流水落库 type=1 / +10 / before-after 正确",
       rec and rec[0] == 1 and rec[1] == 10 and rec[2] == base_stock
       and rec[3] == stock_after_in and rec[4] == biz_in and rec[5] == "v25 联测入库",
       f"流水={rec}")
    ok("3.4 流水记录操作人（仓管 admin_id）", rec and rec[6] is not None, f"operatorId={rec[6] if rec else None}")

    r = sw.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": sku_id, "changeType": 3, "changeQty": -4,
                      "bizNo": biz_adj, "note": "v25 联测盘亏"})
    p("3.5 盘点调整 -4", r, 0)
    stock_after_adj = db_one("SELECT stock FROM product_skus WHERE id=%s", (sku_id,))[0]
    ok("3.6 DB 库存 -4", stock_after_adj == stock_after_in - 4, f"{stock_after_in} → {stock_after_adj}")
    rec2 = db_one("""SELECT change_type, change_qty, before_stock, after_stock
                     FROM inventory_records WHERE biz_no=%s""", (biz_adj,))
    ok("3.7 盘点流水 type=3 / -4 / before-after 正确",
       rec2 and rec2[0] == 3 and rec2[1] == -4 and rec2[2] == stock_after_in
       and rec2[3] == stock_after_adj, f"流水={rec2}")

    print("\n===== 4. 调整参数校验 =====")
    r = sw.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": sku_id, "changeType": 2, "changeQty": 1})
    p("4.1 changeType=2 出库 → 50000（接口仅支持 1/3）", r, 50000)
    r = sw.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": sku_id, "changeType": 3, "changeQty": 0})
    p("4.2 变动数量 0 → 50000", r, 50000)
    r = sw.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": sku_id, "changeType": 3, "changeQty": -999999})
    p("4.3 调整后库存为负 → 50000", r, 50000)
    r = sw.post(f"{BASE}/api/product/inventory/adjust",
                json={"skuId": 999999, "changeType": 1, "changeQty": 1})
    p("4.4 SKU 不存在 → 50000", r, 50000)
    r = sw.post(f"{BASE}/api/product/inventory/adjust", json={"changeType": 1, "changeQty": 1})
    p("4.5 缺 skuId（参数校验）→ 40000", r, 40000)
    stock_now = db_one("SELECT stock FROM product_skus WHERE id=%s", (sku_id,))[0]
    ok("4.6 异常调整未变更库存", stock_now == stock_after_adj, f"stock={stock_now}")

    print("\n===== 5. 低库存预警 =====")
    r = sw.put(f"{BASE}/api/product/inventory/skus/{sku_id}/alert", params={"alertStock": 999999})
    p("5.1 仓管设置超高阈值 999999", r, 0)
    ok("5.2 DB 阈值落库", db_one("SELECT alert_stock FROM product_skus WHERE id=%s", (sku_id,))[0] == 999999)
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"skuId": sku_id, "keyword": sku_code})
    body = p("5.3 总览查询目标 SKU", r, 0)
    target = body["data"]["records"][0]
    ok("5.4 lowStock=true（stock <= alert_stock）", target["lowStock"] is True,
       f"stock={target['stock']} alert={target['alertStock']}")
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"lowStockOnly": 1, "size": 100})
    body = p("5.5 lowStockOnly=1 过滤", r, 0)
    ok("5.6 过滤结果全部为预警行", all(x["lowStock"] for x in body["data"]["records"]))
    ok("5.7 预警行优先排序（首行 lowStock=true）",
       bool(body["data"]["records"]) and body["data"]["records"][0]["lowStock"] is True)
    ok("5.8 目标 SKU 出现在预警列表",
       any(x["skuId"] == sku_id for x in body["data"]["records"]))
    r = sw.put(f"{BASE}/api/product/inventory/skus/{sku_id}/alert", params={"alertStock": 0})
    p("5.9 阈值设 0（库存 > 0 不再预警）", r, 0)
    r = s0.get(f"{BASE}/api/product/inventory/skus", params={"keyword": sku_code})
    body = p("5.10 复查目标 SKU", r, 0)
    ok("5.11 lowStock=false", body["data"]["records"][0]["lowStock"] is False,
       f"stock={body['data']['records'][0]['stock']} alert=0")
    r = sw.put(f"{BASE}/api/product/inventory/skus/{sku_id}/alert", params={"alertStock": -1})
    p("5.12 阈值为负 → 50000", r, 50000)
    r = sw.put(f"{BASE}/api/product/inventory/skus/999999/alert", params={"alertStock": 5})
    p("5.13 SKU 不存在 → 50000", r, 50000)

    print("\n===== 6. 库存流水 =====")
    r = sw.get(f"{BASE}/api/product/inventory/records", params={"skuId": sku_id})
    body = p("6.1 按 SKU 查流水（inventory:manage）", r, 0)
    recs = body["data"]["records"]
    ok("6.2 全部命中目标 SKU", all(x["skuId"] == sku_id for x in recs))
    ok("6.3 联查商品名/SKU 编码（JOIN 生效）",
       all(x["productName"] == product_name and x["skuCode"] == sku_code for x in recs),
       f"首行 productName={recs[0]['productName'] if recs else None}")
    ok("6.4 联查操作人姓名（JOIN admin_users）",
       all(x["operatorName"] for x in recs if x["operatorId"]),
       f"首行 operatorName={recs[0].get('operatorName') if recs else None}")
    r = sw.get(f"{BASE}/api/product/inventory/records", params={"changeType": 1})
    body = p("6.5 changeType=1 过滤（仅入库）", r, 0)
    ok("6.6 类型过滤生效", all(x["changeType"] == 1 for x in body["data"]["records"]))
    r = sw.get(f"{BASE}/api/product/inventory/records", params={"bizNo": biz_in})
    body = p("6.7 bizNo 过滤", r, 0)
    ok("6.8 单号过滤命中 1 条",
       body["data"]["total"] == 1 and body["data"]["records"][0]["bizNo"] == biz_in)
    r = sw.get(f"{BASE}/api/product/inventory/records", params={"changeType": 9})
    p("6.9 changeType=9 → 50000", r, 50000)
    r = s5.get(f"{BASE}/api/product/inventory/records")
    p("6.10 店铺员工查流水 → 40300", r, 40300)

    print("\n===== 7. 数据清理与基线校验 =====")
    n = db_exec("DELETE FROM inventory_records WHERE biz_no LIKE %s", (f"{TEST_BIZ_PREFIX}%",))
    print(f"  ℹ 清理测试流水（{TEST_BIZ_PREFIX} 前缀）: {n} 行")
    db_exec("UPDATE product_skus SET stock=%s, alert_stock=%s WHERE id=%s",
            (base_stock, base_alert, sku_id))
    row = db_one("SELECT stock, alert_stock FROM product_skus WHERE id=%s", (sku_id,))
    ok("7.1 SKU 库存/阈值恢复基线", row == (base_stock, base_alert), f"stock={row[0]} alert={row[1]}")
    row = db_one("SELECT COUNT(*) FROM inventory_records WHERE biz_no LIKE %s", (f"{TEST_BIZ_PREFIX}%",))
    ok("7.2 测试流水已清空", row[0] == 0, f"残留 {row[0]} 行")
    row = db_one("SELECT COUNT(*) FROM product_skus")
    ok("7.3 SKU 总数不变", row[0] == total_sku, f"{row[0]} / {total_sku}")
    row = db_one("""SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA='herbal_tea' AND TABLE_NAME='product_skus'
                      AND COLUMN_NAME='alert_stock'""")
    ok("7.4 V11 迁移列 alert_stock 存在", row[0] == 1)
    row = db_one("SELECT COUNT(*) FROM permissions WHERE code='menu:inventory'")
    ok("7.5 权限码 menu:inventory 存在", row[0] == 1)
    row = db_one("""SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id
                    WHERE p.code='menu:inventory' AND rp.role_id IN (1,3)""")
    ok("7.6 超管/仓管持有 menu:inventory", row[0] == 2, f"授权数={row[0]}")

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    finally:
        # 兜底清理：任何断言失败也要把测试痕迹清干净
        try:
            db_exec("DELETE FROM inventory_records WHERE biz_no LIKE %s", (f"{TEST_BIZ_PREFIX}%",))
        except Exception as e:
            print(f"  ⚠ 兜底清理失败: {e}")
