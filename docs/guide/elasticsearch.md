# Elasticsearch 인덱스 운영 가이드

> 피드 검색 인덱스의 구조와 매핑 변경 절차를 다룹니다.
>
> 검색 쿼리 작성은 `FeedSearchCustomRepositoryImpl`을, 인덱싱 경로는 `FeedIndexEventListener`를 참고하세요.

## 1. 인덱스 구조

인덱스는 `feeds_v{n}`으로 만들고 `feeds`를 alias로 붙입니다.

```text
feeds (alias) → feeds_v2
```

애플리케이션은 alias만 봅니다. `FeedDocument`의 `@Document(indexName = "feeds")`는 실제 인덱스가 아니라 alias를 가리킵니다.
검색·색인 모두 alias를 거치므로 실제 인덱스가 몇 번째 세대인지 코드는 알지 못합니다.

기동 시 `FeedIndexInitializer`(`global/config`)가 alias 유무를 확인해 없으면 `feeds_v1`을 만들고 alias를 붙입니다.

```java
// @Document(createIndex = false)라 Spring Data가 인덱스를 만들지 않는다.
// 다중 인스턴스 동시 기동 시 경합을 피하려고 생성 주체를 Initializer로 고정한다.
```

## 2. 매핑을 바꿀 때

매핑을 바꾸고 재색인 없이 배포하면 **검색이 예외 없이 전건 0**으로 떨어집니다. 인덱스가 이미 있으면 Initializer가 그대로 지나가고, `copy_to`는 이미 색인된
문서를 소급해 채우지 않기 때문입니다.

### 절차

1. `FeedDocument`(또는 `elasticsearch/feed-settings.json`)를 수정하고 배포합니다.
2. 기동 로그에 매핑 불일치 경고가 뜹니다. **검색은 계속 됩니다** — 새 필드만 안 잡힙니다.

   ```text
   FEED_INDEX_MIGRATION_REQUIRED: feeds가 alias가 아닌 실제 인덱스입니다.
   ```

3. ADMIN 권한으로 마이그레이션을 트리거합니다.

   ```bash
   curl -X POST "{host}/actuator/feedindexmigration" \
     -H "Authorization: Bearer {ADMIN 토큰}" \
     -H "X-XSRF-TOKEN: {CSRF 토큰}" \
     -b {쿠키}
   ```

4. `FeedIndexMigrationService`가 네 단계를 순서대로 밟습니다.

   ```text
   feeds_v2 생성 (새 매핑)
   DB에서 feeds_v2로 전체 재색인      ← 이 동안 검색은 feeds_v1
   alias 전환 (remove + add 한 요청)
   feeds_v{n-2} 삭제 (한 세대 보존)
   ```

5. 다음 기동부터 매핑 경고가 사라집니다.

### 왜 수동인가

매핑 변경은 배포와 함께 일어나는 일이라 시점을 사람이 정합니다. 스케줄러에 걸면 의도치 않은 때에 인덱스가 바뀌고, 기동 경로에 두면 다중 인스턴스가 각자 새 인덱스를 만들어
alias가 엉킵니다.

### 왜 `_reindex`가 아니라 DB인가

`_reindex` API는 `_source`를 그대로 옮깁니다. `copy_to`는 색인 시점에만 동작하므로 `searchText`가 채워지지 않습니다.

## 3. 롤백

전환 후 문제가 발견되면 alias만 되돌립니다. 이전 인덱스를 한 세대 남겨두므로 재색인 없이 복구됩니다.

```bash
curl -X POST "{ES_HOST}/_aliases" -H 'Content-Type: application/json' -d '{
  "actions": [
    { "remove": { "index": "feeds_v3", "alias": "feeds" } },
    { "add":    { "index": "feeds_v2", "alias": "feeds" } }
  ]
}'
```

두 세대 전(`feeds_v1`)은 마이그레이션 시 삭제되므로 되돌릴 수 없습니다.

## 4. 매핑 불일치 감지

기동 시 `FeedDocument`가 기대하는 필드가 실제 매핑에 있는지 확인하고, 없으면 로그를 남깁니다.

- **필드 이름만 비교합니다.** 타입·analyzer는 ES가 정규화해 돌려주므로 그대로 비교하면 오탐이 납니다.
- **기대 − 실제** 방향만 봅니다. 매핑에 없는 필드는 검색이 조용히 비지만, 남는 필드는 지저분할 뿐 동작에 영향이 없습니다.
- **기동을 막지 않습니다.** 검색은 앱의 일부 기능이라 전체 서비스를 내리는 것은 과합니다.

`feeds`가 alias가 아닌 실제 인덱스인 경우에도 경고가 뜹니다.

```text
FEED_INDEX_MAPPING_MISMATCH: 실제 매핑에 없는 필드가 있습니다: fields=[newField]
```

`exists()`는 `HEAD` 요청이라 alias와 인덱스를 구분하지 못해, alias 조회로 별도 판별합니다.

## 5. 재색인 배치와의 차이

|        | 정합성 재색인                                        | 매핑 마이그레이션               |
|--------|------------------------------------------------|-------------------------|
| 목적     | 이벤트 인덱싱이 놓친 drift 보정                           | 매핑 변경 시 새 인덱스로 전환       |
| 트리거    | 스케줄 (주 1회 / 매시)                                | 수동 (Actuator)           |
| 대상 인덱스 | alias (`feeds`)                                | 새 인덱스 (`feeds_v{n+1}`)  |
| Job    | `feedReindexJob` / `feedIncrementalReindexJob` | `feedIndexMigrationJob` |
| 패키지    | `batch/feedreindex`                            | `batch/feedmigration`   |

Reader·Writer·SkipPolicy·리스너는 공유합니다. `FeedReindexWriter`가 대상 인덱스를 JobParameter(`targetIndex`)로 받아 어느
인덱스에 쓸지 결정합니다.

## 6. 초기 전환 (alias 도입 시 1회)

alias 구조 도입 전에 만들어진 `feeds` 인덱스가 있으면 한 번 삭제해야 합니다. 같은 이름을 alias로 쓸 수 없기 때문입니다.

```bash
curl -X DELETE "{ES_HOST}/feeds"
```

이후 기동하면 `feeds_v1`과 alias가 만들어집니다. 문서는 재색인 배치나 이벤트 인덱싱으로 채워집니다.

**삭제부터 재색인까지 검색이 빕니다.** 운영에서는 트래픽이 적은 시간에 진행하세요.

## 7. 알려진 한계

재색인과 alias 전환 사이에 등록·수정된 피드는 구 인덱스에만 들어가 전환 후 검색에서 누락됩니다. 주 1회 정합성 재색인이 교정합니다.

마이그레이션과 정합성 재색인은 상호 배제되지 않습니다. 동시에 돌면 재색인 결과가 alias 전환 후 버려지지만, 데이터가 유실되지는 않습니다.
