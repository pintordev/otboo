"""
GitHub Issue/PR 필수 필드 검증.

notion_progress_sync.py와 같은 주기(같은 workflow)로 Issue/PR을 훑어 필수 필드 누락을 점검한다.
주기적 검증이라 open만이 아니라 closed/merged 포함 전체(state=all)가 대상이다. Notion 카드는 건드리지
않는다 — GitHub 쪽 원장 데이터 자체를 깨끗하게 유지하는 목적.

필수 필드:
  Issue: Assignees, Labels(타입 라벨 1개 + 도메인 라벨 1개), Type(네이티브 Issue Type),
         Milestone, Projects(Priority, Start date, Category)
  PR:    Reviewers(요청 중이거나 리뷰 이력 있음), Assignees, Labels

- 미설정 → 작성자(이슈/PR 둘 다 author)에게 DM, 매핑 없으면 웹훅 — 한 실행에서 이슈+PR findings를 합쳐
  수신자 1명당 메시지 1건(웹훅도 전체 통틀어 1건)만 발송
- 라벨 과다설정(타입/도메인 2개 이상)은 이슈든 PR이든 건드리지 않음(허용) — 삭제 안 함
- PR에 Milestone이나 Projects 아이템이 붙어있으면 완전히 제거 — Milestone은 REST PATCH로 null 처리,
  Projects는 아이템 자체를 `deleteProjectV2Item`으로 삭제

필요 시크릿: notion_progress_sync.py와 동일(PROJECTS_PAT, DISCORD_BOT_TOKEN, DISCORD_USER_MAP,
DISCORD_WEBHOOK_URL) + PR 마일스톤 제거를 위한 GH_TOKEN에 pull-requests:write 권한 필요(PATCH 대상이
PR이면 REST 경로가 /issues/{number}여도 issues 권한이 아니라 pull-requests 권한이 적용됨).
"""

import json
import os
import subprocess
import sys

import requests

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import notion_backfill_match as bf
import notion_progress_sync as s

ORG = "sb11-code-rangers"
PROJECT_NUMBER = 1
PROJECTS_PAT = os.environ.get("PROJECTS_PAT")
PROJECT_ID = "PVT_kwDOEk0vfc4BeYM2"
REQUEST_TIMEOUT = 30


def run_gh(args):
    try:
        return subprocess.run(
            ["gh", *args], capture_output=True, text=True, check=True, timeout=REQUEST_TIMEOUT,
        )
    except subprocess.CalledProcessError as exc:
        raise RuntimeError(f"gh 호출 실패({args}): {exc.stderr.strip()}") from exc


def fetch_issues():
    proc = run_gh(
        [
            "api", f"repos/{bf.REPO}/issues?state=all&per_page=100", "--paginate",
            "--jq", ".[] | select(.pull_request == null)",
        ],
    )
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def fetch_prs():
    proc = run_gh(
        ["api", f"repos/{bf.REPO}/pulls?state=all&per_page=100", "--paginate", "--jq", ".[]"],
    )
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def fetch_pr_has_reviews(number):
    proc = run_gh(["api", f"repos/{bf.REPO}/pulls/{number}/reviews", "--jq", "length"])
    return int(proc.stdout.strip() or "0") > 0


def graphql(query, variables):
    headers = {"Authorization": f"Bearer {PROJECTS_PAT}", "Content-Type": "application/json"}
    resp = requests.post(
        "https://api.github.com/graphql", headers=headers,
        json={"query": query, "variables": variables}, timeout=REQUEST_TIMEOUT,
    )
    resp.raise_for_status()
    data = resp.json()
    if "errors" in data:
        raise RuntimeError(f"GraphQL 오류: {data['errors']}")
    return data["data"]


FIELD_VALUE_FRAGMENT = """
... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
... on ProjectV2ItemFieldDateValue { date field { ... on ProjectV2FieldCommon { name } } }
... on ProjectV2ItemFieldMultiSelectValue { value field { ... on ProjectV2FieldCommon { name } } }
"""

