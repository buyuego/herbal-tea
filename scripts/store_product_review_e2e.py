# -*- coding: utf-8 -*-
"""
v13 D14 目录变更复核联测（store_product_review_e2e.py）
=======================================================
覆盖：权限门禁 / 跨店隔离 / 确认流 / 驳回流 / 状态机防护 / 幂等与自清理

基线（v9 回归后固定）：admin(1) 超管、store_admin1(2) 店1店主、store_admin2(3) 店2店主、
staff1(4) 店1员工、warehouse1(5) 仓管；store_products 店1(id=2/3)、店2(product3/sku6 残留 dirty=1)。
店2 行 id 不硬编码（v9 回归会重建，动态查询 store_id=2 AND product_id=3 AND sku_id=6）。

用例数据策略（幂等可重跑）：
- 店1 id=2：确认流（置 dirty=1 → confirm → 重复 40900 → 已确认驳回 40900）
- 店1 id=3：驳回流（置 dirty=1 → reject 无 note 40000 → reject 带 note → 重复 40900 → 改主意 confirm）
- 店2 行（动态 id）：跨店 40400（店1 操作）+ 店2 店主 confirm 成功 + 重复 40900
- 清理 = 恢复基线（店1 id=2/3 dirty 置回 0 + review 清空；店2 行 dirty 置回 1 + review 清空）
"""
import pymysql
import requests

BASE = "http://localhost:8080"
DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)

ADMIN_PW = "Admin@123456"
PASS = "Store@123456"

passed, failed = 0, 0


def p(name, resp, expect_code=0):
    global passed, failed
    try:
        body = resp.json()
        code = body.get("code")
    except Exception:
        body, code = None, None
    ok = code == expect_code
    if ok:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}  -> http={resp.status_code} code={code} body={resp.text[:200]}")


def _FakeResp():
    class R:
        status_code = 200
        def json(self): return {"code": 0}
    return R()


def login(username, password=PASS):
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": username, "password": password})
    if r.status_code != 200 or r.json().get("code") != 0:
        return None
    return r.json()["data"]["accessToken"]


def jwt_claims(token):
    import base64, json
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def api(method, path, token=None, json_body=None):
    h = auth(token) if token else {}
    return requests.request(method, f"{BASE}{path}", headers=h, json=json_body, timeout=10)


def db_exec(sql, args=None):
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    cur.execute(sql, args)
    conn.commit()
    conn.close()


def db_one(sql, args=None):
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    cur.execute(sql, args)
    row = cur.fetchone()
    conn.close()
    return row


def reset_baseline():
    """恢复基线：店1 id=2/3 dirty=0、店2 product3/sku6 dirty=1，review 字段全清空"""
    db_exec("""UPDATE store_products SET catalog_dirty=0, review_status=0,
               review_note=NULL, reviewed_at=NULL, reviewed_by=NULL WHERE id IN (2,3)""")
    store2_id = db_one("SELECT id FROM store_products WHERE store_id=2 AND product_id=3 AND sku_id=6")[0]
    db_exec("""UPDATE store_products SET catalog_dirty=1, review_status=0,
               review_note=NULL, reviewed_at=NULL, reviewed_by=NULL WHERE id=%s""", (store2_id,))
    print(f"基线已恢复：店1 id=2/3(dirty=0)、店2 id={store2_id}(dirty=1)")


def store2_row_id():
    row = db_one("SELECT id FROM store_products WHERE store_id=2 AND product_id=3 AND sku_id=6")
    assert row, "店2 product3/sku6 上架记录不存在（需先跑 v9 回归）"
    return row[0]


