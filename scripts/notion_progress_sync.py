"""
GitHub Issues/Projects ↔ Notion `작업 트래커` 상시 동기화.

`docs/draft/20-notion-progress-sync.md`의 "상시 동작" 설계를 그대로 구현한다. 이슈 하나당 아래 순서로
처리하고 먼저 걸리는 조건에서 종료한다:

  1. 이미 `GitHub Issue` property로 연결된 카드가 있음      → 필드만 갱신
  2. 연결 안 됐지만 이슈 본문 `## Notion 카드`에 URL이 있음  → 그 카드가 `작업 트래커` DB 소속이고
     아직 다른 이슈에 연결돼 있지 않은 경우에만 연결 후 갱신
  3. URL이 없음                                           → 신규 카드 자동 생성(마일스톤 유무 무관)

마지막으로 "잉여 카드"(소속 스프린트가 끝났는데 대응 이슈가 없는 카드)를 점검해 알림을 보낸다.
이슈 하나 처리 중 오류가 나도 나머지 이슈 처리·잉여 카드 점검은 계속 진행한다.

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

주의: `DISCORD_USER_MAP`이 없거나 특정 GitHub 로그인이 매핑에 없으면 `담당자`는 건드리지 않는다
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
REQUEST_TIMEOUT = 30

PROJECTS_PAT = os.environ.get("PROJECTS_PAT")
DISCORD_BOT_TOKEN = os.environ.get("DISCORD_BOT_TOKEN")
DISCORD_WEBHOOK_URL = os.environ.get("DISCORD_WEBHOOK_URL")
DISCORD_USER_MAP = json.loads(os.environ.get("DISCORD_USER_MAP", "{}"))
NAME_TO_ENTRY = {v["name"]: v for v in DISCORD_USER_MAP.values() if v.get("name")}

STATUS_MAP = {"Todo": "대기", "In Progress": "진행중", "In Review": "리뷰중", "Done": "완료"}
NOTION_URL_RE = re.compile(r"https://[a-zA-Z0-9-]*\.?notion\.so/\S+")


def run_gh(args):
    try:
        return subprocess.run(
            ["gh", *args], capture_output=True, text=True, check=True, timeout=REQUEST_TIMEOUT,
        )
    except subprocess.CalledProcessError as exc:
        raise RuntimeError(f"gh 호출 실패({args}): {exc.stderr.strip()}") from exc


def strip_tag(title):
    if title.startswith("["):
        return title.split("]", 1)[1].strip()
    return title


def parse_notion_url_from_body(body):
    if not body:
        return None
    section_match = re.search(r"##\s*Notion\s*카드.*?(?=\n##\s|\Z)", body, re.S)
    if not section_match:
        return None
    url_match = NOTION_URL_RE.search(section_match.group(0))
    if not url_match:
        return None
    return url_match.group(0).rstrip(").,\n ")


def notion_url_to_page_id(url):
    hex_part = re.sub(r"[^0-9a-fA-F]", "", url.split("?")[0])
    if len(hex_part) < 32:
        raise ValueError(f"Notion URL에서 32자리 ID를 추출하지 못함: {url}")
    hex_part = hex_part[-32:]
    return f"{hex_part[0:8]}-{hex_part[8:12]}-{hex_part[12:16]}-{hex_part[16:20]}-{hex_part[20:32]}"


def fetch_notion_page(page_id):
    resp = requests.get(f"{bf.BASE_URL}/pages/{page_id}", headers=bf.HEADERS, timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    return resp.json()


def validate_link_target(page, issue_url):
    parent = page.get("parent", {})
    db_id = (parent.get("database_id") or "").replace("-", "").lower()
    if db_id != bf.DB_ID.replace("-", "").lower():
        return False
    existing_url = page["properties"]["GitHub Issue"]["url"]
    return existing_url in (None, "", issue_url)


def fetch_milestones():
    proc = run_gh(["api", f"repos/{bf.REPO}/milestones?state=all&per_page=100", "--paginate", "--slurp"])
    pages = json.loads(proc.stdout)
    data = [m for page in pages for m in page]
    return {m["title"]: {"state": m["state"], "due_on": m.get("due_on")} for m in data}


def fetch_projects_items():
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
            timeout=REQUEST_TIMEOUT,
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
    resp = requests.patch(
        f"{bf.BASE_URL}/pages/{page_id}", headers=bf.HEADERS, json={"properties": properties},
        timeout=REQUEST_TIMEOUT,
    )
    resp.raise_for_status()


def link_card(page_id, issue_url):
    resp = requests.patch(
        f"{bf.BASE_URL}/pages/{page_id}", headers=bf.HEADERS,
        json={"properties": {"GitHub Issue": {"url": issue_url}}},
        timeout=REQUEST_TIMEOUT,
    )
    resp.raise_for_status()


def create_new_card(issue, project_item, milestones, sprint_page_by_title):
    labels = {l["name"].lower() for l in issue["labels"]}
    type_ = next(iter(sorted(labels & bf.TYPE_LABELS)), None)
    domain = next(iter(sorted(labels & bf.DOMAIN_LABELS)), None)

    properties = build_sync_properties(issue, project_item, milestones, sprint_page_by_title)
    properties["GitHub Issue"] = {"url": issue["html_url"]}
    if type_:
        properties["타입"] = {"select": {"name": type_.upper()}}
    if domain:
        properties["도메인"] = {"select": {"name": domain}}

    resp = requests.post(
        f"{bf.BASE_URL}/pages", headers=bf.HEADERS,
        json={"parent": {"database_id": bf.DB_ID}, "properties": properties},
        timeout=REQUEST_TIMEOUT,
    )
    resp.raise_for_status()
    return resp.json()["id"]


def send_webhook(message):
    if not DISCORD_WEBHOOK_URL:
        print(f"[webhook 미설정, 콘솔 출력]\n{message}\n")
        return
    resp = requests.post(DISCORD_WEBHOOK_URL, json={"content": message}, timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()


def send_dm_to_discord_id(discord_id, message):
    if not DISCORD_BOT_TOKEN:
        print(f"[DM 미설정, 콘솔 출력]\n{message}\n")
        return
    resp = requests.post(
        "https://discord.com/api/v10/users/@me/channels",
        headers={"Authorization": f"Bot {DISCORD_BOT_TOKEN}"},
        json={"recipient_id": discord_id},
        timeout=REQUEST_TIMEOUT,
    )
    resp.raise_for_status()
    channel_id = resp.json()["id"]
    requests.post(
        f"https://discord.com/api/v10/channels/{channel_id}/messages",
        headers={"Authorization": f"Bot {DISCORD_BOT_TOKEN}"},
        json={"content": message},
        timeout=REQUEST_TIMEOUT,
    ).raise_for_status()


def archive_card(page_id):
    resp = requests.patch(
        f"{bf.BASE_URL}/pages/{page_id}", headers=bf.HEADERS,
        json={"archived": True}, timeout=REQUEST_TIMEOUT,
    )
    resp.raise_for_status()


def delete_marked_cards(cards):
    """`삭제 예정`이 체크된 카드를 archive하고, 남은 카드 목록을 반환한다.
    이후 단계(이슈 동기화 루프·잉여 카드 점검)가 이미 삭제 처리된 카드를
    다시 건드리지 않도록 호출부에서 반환값을 그대로 이어쓴다.

    카드 하나의 archive 요청이 실패해도(네트워크 오류, Notion API 오류 등) 나머지 카드
    처리와 이후 단계를 막지 않는다 — main()의 이슈 처리 루프와 동일한 컨벤션(개별 실패를
    로깅만 하고 계속 진행)을 따른다. 실패한 카드는 삭제되지 않은 것이므로 remaining에 남겨
    다음 실행에서 다시 시도되게 한다."""
    remaining = []
    for card in cards:
        if not bf.card_marked_for_deletion(card):
            remaining.append(card)
            continue
        try:
            archive_card(card["id"])
            print(f"archived (삭제 예정): \"{bf.card_title(card)}\" ({bf.card_notion_url(card)})")
        except requests.RequestException as exc:
            print(f"FAIL archive \"{bf.card_title(card)}\" ({bf.card_notion_url(card)}): {exc}")
            remaining.append(card)
    return remaining


def check_surplus_cards(cards, milestones, sprint_titles):
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
    footer = "\n\n확인 후 스프린트 일정을 이월시키거나 `이슈 미대상` 또는 `삭제 예정`을 체크해주세요."
    for discord_id, lines in dm_lines_by_discord_id.items():
        send_dm_to_discord_id(discord_id, header + "\n".join(lines) + footer)
    if webhook_lines:
        send_webhook(header + "\n".join(webhook_lines) + footer)


def process_issue(issue, linked_cards_by_url, projects_items, milestones, sprint_page_by_title):
    project_item = projects_items.get(issue["number"], {})

    existing = linked_cards_by_url.get(issue["html_url"])
    if existing:
        sync_card(existing["id"], issue, project_item, milestones, sprint_page_by_title)
        print(f"synced #{issue['number']}")
        return

    notion_url = parse_notion_url_from_body(issue.get("body"))
    if notion_url:
        page_id = notion_url_to_page_id(notion_url)
        page = fetch_notion_page(page_id)
        if not validate_link_target(page, issue["html_url"]):
            print(f"skip #{issue['number']}: 대상 카드가 작업 트래커 DB 소속이 아니거나 이미 다른 이슈에 연결됨")
            return
        link_card(page_id, issue["html_url"])
        sync_card(page_id, issue, project_item, milestones, sprint_page_by_title)
        print(f"linked + synced #{issue['number']} -> {page_id}")
        return

    new_id = create_new_card(issue, project_item, milestones, sprint_page_by_title)
    print(f"created card for #{issue['number']} -> {new_id}")


def main():
    issues = bf.fetch_github_issues()
    cards = bf.fetch_notion_cards()
    cards = delete_marked_cards(cards)
    sprint_titles = bf.resolve_sprint_titles(cards)
    sprint_page_by_title = {v: k for k, v in sprint_titles.items()}
    milestones = fetch_milestones()
    projects_items = fetch_projects_items()

    linked_cards_by_url = {bf.card_github_issue_url(c): c for c in cards if bf.card_github_issue_url(c)}

    for issue in issues:
        try:
            process_issue(issue, linked_cards_by_url, projects_items, milestones, sprint_page_by_title)
        except (requests.RequestException, ValueError, RuntimeError) as exc:
            print(f"FAIL #{issue['number']}: {exc}")

    cards = bf.fetch_notion_cards()
    cards = delete_marked_cards(cards)
    sprint_titles = bf.resolve_sprint_titles(cards)
    check_surplus_cards(cards, milestones, sprint_titles)


if __name__ == "__main__":
    main()