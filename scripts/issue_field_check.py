"""
GitHub Issue/PR 필수 필드 검증.

notion_progress_sync.py와 같은 주기(같은 workflow)로 Issue/PR을 훑어 필수 필드 누락을 점검한다.
주기적 검증이라 open만이 아니라 **closed/merged 포함 전체(state=all)** 가 대상이다(예: 이미 merge된
PR에 마일스톤/Projects 값이 남아있는 경우도 정리 대상). Notion 카드는 건드리지 않는다 — GitHub 쪽
원장 데이터 자체를 깨끗하게 유지하는 목적.

필수 필드:
  Issue: Assignees, Labels(타입 라벨 1개 + 도메인 라벨 1개), Type(네이티브 Issue Type),
         Milestone, Projects(Priority, Start date, Category)
  PR:    Reviewers(요청 중이거나 리뷰 이력 있음), Assignees, Labels

- 미설정 → **작성자**(이슈/PR 둘 다 author — assignee가 누락 항목일 수도 있어서 assignee한테 보내면
  보낼 대상이 없는 경우가 생김)에게 DM, 매핑 없으면 웹훅 — **한 실행에서 이슈+PR findings를 합쳐
  수신자 1명당 메시지 1건**(웹훅도 전체 통틀어 1건)만 발송
- **라벨 과다설정(타입/도메인 2개 이상)은 이슈든 PR이든 건드리지 않음**(허용) — 삭제 안 함
- PR에 **Milestone**이나 **Projects 아이템**이 붙어있으면(이슈 전용 개념이라 PR엔 있으면 안 됨)
  완전히 제거 — Milestone은 REST PATCH로 null 처리, Projects는 아이템 자체를
  `deleteProjectV2Item`으로 삭제(필드 값만 지우는 게 아니라 보드에서 아예 뺌)

필요 시크릿: notion_progress_sync.py와 동일(PROJECTS_PAT, DISCORD_BOT_TOKEN, DISCORD_USER_MAP,
DISCORD_WEBHOOK_URL) + PR 마일스톤 제거를 위한 GH_TOKEN에 issues:write 권한 필요.
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


def fetch_issues():
    """주기적 검증이라 open만이 아니라 전체(state=all)를 대상으로 함."""
    proc = subprocess.run(
        [
            "gh", "api", f"repos/{bf.REPO}/issues?state=all&per_page=100", "--paginate",
            "--jq", ".[] | select(.pull_request == null)",
        ],
        capture_output=True, text=True, check=True,
    )
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def fetch_prs():
    """주기적 검증이라 open만이 아니라 전체(state=all)를 대상으로 함."""
    proc = subprocess.run(
        [
            "gh", "api", f"repos/{bf.REPO}/pulls?state=all&per_page=100", "--paginate",
            "--jq", ".[]",
        ],
        capture_output=True, text=True, check=True,
    )
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def fetch_pr_has_reviews(number):
    proc = subprocess.run(
        ["gh", "api", f"repos/{bf.REPO}/pulls/{number}/reviews", "--jq", "length"],
        capture_output=True, text=True, check=True,
    )
    return int(proc.stdout.strip() or "0") > 0


def fetch_issue_project_fields():
    """이슈 번호 -> {priority, start_date, category}. PROJECTS_PAT 없으면 빈 dict.

    `Target date`는 필수 필드 목록(Priority/Start date/Category)에 의도적으로 포함하지 않는다 —
    이 값은 착수 시점이 아니라 진행하면서 나중에 채워 넣는 필드라, 없다고 "누락"으로 잡거나
    자동으로 지우는 대상이 아님.
    """
    if not PROJECTS_PAT:
        return {}
    headers = {"Authorization": f"Bearer {PROJECTS_PAT}", "Content-Type": "application/json"}
    query = """
    query($org: String!, $number: Int!, $cursor: String) {
      organization(login: $org) {
        projectV2(number: $number) {
          items(first: 100, after: $cursor) {
            pageInfo { hasNextPage endCursor }
            nodes {
              content { __typename ... on Issue { number } }
              fieldValues(first: 20) {
                nodes {
                  __typename
                  ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
                  ... on ProjectV2ItemFieldDateValue { date field { ... on ProjectV2FieldCommon { name } } }
                  ... on ProjectV2ItemFieldMultiSelectValue { value field { ... on ProjectV2FieldCommon { name } } }
                }
              }
            }
          }
        }
      }
    }
    """
    fields, cursor = {}, None
    while True:
        resp = requests.post(
            "https://api.github.com/graphql",
            headers=headers,
            json={"query": query, "variables": {"org": ORG, "number": PROJECT_NUMBER, "cursor": cursor}},
        )
        resp.raise_for_status()
        page = resp.json()["data"]["organization"]["projectV2"]["items"]
        for node in page["nodes"]:
            content = node.get("content") or {}
            if content.get("__typename") != "Issue":
                continue
            number = content.get("number")
            if number is None:
                continue
            priority, start_date, category = None, None, None
            for fv in node["fieldValues"]["nodes"]:
                field_name = (fv.get("field") or {}).get("name")
                if field_name == "Priority":
                    priority = fv.get("name")
                elif field_name == "Start date":
                    start_date = fv.get("date")
                elif field_name == "Category":
                    category = fv.get("value")
            fields[number] = {"priority": priority, "start_date": start_date, "category": category}
        if not page["pageInfo"]["hasNextPage"]:
            break
        cursor = page["pageInfo"]["endCursor"]
    return fields


def fetch_pr_project_item_ids():
    """PR 번호 -> project item id. PR은 애초에 프로젝트 보드 아이템으로 안 남아있어야 함(완전 제거 대상)."""
    if not PROJECTS_PAT:
        return {}
    headers = {"Authorization": f"Bearer {PROJECTS_PAT}", "Content-Type": "application/json"}
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
        resp = requests.post(
            "https://api.github.com/graphql",
            headers=headers,
            json={"query": query, "variables": {"org": ORG, "number": PROJECT_NUMBER, "cursor": cursor}},
        )
        resp.raise_for_status()
        page = resp.json()["data"]["organization"]["projectV2"]["items"]
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
    if not PROJECTS_PAT:
        return
    headers = {"Authorization": f"Bearer {PROJECTS_PAT}", "Content-Type": "application/json"}
    mutation = """
    mutation($project: ID!, $item: ID!) {
      deleteProjectV2Item(input: {projectId: $project, itemId: $item}) {
        deletedItemId
      }
    }
    """
    resp = requests.post(
        "https://api.github.com/graphql",
        headers=headers,
        json={"query": mutation, "variables": {"project": PROJECT_ID, "item": item_id}},
    )
    resp.raise_for_status()
    if "errors" in resp.json():
        print(f"FAIL delete project item {item_id}: {resp.json()['errors']}")


def clear_pr_milestone(pr_number):
    proc = subprocess.run(
        ["gh", "api", "-X", "PATCH", f"repos/{bf.REPO}/issues/{pr_number}", "--input", "-"],
        input='{"milestone": null}', capture_output=True, text=True,
    )
    if proc.returncode != 0:
        print(f"FAIL clear milestone on PR #{pr_number}: {proc.stderr}")


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
    """recipients: {키: {섹션: [줄]}} — 키가 None이면 웹훅 버킷. 이슈/PR findings를 한 곳에 모아서
    실행 전체를 통틀어 수신자 1명당 메시지 1건(웹훅도 전체 통틀어 1건)만 나가게 한다."""
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
        if key is None:
            s.send_webhook(message)
        else:
            entry = s.DISCORD_USER_MAP[key]
            s.send_dm_to_discord_id(entry["discord_id"], message)


def main():
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