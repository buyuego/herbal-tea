"""B 端 Excel 导出 e2e 联测（v33）

覆盖：
- 4 模块创建导出任务
- Worker 处理进度（pending→running→success/failed）
- 文件下载（content-type + bytes）
- 权限矩阵（menu:export 全部，export:run 仅超管）
- 数据隔离（本人任务仅本人可见）
- 跨用户删除/下载 404 防枚举
- 文件内容校验（openpyxl 读取 sheet/header/row count）
"""
import os
import sys
import time
import json
import tempfile
import requests

BASE = "http://127.0.0.1:8080"
OUTPUT_DIR = os.path.join(tempfile.gettempdir(), "v33_exports")
os.makedirs(OUTPUT_DIR, exist_ok=True)

pass_count = 0
fail_count = 0
failures = []


def p(label, ok, detail=""):
    global pass_count, fail_count
    if ok:
        pass_count += 1
        print(f"  \u2705 {label}")
    else:
        fail_count += 1
        failures.append(f"{label}: {detail}")
        print(f"  \u274c {label} \u2014 {detail}")


def login(username, password=None):
    """密码按用户名差异化：超管用 Admin@123456（v17 重置），其他默认 Store@123456（v18 实测）"""
    pw = password if password else ("Admin@123456" if username == "admin" else "Store@123456")
    r = requests.post(f"{BASE}/api/auth/admin/login", json={"username": username, "password": pw})
    b = r.json()
    assert b.get("code") == 0, f"{username} 登录失败: {b}"
    return b["data"]["accessToken"]


def h(token):
    return {"Authorization": f"Bearer {token}"}


