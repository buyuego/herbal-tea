# -*- coding: utf-8 -*-
"""门店上架 HTTP 全链路联测 v1：
门店登录(sid) → 总部建商品 → 门店上架 → 改价(区间校验) → 上下架 → 列表
→ 越权(店2改店1) → 总部上架拒绝 → 回归(admin/用户登录)
"""
import base64
import json
import sys
import uuid

import requests

BASE = "http://localhost:8080"
ADMIN_PW = "Admin@123456"
STORE_PW = "Store@123456"


def p(label, resp, show_keys=None):
    try:
        body = resp.json()
    except Exception:
        print(f"[{label}] HTTP {resp.status_code} RAW: {resp.text[:300]}")
        return None
    code = body.get("code")
    msg = body.get("message") or body.get("msg")
    data = body.get("data")
    print(f"[{label}] HTTP {resp.status_code} code={code} msg={msg}")
    if data is not None and show_keys:
        if isinstance(data, dict):
            slim = {k: data.get(k) for k in show_keys}
            print(f"    data: {json.dumps(slim, ensure_ascii=False, default=str)[:300]}")
        elif isinstance(data, list):
            print(f"    data: {len(data)} 条")
        else:
            print(f"    data: {data}")
    return body


def jwt_payload(token):
    """解码 JWT payload（不验签，仅读 claims 断言）"""
    part = token.split(".")[1]
    pad = "=" * (-len(part) % 4)
    return json.loads(base64.urlsafe_b64decode(part + pad))


def admin_login(s, username, password):
    r = s.post(f"{BASE}/api/auth/admin/login", json={"username": username, "password": password})
    body = r.json()
    if body.get("code") != 0:
        return None, body
    return body["data"]["accessToken"], body


