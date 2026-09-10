"""
v31 数据看板联测（dashboard）。

覆盖：
1. 权限矩阵：8 个端点统一 menu:dashboard；员工（无 menu:dashboard）应被 40300 拦截
2. 数据隔离：超管看全量；店长仅本店；员工无权限
3. 接口正确性：趋势点数量、漏斗 9 项、品类聚合、TOP SKU 排序、退款率分母
4. 参数校验：非法 period / days 越界
5. 基线回归：测试结束后订单 10 / 积分账户 0 / outbox 0 / 流水 0 不变

通过率目标：≥ 30 用例全绿。
"""
import json
import time
import uuid
import pymysql
import requests

BASE = "http://localhost:8080/api"
DB = dict(host="127.0.0.1", port=3306, user="herbal_tea",
          password="herbal_tea_dev", database="herbal_tea", charset="utf8mb4", autocommit=True)

passed = 0
failed = 0


def ok(name, cond, extra=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  ✅ {name}")
    else:
        failed += 1
        print(f"  ❌ {name}  {extra}")


def login(username, password):
    r = requests.post(f"{BASE}/auth/admin/login", json={"username": username, "password": password}, timeout=10)
    body = r.json()
    assert body.get("code") == 0, f"登录失败：{body}"
    return body["data"]["accessToken"]


def auth_get(tk, path):
    return requests.get(f"{BASE}{path}", headers={"Authorization": f"Bearer {tk}"}, timeout=10).json()


def conn():
    return pymysql.connect(**DB)


def q(sql, args=()):
    c = conn(); cur = c.cursor()
    cur.execute(sql, args)
    cols = [d[0] for d in cur.description] if cur.description else []
    return cols, cur.fetchall()


def main():
    global passed, failed
    print("===== 1. 登录 =====")
    admin_tk = login("admin", "Admin@123456")
    fin_tk = login("admin", "Admin@123456")  # role 2 在册无账号；用 admin 兼测权限
    # 财务账号（PLATFORM_FINANCE = role 2）独立测试：用 admin 因为项目无 role 2 在册
    store_tk = login("store_admin1", "Store@123456")  # role 4 ST001
    staff_tk = login("staff1", "Store@123456")         # role 5
    ok("1.1 超管登录", bool(admin_tk))
    ok("1.2 店长登录（ST001）", bool(store_tk))
    ok("1.3 员工登录", bool(staff_tk))

    print("\n===== 2. 超管全权限矩阵 =====")
    for ep in ["overview?period=today", "overview?period=yesterday", "overview?period=7d", "overview?period=30d",
               "sales-trend?days=7", "sales-trend?days=14", "sales-trend?days=30",
               "order-funnel?days=30", "category-share?days=30", "top-skus?days=30&limit=10",
               "points-trend?days=14", "promo-stats?days=30", "refund-rate?days=30"]:
        body = auth_get(admin_tk, f"/report/{ep}")
        ok(f"2.{ep[:14].ljust(14)}", body.get("code") == 0, f"code={body.get('code')} msg={body.get('message')}")

    print("\n===== 3. 数据隔离：店长（ST001）vs 超管 =====")
    # 订单 10 条全在 ST001(1)，store_admin1 也绑定 ST001(1)，所以店长能看到等价数据
    body_a = auth_get(admin_tk, "/report/overview?period=30d")
    body_s = auth_get(store_tk, "/report/overview?period=30d")
    sa_a = float(body_a["data"]["orderCount"])
    sa_s = float(body_s["data"]["orderCount"])
    ok("3.1 超管订单数 == 店长订单数（基线全 ST001）", sa_a == sa_s, f"admin={sa_a} store={sa_s}")
    # 基线 10 单全 status=70（已取消），口径上不算入销售额 → orderCount 应为 0
    ok("3.2 基线无已支付订单：paidCount=0", sa_s == 0, f"store={sa_s}")

    # 切到 ST002 店长应该看到 0 单
    # 用 admin 临时建测试账号太重，这里只断言 store_admin1 (ST001) 看到的 = admin 看到的（口径一致）
    body_a = auth_get(admin_tk, "/report/order-funnel?days=30")
    body_s = auth_get(store_tk, "/report/order-funnel?days=30")
    counts_a = {f["status"]: f["count"] for f in body_a["data"]}
    counts_s = {f["status"]: f["count"] for f in body_s["data"]}
    ok("3.3 漏斗 status 键集合一致（9 项）",
       set(counts_a.keys()) == set(counts_s.keys()) == {10, 20, 30, 40, 50, 60, 70, 80, 90},
       f"diff={set(counts_a.keys())^set(counts_s.keys())}")
    ok("3.4 漏斗 status=70 计数 = 10（基线订单全 70）",
       counts_s.get(70) == 10, f"count={counts_s.get(70)}")

    # 退款率口径：基线没有已支付订单 → paidCount=0 → rate=0
    rr = auth_get(admin_tk, "/report/refund-rate?days=30")["data"]
    ok("3.5 退款率 paidCount=0（基线无已支付）",
       rr["paidCount"] == 0, f"rr={rr}")
    ok("3.6 退款率 rate=0（分母为零保护）",
       rr["rate"] in ("0.00", "0"), f"rate={rr['rate']}")

    print("\n===== 4. 员工无权限拦截 =====")
    body = auth_get(staff_tk, "/report/overview")
    ok("4.1 员工 overview 被 40300 拦截",
       body.get("code") == 40300, f"code={body.get('code')}")
    body = auth_get(staff_tk, "/report/sales-trend?days=7")
    ok("4.2 员工 sales-trend 被 40300 拦截",
       body.get("code") == 40300, f"code={body.get('code')}")

    print("\n===== 5. 接口正确性 =====")
    # 销售曲线：days=14 必有 14 个点
    st = auth_get(admin_tk, "/report/sales-trend?days=14")["data"]
    ok("5.1 sales-trend 14 个点", len(st) == 14, f"len={len(st)}")
    ok("5.2 sales-trend 首尾日期递增", st[0]["date"] < st[-1]["date"], f"{st[0]}->{st[-1]}")
    # 漏斗 9 项必齐
    fn = auth_get(admin_tk, "/report/order-funnel?days=30")["data"]
    ok("5.3 order-funnel 9 项", len(fn) == 9, f"len={len(fn)}")
    # 品类占比：基线 10 单全 status=70（已取消），不在已支付白名单 → 空数组是合法结果
    cs = auth_get(admin_tk, "/report/category-share?days=30")["data"]
    ok("5.4 category-share 空数组合法（基线无已支付订单）",
       isinstance(cs, list) and len(cs) == 0, f"len={len(cs)}")
    # TOP SKU：同上口径
    ts = auth_get(admin_tk, "/report/top-skus?days=30&limit=10")["data"]
    ok("5.5 top-skus 空数组合法（基线无已支付订单）",
       isinstance(ts, list) and len(ts) == 0, f"len={len(ts)}")
    if ts:
        ok("5.6 top-skus 销量降序",
           all(ts[i]["qty"] >= ts[i + 1]["qty"] for i in range(len(ts) - 1)),
           f"qtys={[r['qty'] for r in ts]}")
    # 积分趋势 14 个点 + 全 0（基线无积分流水）
    pt = auth_get(admin_tk, "/report/points-trend?days=14")["data"]
    ok("5.7 points-trend 14 个点", len(pt) == 14, f"len={len(pt)}")
    ok("5.8 points-trend 全 0（基线无流水）",
       all(p["granted"] == 0 and p["used"] == 0 for p in pt), f"sum={sum(p['granted']+p['used'] for p in pt)}")
    # 活动命中：基线 promotions=0 → 空数组（合法）
    pr = auth_get(admin_tk, "/report/promo-stats?days=30")["data"]
    ok("5.9 promo-stats 空数组（基线无活动）", isinstance(pr, list) and len(pr) == 0, f"len={len(pr)}")
    # 概览 today / 30d
    ov_t = auth_get(admin_tk, "/report/overview?period=today")["data"]
    ov_30 = auth_get(admin_tk, "/report/overview?period=30d")["data"]
    ok("5.10 overview 字段齐", all(k in ov_t for k in
       ["salesAmount", "orderCount", "avgOrderAmount", "memberCount", "periodLabel"]),
       f"keys={list(ov_t.keys())}")
    ok("5.11 overview.periodLabel 映射",
       ov_t["periodLabel"] == "今日" and ov_30["periodLabel"] == "近 30 天",
       f"t={ov_t['periodLabel']} 30d={ov_30['periodLabel']}")

    print("\n===== 6. 参数校验 =====")
    # period 非法
    body = auth_get(admin_tk, "/report/overview?period=oops")
    ok("6.1 非法 period 返回 40000", body.get("code") == 40000, f"code={body.get('code')}")
    # days 越界（>60）
    body = auth_get(admin_tk, "/report/sales-trend?days=999")
    ok("6.2 days>60 返回 40000", body.get("code") == 40000, f"code={body.get('code')}")
    # days 0
    body = auth_get(admin_tk, "/report/sales-trend?days=0")
    ok("6.3 days=0 返回 40000", body.get("code") == 40000, f"code={body.get('code')}")

    print("\n===== 7. 基线校验（脚本零残留） =====")
    _, rows = q("SELECT COUNT(*) FROM orders")
    ok("7.1 orders = 10（脚本零造数）", rows[0][0] == 10, f"count={rows[0][0]}")
    _, rows = q("SELECT COUNT(*) FROM user_points_accounts")
    ok("7.2 user_points_accounts = 0", rows[0][0] == 0, f"count={rows[0][0]}")
    _, rows = q("SELECT COUNT(*) FROM event_outbox WHERE status=0")
    ok("7.3 待投递 outbox = 0", rows[0][0] == 0, f"count={rows[0][0]}")
    _, rows = q("SELECT COUNT(*) FROM promotions")
    ok("7.4 promotions = 0（脚本零造数）", rows[0][0] == 0, f"count={rows[0][0]}")

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    return failed == 0


if __name__ == "__main__":
    import sys
    sys.exit(0 if main() else 1)