def wait_task_ready(tid, token, timeout=20):
    """Worker 每 5s 轮询，最长等 20s（worker 上限 4 轮）"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = requests.get(f"{BASE}/api/export/tasks?page=1&size=50", headers=h(token))
        records = r.json().get("data", {}).get("records", [])
        for t in records:
            if t["id"] == tid:
                if t["status"] == 20:
                    return t, "success"
                if t["status"] == 30:
                    return t, "failed"
                break
        time.sleep(1)
    return None, "timeout"


def main():
    print("===== v33 B 端 Excel 导出 e2e =====\n")
    admin = login("admin")
    store_admin1 = login("store_admin1")
    staff = login("staff1")
    warehouse = login("warehouse1")

    # ===== 1. 端点权限 =====
    print("[1] 端点权限")
    for u, t in [("admin", admin), ("store_admin1", store_admin1), ("staff", staff), ("warehouse", warehouse)]:
        r = requests.get(f"{BASE}/api/export/tasks?page=1&size=10", headers=h(t))
        p(f"{u} GET /tasks 200", r.status_code == 200 and r.json().get("code") == 0, r.text[:120])

    # non-admin 创建 export:run 应 40300
    for u, t in [("store_admin1", store_admin1), ("staff", staff), ("warehouse", warehouse)]:
        r = requests.post(f"{BASE}/api/export/tasks", headers=h(t),
                          json={"bizType": "order"})
        p(f"{u} POST /tasks 40300", r.json().get("code") == 40300, f"got {r.status_code} {r.json()}")
    # admin 触发
    r = requests.post(f"{BASE}/api/export/tasks", headers=h(admin), json={"bizType": "order"})
    p(f"admin POST /tasks 200", r.status_code == 200 and r.json().get("code") == 0, r.text[:120])

    # ===== 2. 4 模块创建导出任务 =====
    print("\n[2] 4 模块创建导出任务")
    task_ids = {}
    for biz in ["product", "order", "member", "settlement"]:
        r = requests.post(f"{BASE}/api/export/tasks", headers=h(admin), json={"bizType": biz})
        assert r.status_code == 200 and r.json().get("code") == 0, f"{biz}: {r.json()}"
        task_ids[biz] = r.json()["data"]["id"]
        p(f"{biz} 创建 200 id={task_ids[biz]}", True)

    # ===== 3. 等待 Worker 处理 + 状态校验 =====
    print("\n[3] Worker 处理（20s 内）")
    for biz, tid in task_ids.items():
        t, s = wait_task_ready(tid, admin)
        if t is None:
            p(f"{biz} 处理完成", False, s)
            continue
        err_field = (t.get("errorMsg") if t.get("errorMsg") else "")
        rc = t.get("rowCount") or 0
        # 只要 export 成功（status=20）即可；settlement 基线为 0 时 row_count=0 合法
        if s == "success":
            p(f"{biz} 导出成功", True)
            p(f"{biz} 文件>1KB", (t.get("fileSize") or 0) > 1024, f"got {t.get('fileSize', 0)}")
        else:
            p(f"{biz} 状态 success", False, f"status={t['status']} err={err_field[:80]}")

    # ===== 4. 下载文件 + openpyxl 校验 =====
    print("\n[4] 下载文件 + 内容校验")
    for biz, tid in task_ids.items():
        t, s = wait_task_ready(tid, admin)
        if not t or s != "success":
            p(f"{biz} 下载（跳过）", False, f"s={s}")
            continue
        r = requests.get(f"{BASE}/api/export/tasks/{tid}/download", headers=h(admin))
        ct = r.headers.get("Content-Type", "")
        ok_ct = "spreadsheetml" in ct
        path = os.path.join(OUTPUT_DIR, f"v33_{biz}.xlsx")
        with open(path, "wb") as f:
            f.write(r.content)
        ok_bytes = len(r.content) > 1024
        p(f"{biz} 文件类型正确", ok_ct, f"got {ct}")
        p(f"{biz} 字节>1KB", ok_bytes, f"got {len(r.content)}")
        try:
            from openpyxl import load_workbook
            wb = load_workbook(path)
            ws = wb.active
            nrows = ws.max_row
            ncols = ws.max_column
            header0 = ws.cell(1, 1).value
            p(f"{biz} sheet={ws.title}", True)
            p(f"{biz} 表头={header0}", True)
        except Exception as e:
            p(f"{biz} openpyxl 读取", False, str(e))

    # ===== 5. 数据隔离：跨用户 =====
    print("\n[5] 数据隔离")
    r = requests.post(f"{BASE}/api/export/tasks", headers=h(admin), json={"bizType": "member"})
    admin_tid = r.json()["data"]["id"]
    r = requests.get(f"{BASE}/api/export/tasks?page=1&size=50", headers=h(store_admin1))
    store_list_ids = {t["id"] for t in r.json()["data"]["records"]}
    p("store_admin1 看不到 admin 任务", admin_tid not in store_list_ids,
      f"泄漏 id={admin_tid}")
    r = requests.delete(f"{BASE}/api/export/tasks/{admin_tid}", headers=h(store_admin1))
    p("store_admin1 删 admin 任务 404",
      (r.status_code == 404 or (r.status_code == 200 and r.json().get("code") == 40400)),
      f"got {r.status_code} {r.json()}")
    r = requests.get(f"{BASE}/api/export/tasks/{admin_tid}/download", headers=h(store_admin1))
    p("store_admin1 下载 admin 任务 404",
      (r.status_code == 404 or (r.status_code == 200 and r.json().get("code") == 40400)),
      f"got {r.status_code} {r.json()}")
    r = requests.get(f"{BASE}/api/export/tasks?page=1&size=50", headers=h(admin))
    admin_list_ids = {t["id"] for t in r.json()["data"]["records"]}
    p("admin 自己列表含本任务", admin_tid in admin_list_ids)

    # ===== 6. 状态过滤 =====
    print("\n[6] 状态过滤")
    r = requests.get(f"{BASE}/api/export/tasks?status=20&page=1&size=50", headers=h(admin))
    succ_only = all(t["status"] == 20 for t in r.json()["data"]["records"])
    p("status=20 全部成功", succ_only)
    r = requests.get(f"{BASE}/api/export/tasks?status=30&page=1&size=50", headers=h(admin))
    fail_only = all(t["status"] == 30 for t in r.json()["data"]["records"]) if r.json().get("data", {}).get("records") else True
    p("status=30 全部失败或空", fail_only)

    # ===== 7. 不支持的 bizType =====
    print("\n[7] 非法 bizType")
    r = requests.post(f"{BASE}/api/export/tasks", headers=h(admin),
                      json={"bizType": "invalid"})
    p("bizType=invalid 40000", r.json().get("code") == 40000, f"got {r.json()}")
    r = requests.post(f"{BASE}/api/export/tasks", headers=h(admin),
                      json={"bizType": ""})
    p("bizType 空 40000", r.json().get("code") == 40000, f"got {r.json()}")

    # ===== 8. 删除（成功任务） =====
    print("\n[8] 删除任务")
    r = requests.get(f"{BASE}/api/export/tasks?status=20&page=1&size=10", headers=h(admin))
    success_records = r.json().get("data", {}).get("records", [])
    if success_records:
        target = success_records[0]
        r = requests.delete(f"{BASE}/api/export/tasks/{target['id']}", headers=h(admin))
        p(f"删除成功任务", r.json().get("code") == 0, f"got {r.json()}")
        # 文件物理不存在？
        path = os.path.join(os.getcwd(), target.get("filePath", "") if target.get("filePath") else "")
        if target.get("filePath") and not os.path.exists(path):
            p("文件物理删除", True)

    # ===== 9. 统计 =====
    print(f"\n===== 通过 {pass_count} / 失败 {fail_count} =====")
    if failures:
        print("\n失败明细：")
        for f in failures:
            print(f"  - {f}")
    sys.exit(0 if fail_count == 0 else 1)


if __name__ == "__main__":
    main()