MORE_FIELD_VALUES_QUERY = f"""
query($item: ID!, $cursor: String) {{
  node(id: $item) {{
    ... on ProjectV2Item {{
      fieldValues(first: 20, after: $cursor) {{
        pageInfo {{ hasNextPage endCursor }}
        nodes {{ __typename {FIELD_VALUE_FRAGMENT} }}
      }}
    }}
  }}
}}
"""


def collect_field_values(item_id, field_values):
    nodes = list(field_values["nodes"])
    page_info = field_values["pageInfo"]
    while page_info["hasNextPage"]:
        data = graphql(MORE_FIELD_VALUES_QUERY, {"item": item_id, "cursor": page_info["endCursor"]})
        more = data["node"]["fieldValues"]
        nodes.extend(more["nodes"])
        page_info = more["pageInfo"]
    return nodes


def extract_priority_start_category(field_value_nodes):
    priority, start_date, category = None, None, None
    for fv in field_value_nodes:
        field_name = (fv.get("field") or {}).get("name")
        if field_name == "Priority":
            priority = fv.get("name")
        elif field_name == "Start date":
            start_date = fv.get("date")
        elif field_name == "Category":
            category = fv.get("value")
    return {"priority": priority, "start_date": start_date, "category": category}


def fetch_issue_project_fields():
    query = f"""
    query($org: String!, $number: Int!, $cursor: String) {{
      organization(login: $org) {{
        projectV2(number: $number) {{
          items(first: 100, after: $cursor) {{
            pageInfo {{ hasNextPage endCursor }}
            nodes {{
              id
              content {{ __typename ... on Issue {{ number }} }}
              fieldValues(first: 20) {{
                pageInfo {{ hasNextPage endCursor }}
                nodes {{ __typename {FIELD_VALUE_FRAGMENT} }}
              }}
            }}
          }}
        }}
      }}
    }}
    """
    fields, cursor = {}, None
    while True:
        data = graphql(query, {"org": ORG, "number": PROJECT_NUMBER, "cursor": cursor})
        page = data["organization"]["projectV2"]["items"]
        for node in page["nodes"]:
            content = node.get("content") or {}
            if content.get("__typename") != "Issue":
                continue
            number = content.get("number")
            if number is None:
                continue
            field_value_nodes = collect_field_values(node["id"], node["fieldValues"])
            fields[number] = extract_priority_start_category(field_value_nodes)
        if not page["pageInfo"]["hasNextPage"]:
            break
        cursor = page["pageInfo"]["endCursor"]
    return fields


def fetch_pr_project_item_ids():
    query = """
    query($org: String!, $number: Int!, $cursor: String) {
      organization(login: $org) {
        projectV2(number: $number) {
          items(first: 100, after: $cursor) {
            pageInfo { hasNextPage endCursor }
            nodes {
              id
              content { __typename ... on PullRequest { number } }
            }
          }
        }
      }
    }
    """
    items, cursor = {}, None
    while True:
        data = graphql(query, {"org": ORG, "number": PROJECT_NUMBER, "cursor": cursor})
        page = data["organization"]["projectV2"]["items"]
        for node in page["nodes"]:
            content = node.get("content") or {}
            if content.get("__typename") != "PullRequest":
                continue
            number = content.get("number")
            if number is not None:
                items[number] = node["id"]
        if not page["pageInfo"]["hasNextPage"]:
            break
        cursor = page["pageInfo"]["endCursor"]
    return items


def delete_project_item(item_id):
    mutation = """
    mutation($project: ID!, $item: ID!) {
      deleteProjectV2Item(input: {projectId: $project, itemId: $item}) {
        deletedItemId
      }
    }
    """
    graphql(mutation, {"project": PROJECT_ID, "item": item_id})


def clear_pr_milestone(pr_number):
    proc = subprocess.run(
        ["gh", "api", "-X", "PATCH", f"repos/{bf.REPO}/issues/{pr_number}", "--input", "-"],
        input='{"milestone": null}', capture_output=True, text=True, timeout=REQUEST_TIMEOUT,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"PR #{pr_number} 마일스톤 제거 실패: {proc.stderr.strip()}")