def main():
    failures = []

    def check(cond, label):
        print(("    ✅ " if cond else "    ❌ ") + label)
        if not cond:
            failures.append(label)

    # 1. 门店管理员1 登录 → JWT 应含 sid=1
    s1 = requests.Session()
    tok1, body = admin_login(s1, "store_admin1", STORE_PW)
    check(tok1 is not None, "store_admin1 登录成功")
    if tok1:
        claims = jwt_payload(tok1)
        check(claims.get("sid") == 1, f"JWT sid=1（实际 {claims.get('sid')}）")
        check(claims.get("type") == "ADMIN", "JWT type=ADMIN")
    s1.headers["Authorization"] = f"Bearer {tok1}"

    # 2. 总部 admin 登录（回归）→ 建新商品（菊花枸杞茶 建议价 100）
    s0 = requests.Session()
    tok0, body = admin_login(s0, "admin", ADMIN_PW)
    check(tok0 is not None, "总部 admin 登录成功（回归）")
    if tok0:
        c = jwt_payload(tok0)
        check("sid" not in c or c.get("sid") is None, "admin JWT 无 sid（总部）")
    s0.headers["Authorization"] = f"Bearer {tok0}"

    r = s0.post(f"{BASE}/api/product/admin/products", json={
        "categoryId": 6, "name": "菊花枸杞茶", "subtitle": "清肝明目",
        "formula": "菊花、枸杞、决明子", "mainImage": "https://img.example.com/juhua.png",
        "suggestedPrice": 100.00, "costPrice": 45.00,
        "skus": [{"skuCode": "SKU-JH-001", "specs": {"规格": "默认规格"},
                  "price": 100.00, "costPrice": 45.00, "stock": 500}],
    })
    body = p("2. 总部建商品(菊花枸杞茶)", r, show_keys=["data"])
    product_id = body.get("data")
    # 幂等：SKU-JH-001 已存在（脚本重复运行）→ 反查复用已有商品，不判失败
    if body.get("code") != 0:
        rp = s0.get(f"{BASE}/api/product/admin/products", params={"keyword": "菊花枸杞茶"})
        recs = (rp.json().get("data") or {}).get("records") or []
        if recs:
            product_id = recs[0].get("id")
            print(f"    ℹ SKU 已存在，复用已有商品 productId={product_id}")
    check(product_id is not None, "总部建商品成功（新建或复用）")
    sku_id = None
    if product_id:
        r = s0.get(f"{BASE}/api/product/admin/products/{product_id}")
        d = r.json().get("data", {})
        skus = d.get("skus") or []
        sku_id = skus[0].get("id") if skus else None
        print(f"    商品 productId={product_id} 首 SKU id={sku_id}")

    # 3. 门店1 上架新 SKU（92 元，在建议价 100 的 80%-120% 内）
    listing_id = None
    if sku_id:
        r = s1.post(f"{BASE}/api/product/store/listings", json={
            "productId": product_id, "skuId": sku_id, "price": 92.00, "dailyQuota": 50})
        body = p("3. 门店1上架菊花枸杞茶", r, show_keys=["data"])
        listing_id = body.get("data")
        # 幂等：本店已上架（脚本重复运行）→ 反查复用 listing
        if body.get("code") != 0:
            rl = s1.get(f"{BASE}/api/product/store/listings")
            for rec in (rl.json().get("data") or []):
                if rec.get("skuId") == sku_id:
                    listing_id = rec.get("id")
                    break
            if listing_id:
                print(f"    ℹ 本店已上架，复用 listingId={listing_id}")
        check(listing_id is not None, "门店1上架成功（新建或复用）")
        print(f"    listingId={listing_id}")

    # 3b. 重复上架同 SKU → 409 冲突
    if sku_id:
        r = s1.post(f"{BASE}/api/product/store/listings", json={
            "productId": product_id, "skuId": sku_id, "price": 90.00})
        body = p("3b. 重复上架同SKU(预期409)", r)
        check(body.get("code") in (40900, 40901), "重复上架被拦截")

    # 4. 门店1 改价 92 → 95（合法区间）
    if listing_id:
        r = s1.put(f"{BASE}/api/product/store/listings/{listing_id}/price",
                   json={"price": 95.00})
        body = p("4. 门店1改价 92→95", r)
        check(body.get("code") == 0, "改价成功")

    # 4b. 越界改价 130（>120%）→ 拒绝
    if listing_id:
        r = s1.put(f"{BASE}/api/product/store/listings/{listing_id}/price",
                   json={"price": 130.00})
        body = p("4b. 越界改价130(预期拒绝)", r)
        check(body.get("code") != 0, "越界改价被拒绝")

    # 5. 门店1 上下架：下架 → 上架
    if listing_id:
        r = s1.put(f"{BASE}/api/product/store/listings/{listing_id}/status", params={"status": 0})
        body = p("5. 门店1下架", r)
        check(body.get("code") == 0, "下架成功")
        r = s1.put(f"{BASE}/api/product/store/listings/{listing_id}/status", params={"status": 1})
        body = p("5b. 门店1重新上架", r)
        check(body.get("code") == 0, "重新上架成功")

    # 6. 门店1 上架列表（应 ≥2 条：手工 INSERT 的红枣枸杞茶 + 菊花枸杞茶）
    r = s1.get(f"{BASE}/api/product/store/listings")
    body = p("6. 门店1上架列表", r)
    records = body.get("data") or []
    check(len(records) >= 2, f"门店1列表 ≥2 条（实际 {len(records)}）")
    for rec in records:
        print(f"      listingId={rec.get('id')} product={rec.get('productName')} "
              f"sku={rec.get('skuCode')} price={rec.get('price')} status={rec.get('status')} "
              f"catalog_dirty={rec.get('catalogDirty')}")

    # 7. 越权：门店2 登录（sid=2）→ 改门店1 的上架记录 → 拒绝
    s2 = requests.Session()
    tok2, body = admin_login(s2, "store_admin2", STORE_PW)
    check(tok2 is not None, "store_admin2 登录成功")
    if tok2:
        claims = jwt_payload(tok2)
        check(claims.get("sid") == 2, f"JWT sid=2（实际 {claims.get('sid')}）")
    s2.headers["Authorization"] = f"Bearer {tok2}"
    if listing_id:
        r = s2.put(f"{BASE}/api/product/store/listings/{listing_id}/price", json={"price": 10.00})
        body = p("7. 门店2改门店1上架价(预期拒绝)", r)
        check(body.get("code") != 0, "越权改价被拒绝")
        r = s2.put(f"{BASE}/api/product/store/listings/{listing_id}/status", params={"status": 0})
        body = p("7b. 门店2下架门店1商品(预期拒绝)", r)
        check(body.get("code") != 0, "越权下架被拒绝")
        # 门店隔离：门店2 列表不应看到门店1 的上架记录（历史跑批可能让门店2 也有上架，故按 SKU 校验隔离）
        r = s2.get(f"{BASE}/api/product/store/listings")
        body = p("7c. 门店2上架列表(不应含门店1商品)", r)
        recs2 = body.get("data") or []
        check(all(rec.get("skuId") != sku_id for rec in recs2),
              f"门店2列表不含门店1的 SKU（门店2 共 {len(recs2)} 条）")

    # 8. 总部 admin 上架 → 拒绝（总部账号不能直接上架）
    if sku_id:
        r = s0.post(f"{BASE}/api/product/store/listings", json={
            "productId": product_id, "skuId": sku_id, "price": 90.00})
        body = p("8. 总部admin上架(预期拒绝)", r)
        check(body.get("code") != 0, "总部上架被拒绝")

    # 9. 回归：C 端微信登录 + 门店列表接口认证
    r = requests.post(f"{BASE}/api/user/wx-login", json={"code": "u001"})
    body = p("9. C端wx-login回归", r)
    check(body.get("code") == 0, "C 端登录回归正常")

    print("\n===== 门店上架全链路联测结果 =====")
    if failures:
        print(f"❌ {len(failures)} 项失败: {failures}")
        sys.exit(1)
    print("✅ 全部通过")
    print(f"新增商品 productId={product_id}, SKU id={sku_id}, 门店1上架 listingId={listing_id}")
    print("账号: store_admin1/Store@123456(店1) store_admin2/Store@123456(店2)")


if __name__ == "__main__":
    main()
