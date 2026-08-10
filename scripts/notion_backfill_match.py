"""
GitHub Issue ↔ Notion `작업 트래커` 카드 1회성 백필 매칭 추천표.

`docs/draft/20-notion-progress-sync.md`의 3축(스프린트/타입/도메인) 완전일치 기준으로 후보를 계산해
사람이 검토 후 각 카드의 `GitHub Issue`(url) property에 직접 붙여넣을 수 있도록 리포트만 출력한다.
Notion/GitHub 어느 쪽도 실제로 쓰지 않는다(읽기 전용).

사용법:
  pip install requests
  export NOTION_TOKEN=ntn_xxx
  export NOTION_DB_ID=xxx
  gh auth status로 로그인 확인 후(이 스크립트는 GitHub 쪽 조회에 gh CLI를 그대로 사용):
  python scripts/notion_backfill_match.py
"""

import json
import os
import subprocess
from difflib import SequenceMatcher

import requests

NOTION_VERSION = "2022-06-28"
BASE_URL = "https://api.notion.com/v1"
REPO = "sb11-code-rangers/sb11-otboo-team4"
REQUEST_TIMEOUT = 30

TOKEN = os.environ["NOTION_TOKEN"]
DB_ID = os.environ["NOTION_DB_ID"]
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Notion-Version": NOTION_VERSION,
    "Content-Type": "application/json",
}

TYPE_LABELS = {"adhoc", "batch", "chore", "deploy", "docs", "feat", "fix", "refactor", "test"}
DOMAIN_LABELS = {"auth-user", "clothes-recommend", "social", "weather-notification", "infra"}


def run_gh(args):
    try:
        return subprocess.run(
            ["gh", *args], capture_output=True, text=True, check=True, timeout=REQUEST_TIMEOUT,
        )
    except subprocess.CalledProcessError as exc:
        raise RuntimeError(f"gh 호출 실패({args}): {exc.stderr.strip()}") from exc


def fetch_github_issues():
    proc = run_gh(
        [
            "api", f"repos/{REPO}/issues?state=all&per_page=100", "--paginate",
            "--jq", ".[] | select(.pull_request == null)",
        ],
    )
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def issue_key(issue):
    milestone = issue.get("milestone")
    phase = milestone["title"].split(":", 1)[-1].strip() if milestone else None
    labels = {label["name"].lower() for label in issue["labels"]}
    type_ = next(iter(sorted(labels & TYPE_LABELS)), None)
    domain = next(iter(sorted(labels & DOMAIN_LABELS)), None)
    return phase, type_, domain


def fetch_notion_cards():
    cards, cursor = [], None
    while True:
        payload = {"page_size": 100}
        if cursor:
            payload["start_cursor"] = cursor
        resp = requests.post(
            f"{BASE_URL}/databases/{DB_ID}/query", headers=HEADERS, json=payload, timeout=REQUEST_TIMEOUT,
        )
        resp.raise_for_status()
        data = resp.json()
        cards.extend(data["results"])
        if not data.get("has_more"):
            break
        cursor = data["next_cursor"]
    return cards


def resolve_sprint_titles(cards):
    ids = {rel["id"] for card in cards for rel in card["properties"]["스프린트"]["relation"]}
    titles = {}
    for page_id in ids:
        resp = requests.get(f"{BASE_URL}/pages/{page_id}", headers=HEADERS, timeout=REQUEST_TIMEOUT)
        resp.raise_for_status()
        for value in resp.json()["properties"].values():
            if value["type"] == "title":
                titles[page_id] = plain_text(value["title"])
    return titles


def plain_text(rich_list):
    return "".join(t.get("plain_text", "") for t in rich_list)


def card_title(card):
    return plain_text(card["properties"]["작업명"]["title"])


def card_github_issue_url(card):
    return card["properties"]["GitHub Issue"]["url"]


def card_excluded(card):
    return card["properties"]["이슈 미대상"]["checkbox"]


def card_marked_for_deletion(card):
    return card["properties"]["삭제 예정"]["checkbox"]


def card_notion_url(card):
    return f"https://www.notion.so/{card['id'].replace('-', '')}"