def main():
    global passed, failed
    print("=== v13 D14 目录变更复核联测 ===")

    # ---------- 0. 前置登录 ----------
    admin_t = login("admin", ADMIN_PW)      # 超管 role=1（无门店）
    sa1_t = login("store_admin1")           # 店1 店主 role=4 sid=1
    sa2_t = login("store_admin2")           # 店2 店主 role=4 sid=2
    staff1_t = login("staff1")              # 店1 员工 role=5
    assert admin_t and sa1_t and sa2_t and staff1_t, "前置登录失败"
    print("前置登录 OK: admin/store_admin1/store_admin2/staff1")

    # ---------- 1. 权限门禁 ----------
    print("\n[1] 权限门禁")
    p("1.1 无令牌复核确认 40100",
      api("POST", "/api/store/products/2/review-confirm"), 40100)
    p("1.2 员工复核确认 40300（无 store:product:review）",
      api("POST", "/api/store/products/2/review-confirm", staff1_t), 40300)
    p("1.3 员工复核驳回 40300",
      api("POST", "/api/store/products/2/review-reject", staff1_t, {"note": "x"}), 40300)
    p("1.4 员工复核列表 40300（无 menu:product）",
      api("GET", "/api/store/pending-catalog-review", staff1_t), 40300)
    p("1.5 超管复核确认 40000（无绑定门店）",
      api("POST", "/api/store/products/2/review-confirm", admin_t), 40000)
    p("1.6 超管复核驳回 40000（无绑定门店）",
      api("POST", "/api/store/products/2/review-reject", admin_t, {"note": "x"}), 40000)
    p("1.7 店主复核列表 200（menu:product 放行）",
      api("GET", "/api/store/pending-catalog-review", sa1_t))

    # ---------- 2. 跨店隔离 ----------
    print("\n[2] 跨店隔离")
    s2id = store2_row_id()
    p("2.1 店1 店主确认店2 商品 40400（不暴露他店存在性）",
      api("POST", f"/api/store/products/{s2id}/review-confirm", sa1_t), 40400)
    p("2.2 店1 店主驳回店2 商品 40400",
      api("POST", f"/api/store/products/{s2id}/review-reject", sa1_t, {"note": "x"}), 40400)
    p("2.3 不存在商品 40400",
      api("POST", "/api/store/products/999999/review-confirm", sa1_t), 40400)

    # ---------- 3. 确认流（店1 id=2） ----------
    print("\n[3] 确认流（店1 id=2）")
    db_exec("UPDATE store_products SET catalog_dirty=1, review_status=0 WHERE id=2")
    r = api("POST", "/api/store/products/2/review-confirm", sa1_t)
    p("3.1 确认成功", r)
    row = db_one("SELECT catalog_dirty, review_status, reviewed_by, reviewed_at IS NOT NULL FROM store_products WHERE id=2")
    p("3.2 落库：dirty=0/status=1/reviewed_by=2/reviewed_at 非空",
      _FakeResp(), 0 if (row == (0, 1, 2, 1)) else -1)
    items = api("GET", "/api/store/pending-catalog-review", sa1_t).json()["data"]
    p("3.3 已确认商品不在复核列表",
      _FakeResp(), 0 if all(i["storeProductId"] != 2 for i in items) else -1)
    p("3.4 重复确认 40900",
      api("POST", "/api/store/products/2/review-confirm", sa1_t), 40900)
    p("3.5 已确认商品不可驳回 40900",
      api("POST", "/api/store/products/2/review-reject", sa1_t, {"note": "后悔了"}), 40900)

    # ---------- 4. 驳回流（店1 id=3） ----------
    print("\n[4] 驳回流（店1 id=3）")
    db_exec("UPDATE store_products SET catalog_dirty=1, review_status=0 WHERE id=3")
    p("4.1 驳回不带原因 40000（@NotBlank）",
      api("POST", "/api/store/products/3/review-reject", sa1_t, {}), 40000)
    p("4.2 驳回带原因成功",
      api("POST", "/api/store/products/3/review-reject", sa1_t, {"note": "定价偏高，申请维持原价"}), )
    row = db_one("SELECT catalog_dirty, review_status, review_note, reviewed_by FROM store_products WHERE id=3")
    p("4.3 落库：dirty 仍=1/status=2/note 落库/reviewed_by=2",
      _FakeResp(), 0 if (row == (1, 2, "定价偏高，申请维持原价", 2)) else -1)
    items = api("GET", "/api/store/pending-catalog-review", sa1_t).json()["data"]
    p("4.4 驳回商品仍在复核列表（角标不清除）",
      _FakeResp(), 0 if any(i["storeProductId"] == 3 and i.get("reviewStatus") == 2 and i.get("reviewNote") for i in items) else -1)
    p("4.5 重复驳回 40900",
      api("POST", "/api/store/products/3/review-reject", sa1_t, {"note": "再驳一次"}), 40900)
    r = api("POST", "/api/store/products/3/review-confirm", sa1_t)
    p("4.6 驳回后改主意确认成功",
      r)
    row = db_one("SELECT catalog_dirty, review_status, review_note FROM store_products WHERE id=3")
    p("4.7 落库：dirty=0/status=1/note 清空",
      _FakeResp(), 0 if (row == (0, 1, None)) else -1)

    # ---------- 5. 店2 闭环（真实残留数据） ----------
    print("\n[5] 店2 闭环（id=%s）" % s2id)
    r = api("POST", f"/api/store/products/{s2id}/review-confirm", sa2_t)
    p("5.1 店2 店主确认本店商品成功", r)
    row = db_one("SELECT catalog_dirty, review_status, reviewed_by FROM store_products WHERE id=%s", (s2id,))
    p("5.2 落库：dirty=0/status=1/reviewed_by=3",
      _FakeResp(), 0 if (row == (0, 1, 3)) else -1)
    p("5.3 重复确认 40900",
      api("POST", f"/api/store/products/{s2id}/review-confirm", sa2_t), 40900)

    # ---------- 6. 清理 ----------
    print("\n[6] 清理")
    reset_baseline()
    print("清理完成：店1 id=2/3 dirty=0、店2 id=13 dirty=1（还原基线，幂等可重跑）")

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    exit(main())
