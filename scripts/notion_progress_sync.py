"""
GitHub Issues/Projects ↔ Notion `작업 트래커` 상시 동기화.

`docs/draft/20-notion-progress-sync.md`의 "상시 동작" 설계를 그대로 구현한다. 이슈 하나당 아래 순서로
처리하고 먼저 걸리는 조건에서 종료한다:

  1. 이미 `GitHub Issue` property로 연결된 카드가 있음      → 필드만 갱신
  2. 연결 안 됐지만 이슈 본문 `## Notion 카드`에 URL이 있음  → 그 카드에 연결 후 갱신
  3. URL이 없음                                           → 신규 카드 자동 생성

마지막으로 "잉여 카드"(소속 스프린트가 끝났는데 대응 이슈가 없는 카드)를 점검해 알림을 보낸다.

과거엔 3번에서 3축(스프린트+타입+도메인) 매칭으로 후보를 찾아 "후보 있으면 알림만, 후보 없으면
생성"으로 나눴었는데, 후보 랭킹/알림 판단 자체가 애매해서 걷어냄 — URL이 없으면 무조건 신규 생성하고,
그 결과 생기는 중복은 `notion_backfill_match.py`(수동 실행)로 사람이 검토해 정리하는 쪽이 더 단순하고
실제로도 잘 작동함(1회 실행 결과 13건 중 대부분이 중복이었고 사람이 바로 정리 가능했음).

필요 시크릿(env):
  NOTION_TOKEN, NOTION_DB_ID          — notion_backfill_match와 동일
  PROJECTS_PAT                        — GitHub Projects(v2) GraphQL, `project` 스코프 (없으면 상태/목표일은 issue.state 기반 최소 매핑만 적용)
  DISCORD_BOT_TOKEN, DISCORD_USER_MAP — DM 발송 (review-discord.yml과 동일 시크릿/포맷 재사용)
  DISCORD_WEBHOOK_URL                 — 담당자 특정 안 되는 공통 알림
  gh CLI 인증 필요(REST 이슈/마일스톤 조회)

주의: `DISCORD_USER_MAP`이 없거나 특정 GitHub 로그인이 매핑에 없으면 `담당자`는 **건드리지 않는다**
(모른다고 "공통 작업"으로 덮어쓰면 이미 정확히 기재된 값을 잘못 지울 수 있음 — 이슈에 담당자가
아예 없는 경우에만 "공통 작업"으로 채운다).
"""

import json
import os
import re
import subprocess
import sys

import requests

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import notion_backfill_match as bf

ORG = "sb11-code-rangers"
PROJECT_NUMBER = 1

PROJECTS_PAT = os.environ.get("PROJECTS_PAT")
DISCORD_BOT_TOKEN = os.environ.get("DISCORD_BOT_TOKEN")
DISCORD_WEBHOOK_URL = os.environ.get("DISCORD_WEBHOOK_URL")
DISCORD_USER_MAP = json.loads(os.environ.get("DISCORD_USER_MAP", "{}"))
NAME_TO_ENTRY = {v["name"]: v for v in DISCORD_USER_MAP.values() if v.get("name")}

STATUS_MAP = {"Todo": "대기", "In Progress": "진행중", "In Review": "리뷰중", "Done": "완료"}
NOTION_URL_RE = re.compile(r"https://[a-zA-Z0-9-]*\.?notion\.so/\S+")


def strip_tag(title):
    if title.startswith("["):
        return title.split("]", 1)[1].strip()
    return title


def parse_notion_url_from_body(body):
    if not body:
        return None
    section_match = re.search(r"##\s*Notion\s*카드.*?(?=\n##\s|\Z)", body, re.S)
    section = section_match.group(0) if section_match else body
    url_match = NOTION_URL_RE.search(section)
    if not url_match:
        return None
    return url_match.group(0).rstrip(").,\n ")


def notion_url_to_page_id(url):
    hex_part = re.sub(r"[^0-9a-fA-F]", "", url.split("?")[0])[-32:]
    return f"{hex_part[0:8]}-{hex_part[8:12]}-{hex_part[12:16]}-{hex_part[16:20]}-{hex_part[20:32]}"


def fetch_milestones():
    proc = subprocess.run(
        ["gh", "api", f"repos/{bf.REPO}/milestones?state=all", "--jq", "."],
        capture_output=True, text=True, check=True,
    )
    data = json.loads(proc.stdout)
    return {m["title"]: {"state": m["state"], "due_on": m.get("due_on")} for m in data}