def card_key(card, sprint_titles):
    props = card["properties"]
    sprint_rel = props["스프린트"]["relation"]
    phase = sprint_titles.get(sprint_rel[0]["id"]) if sprint_rel else None
    type_select = props["타입"]["select"]
    domain_select = props["도메인"]["select"]
    type_ = type_select["name"].lower() if type_select else None
    domain = domain_select["name"].lower() if domain_select else None
    return phase, type_, domain


def build_candidate_index(cards, sprint_titles):
    index = {}
    for card in cards:
        if card_github_issue_url(card):
            continue
        if card_excluded(card):
            continue
        index.setdefault(card_key(card, sprint_titles), []).append(card)
    return index


def title_similarity(a, b):
    return SequenceMatcher(None, a, b).ratio()


def classify(issues, index):
    issues_by_key, no_milestone = {}, []
    for issue in issues:
        if not issue.get("milestone"):
            no_milestone.append(issue)
            continue
        issues_by_key.setdefault(issue_key(issue), []).append(issue)

    confirmed, ambiguous, no_candidate, conflict = [], [], [], []
    for key, key_issues in issues_by_key.items():
        candidates = index.get(key, [])
        if len(candidates) == 1 and len(key_issues) == 1:
            confirmed.append((key_issues[0], candidates[0]))
        elif len(candidates) == 1:
            conflict.append((key_issues, candidates[0]))
        elif candidates:
            for issue in key_issues:
                ranked = sorted(
                    candidates, key=lambda c: -title_similarity(issue["title"], card_title(c)),
                )
                ambiguous.append((issue, ranked))
        else:
            no_candidate.extend(key_issues)
    return confirmed, conflict, ambiguous, no_candidate, no_milestone


def print_report(confirmed, conflict, ambiguous, no_candidate, no_milestone):
    print(f"=== 확정 매칭 ({len(confirmed)}건) — 그대로 GitHub Issue property에 붙여넣기 ===\n")
    for issue, card in confirmed:
        print(f"- #{issue['number']} {issue['title']}")
        print(f"  GitHub: {issue['html_url']}")
        print(f"  Notion: {card_notion_url(card)} (\"{card_title(card)}\")\n")

    print(f"\n=== 충돌 — 서로 다른 이슈가 카드 1개를 동시에 지목 ({len(conflict)}건) — 우연한 3축 일치, 직접 판단 필요 ===\n")
    for key_issues, card in conflict:
        print(f"  Notion: {card_notion_url(card)} (\"{card_title(card)}\")")
        for issue in key_issues:
            print(f"    - #{issue['number']} {issue['title']} ({issue['html_url']})")
        print()

    print(f"\n=== 모호함 — 후보 여러 개, 제목 유사도 순 ({len(ambiguous)}건) ===\n")
    for issue, ranked in ambiguous:
        print(f"- #{issue['number']} {issue['title']}")
        print(f"  GitHub: {issue['html_url']}")
        for card in ranked:
            score = title_similarity(issue["title"], card_title(card))
            print(f"    후보(유사도 {score:.2f}): {card_notion_url(card)} (\"{card_title(card)}\")")
        print()

    print(f"\n=== 후보 없음 — 새 카드 필요 ({len(no_candidate)}건) ===\n")
    for issue in no_candidate:
        print(f"- #{issue['number']} {issue['title']} ({issue['html_url']})")

    print(f"\n=== 마일스톤 없음 — 후보 계산 안 함, 직접 찾아서 매칭 ({len(no_milestone)}건) ===\n")
    for issue in no_milestone:
        print(f"- #{issue['number']} {issue['title']} ({issue['html_url']})")


def main():
    issues = fetch_github_issues()
    cards = fetch_notion_cards()
    sprint_titles = resolve_sprint_titles(cards)
    index = build_candidate_index(cards, sprint_titles)

    confirmed, conflict, ambiguous, no_candidate, no_milestone = classify(issues, index)
    print_report(confirmed, conflict, ambiguous, no_candidate, no_milestone)


if __name__ == "__main__":
    main()