def clean_pr_issue_only_fields(pr, pr_project_item_ids):
    if pr.get("milestone"):
        clear_pr_milestone(pr["number"])
        print(f"cleared milestone on PR #{pr['number']}")
    item_id = pr_project_item_ids.get(pr["number"])
    if item_id:
        delete_project_item(item_id)
        print(f"removed PR #{pr['number']} from project board")


def check_issue(issue, project_fields):
    missing = []
    if not issue.get("assignees"):
        missing.append("담당자(Assignees)")

    labels = {l["name"].lower() for l in issue["labels"]}
    if not any(l in bf.TYPE_LABELS for l in labels):
        missing.append("타입 라벨")
    if not any(l in bf.DOMAIN_LABELS for l in labels):
        missing.append("도메인 라벨")

    if not issue.get("type"):
        missing.append("Issue Type")
    if not issue.get("milestone"):
        missing.append("마일스톤")

    proj = project_fields.get(issue["number"], {})
    if not proj.get("priority"):
        missing.append("Projects: Priority")
    if not proj.get("start_date"):
        missing.append("Projects: Start date")
    if not proj.get("category"):
        missing.append("Projects: Category")

    return missing


def check_pr(pr, has_reviews):
    missing = []
    if not pr.get("requested_reviewers") and not has_reviews:
        missing.append("리뷰어(Reviewers)")
    if not pr.get("assignees"):
        missing.append("담당자(Assignees)")
    if not pr.get("labels"):
        missing.append("라벨(Labels)")
    return missing


def add_finding(recipients, login, section, line):
    key = login if (login and login in s.DISCORD_USER_MAP) else None
    recipients.setdefault(key, {}).setdefault(section, []).append(line)


def build_message(sections):
    parts = []
    if sections.get("이슈"):
        parts.append("[이슈 필수 필드 점검] 아래 이슈에 빠진 필드가 있습니다:\n" + "\n".join(sections["이슈"]))
    if sections.get("PR"):
        parts.append("[PR 필수 필드 점검] 아래 PR에 빠진 필드가 있습니다:\n" + "\n".join(sections["PR"]))
    return "\n\n".join(parts)


def send_all(recipients):
    for key, sections in recipients.items():
        message = build_message(sections)
        try:
            if key is None:
                s.send_webhook(message)
            else:
                entry = s.DISCORD_USER_MAP[key]
                s.send_dm_to_discord_id(entry["discord_id"], message)
        except requests.RequestException as exc:
            status = exc.response.status_code if exc.response is not None else None
            print(f"FAIL notify {key or '(webhook)'}: {type(exc).__name__} (status={status})")
        except KeyError as exc:
            print(f"FAIL notify {key or '(webhook)'}: missing key {exc}")


def main():
    if not PROJECTS_PAT:
        raise RuntimeError("PROJECTS_PAT이 필요합니다(Projects Priority/Start date/Category 조회·정리에 사용)")

    recipients = {}

    issues = fetch_issues()
    project_fields = fetch_issue_project_fields()
    issue_flagged = 0
    for issue in issues:
        missing = check_issue(issue, project_fields)
        if not missing:
            continue
        issue_flagged += 1
        login = issue["user"]["login"]
        line = f"- #{issue['number']} {issue['title']} ({issue['html_url']}) — 누락: {', '.join(missing)}"
        add_finding(recipients, login, "이슈", line)
    print(f"issues checked: {len(issues)}, flagged: {issue_flagged}")

    prs = fetch_prs()
    pr_project_item_ids = fetch_pr_project_item_ids()
    pr_flagged = 0
    for pr in prs:
        clean_pr_issue_only_fields(pr, pr_project_item_ids)
        has_reviews = fetch_pr_has_reviews(pr["number"])
        missing = check_pr(pr, has_reviews)
        if not missing:
            continue
        pr_flagged += 1
        login = pr["user"]["login"]
        line = f"- #{pr['number']} {pr['title']} ({pr['html_url']}) — 누락: {', '.join(missing)}"
        add_finding(recipients, login, "PR", line)
    print(f"prs checked: {len(prs)}, flagged: {pr_flagged}")

    send_all(recipients)
    print(f"notifications sent: {len(recipients)}")


if __name__ == "__main__":
    main()