def fetch_projects_items():
    """이슈 번호 -> {status, target_date}. PROJECTS_PAT 없으면 빈 dict(상태는 issue.state로만 판단)."""
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
              content { ... on Issue { number } }
              fieldValues(first: 20) {
                nodes {
                  ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
                  ... on ProjectV2ItemFieldDateValue { date field { ... on ProjectV2FieldCommon { name } } }
                }
              }
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
            number = (node.get("content") or {}).get("number")
            if number is None:
                continue
            status, target_date = None, None
            for fv in node["fieldValues"]["nodes"]:
                field_name = (fv.get("field") or {}).get("name")
                if field_name == "Status":
                    status = fv.get("name")
                elif field_name == "Target date":
                    target_date = fv.get("date")
            items[number] = {"status": status, "target_date": target_date}
        if not page["pageInfo"]["hasNextPage"]:
            break
        cursor = page["pageInfo"]["endCursor"]
    return items


def compute_status(issue, project_item, milestones):
    if issue["state"] == "closed":
        return "완료"
    milestone = issue.get("milestone")
    if milestone:
        m = milestones.get(milestone["title"])
        if m and m["state"] == "closed":
            return "이월"
    return STATUS_MAP.get((project_item or {}).get("status"), "대기")


def resolve_assignee_name(issue):
    """None이면 '모르니 건드리지 않음' — 실제 담당자가 있는데 맵에 없어서 지우는 사고 방지."""
    assignees = issue.get("assignees") or []
    if not assignees:
        return "공통 작업"
    login = assignees[0]["login"]
    entry = DISCORD_USER_MAP.get(login)
    return entry["name"] if entry else None


def build_sync_properties(issue, project_item, milestones, sprint_page_by_title):
    labels = {l["name"].lower() for l in issue["labels"]}
    properties = {
        "상태": {"select": {"name": compute_status(issue, project_item, milestones)}},
        "심화": {"checkbox": "advanced" in labels},
        "작업명": {"title": [{"text": {"content": strip_tag(issue["title"])}}]},
    }
    target_date = (project_item or {}).get("target_date")
    if target_date:
        properties["목표일"] = {"date": {"start": target_date}}
    if issue.get("milestone"):
        phase_title = issue["milestone"]["title"].split(":", 1)[-1].strip()
        sprint_id = sprint_page_by_title.get(phase_title)
        if sprint_id:
            properties["스프린트"] = {"relation": [{"id": sprint_id}]}
    assignee_name = resolve_assignee_name(issue)
    if assignee_name:
        properties["담당자"] = {"select": {"name": assignee_name}}
    return properties


def sync_card(page_id, issue, project_item, milestones, sprint_page_by_title):
    properties = build_sync_properties(issue, project_item, milestones, sprint_page_by_title)
    resp = requests.patch(f"{bf.BASE_URL}/pages/{page_id}", headers=bf.HEADERS, json={"properties": properties})
    resp.raise_for_status()


def link_card(page_id, issue_url):
    resp = requests.patch(
        f"{bf.BASE_URL}/pages/{page_id}", headers=bf.HEADERS,
        json={"properties": {"GitHub Issue": {"url": issue_url}}},
    )
    resp.raise_for_status()


def create_new_card(issue, project_item, milestones, sprint_page_by_title):
    labels = {l["name"].lower() for l in issue["labels"]}
    type_ = next((l.upper() for l in labels if l in bf.TYPE_LABELS), None)
    domain = next((l for l in labels if l in bf.DOMAIN_LABELS), None)

    properties = build_sync_properties(issue, project_item, milestones, sprint_page_by_title)
    properties["GitHub Issue"] = {"url": issue["html_url"]}
    if type_:
        properties["타입"] = {"select": {"name": type_}}
    if domain:
        properties["도메인"] = {"select": {"name": domain}}

    resp = requests.post(
        f"{bf.BASE_URL}/pages", headers=bf.HEADERS,
        json={"parent": {"database_id": bf.DB_ID}, "properties": properties},
    )
    resp.raise_for_status()
    return resp.json()["id"]


def send_webhook(message):
    if not DISCORD_WEBHOOK_URL:
        print(f"[webhook 미설정, 콘솔 출력]\n{message}\n")
        return
    requests.post(DISCORD_WEBHOOK_URL, json={"content": message})


def send_dm_to_discord_id(discord_id, message):
    if not DISCORD_BOT_TOKEN:
        print(f"[DM 미설정, 콘솔 출력]\n{message}\n")
        return
    resp = requests.post(
        "https://discord.com/api/v10/users/@me/channels",
        headers={"Authorization": f"Bot {DISCORD_BOT_TOKEN}"},
        json={"recipient_id": discord_id},
    )
    resp.raise_for_status()
    channel_id = resp.json()["id"]
    requests.post(
        f"https://discord.com/api/v10/channels/{channel_id}/messages",
        headers={"Authorization": f"Bot {DISCORD_BOT_TOKEN}"},
        json={"content": message},
    ).raise_for_status()




def check_surplus_cards(cards, milestones, sprint_titles):
    """소속 스프린트(마일스톤) 자체가 closed됐는데도 연결된 이슈가 없는 카드만 점검 대상으로 삼는다.

    개별 카드의 `목표일`이 지났다는 것만으로는 플래그하지 않는다 — 스프린트가 아직 진행 중이면
    막바지에 목표일이 지난 카드가 있는 게 정상이라(단순 지연), "다른 이슈로 이미 처리됐는지 확인
    필요"라는 잉여 신호와는 다르다(실사용 중 오탐 확인: 2차 스프린트가 아직 open인데 목표일만 지난
    카드가 잉여로 잘못 잡힘).
    """
    dm_lines_by_discord_id = {}
    webhook_lines = []

    for card in cards:
        if bf.card_github_issue_url(card):
            continue
        if bf.card_excluded(card):
            continue
        props = card["properties"]
        sprint_rel = props["스프린트"]["relation"]
        phase_title = sprint_titles.get(sprint_rel[0]["id"]) if sprint_rel else None
        if not phase_title:
            continue
        milestone_title = next(
            (t for t in milestones if t.split(":", 1)[-1].strip() == phase_title), None
        )
        phase_ended = milestone_title and milestones[milestone_title]["state"] == "closed"
        if not phase_ended:
            continue

        assignee = props["담당자"]["select"]
        name = assignee["name"] if assignee else None
        line = f"- \"{bf.card_title(card)}\" ({phase_title}) — {bf.card_notion_url(card)}"

        entry = NAME_TO_ENTRY.get(name) if name and name != "공통 작업" else None
        if entry:
            dm_lines_by_discord_id.setdefault(entry["discord_id"], []).append(line)
        else:
            webhook_lines.append(line)

    header = "[잉여 카드 점검] 아래 카드들은 소속 스프린트가 종료됐는데 연결된 GitHub 이슈가 없습니다:\n"
    for discord_id, lines in dm_lines_by_discord_id.items():
        send_dm_to_discord_id(discord_id, header + "\n".join(lines))
    if webhook_lines:
        send_webhook(header + "\n".join(webhook_lines))


def main():
    issues = bf.fetch_github_issues()
    cards = bf.fetch_notion_cards()
    sprint_titles = bf.resolve_sprint_titles(cards)
    sprint_page_by_title = {v: k for k, v in sprint_titles.items()}
    milestones = fetch_milestones()
    projects_items = fetch_projects_items()

    linked_cards_by_url = {bf.card_github_issue_url(c): c for c in cards if bf.card_github_issue_url(c)}

    for issue in issues:
        project_item = projects_items.get(issue["number"], {})

        existing = linked_cards_by_url.get(issue["html_url"])
        if existing:
            sync_card(existing["id"], issue, project_item, milestones, sprint_page_by_title)
            print(f"synced #{issue['number']}")
            continue

        notion_url = parse_notion_url_from_body(issue.get("body"))
        if notion_url:
            page_id = notion_url_to_page_id(notion_url)
            link_card(page_id, issue["html_url"])
            sync_card(page_id, issue, project_item, milestones, sprint_page_by_title)
            print(f"linked + synced #{issue['number']} -> {page_id}")
            continue

        if not issue.get("milestone"):
            continue

        new_id = create_new_card(issue, project_item, milestones, sprint_page_by_title)
        print(f"created card for #{issue['number']} -> {new_id}")

    check_surplus_cards(cards, milestones, sprint_titles)


if __name__ == "__main__":
    main()