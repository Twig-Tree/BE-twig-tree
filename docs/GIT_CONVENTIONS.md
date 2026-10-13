# Git 작업 컨벤션

이 문서는 TwigTree 백엔드 저장소에서 브랜치를 만들고 Pull Request를 작성할 때 사용하는 규칙을 정의한다.

## 브랜치명 형식

새 작업 브랜치는 다음 형식을 사용한다.

```text
<type>/#<issue-number>-<description>
```

- `type`: 작업 종류를 나타내는 소문자 접두어
- `issue-number`: 실제로 존재하는 GitHub 이슈 번호
- `description`: 작업 내용을 요약한 영문 소문자 kebab-case

예시:

```text
feat/#101-node-search
fix/#38-document-parser-defense
chore/#55-ci
chore/#55-codeql
chore/#55-cd
docs/#120-git-conventions
```

이슈 번호가 확인되지 않았다면 임의의 번호를 만들지 않는다. 먼저 이슈를 확인하거나 사용자에게 물어본다.

## 표준 type

다음 type은 현재 PR 제목 자동화와 자동 라벨링이 모두 지원한다.

| type | 용도 | 자동 PR 제목 | 자동 라벨 |
|---|---|---|---|
| `feat` | 기능 추가 | `[Feat]` | `✨ feature` |
| `fix` | 버그 수정 | `[Fix]` | `🐛 bug/error` |
| `refactor` | 동작을 유지하는 구조 개선 | `[Refactor]` | `♻️ refactor` |
| `chore` | 빌드, 설정, CI/CD, 의존성 작업 | `[Chore]` | `⚙️ chore` |
| `docs` | 문서 작업 | `[Docs]` | `📝 documentation` |
| `hotfix` | 운영 긴급 수정 | `[Hotfix]` | `🚑 hotfix` |

`test`도 PR 제목 자동화에서 `[Test]`로 인식하지만, 현재 `.github/labeler.yml`에는 대응 라벨 규칙이 없다. 독립적인 테스트 작업에 `test`를 사용할 수 있지만 PR 라벨은 수동으로 지정해야 한다.

## 자동화와의 관계

이 형식은 다음 저장소 자동화가 사용한다.

- `.github/workflows/pr-title-prefix.yml`
  - 브랜치의 첫 번째 경로를 읽어 PR 제목에 `[Feat]`, `[Fix]` 등의 접두어를 붙인다.
- `.github/workflows/pr-issue-link.yml`
  - `^[a-z]+/#(\d+)-` 형식에서 이슈 번호를 읽어 PR 템플릿의 `#{issue_number}`를 실제 번호로 바꾼다.
- `.github/labeler.yml`
  - 브랜치 접두어에 따라 PR 라벨을 지정한다.

따라서 표준 형식을 벗어난 브랜치도 GitHub에 생성될 수는 있지만, 제목·이슈 연결·라벨 자동화가 일부 또는 전부 동작하지 않을 수 있다.

## GitHub가 실제로 제한하는 범위

현재 저장소 ruleset은 브랜치 이름 패턴을 제한하지 않는다. ruleset은 기본 브랜치에 대해 다음 항목만 강제한다.

- 브랜치 삭제 금지
- force push 금지
- Pull Request를 통한 변경
- 승인 1명
- 리뷰 대화 해결
- squash merge

즉, `feat`, `fix` 같은 접두어는 GitHub가 브랜치 생성을 제한하는 허용 목록이 아니라 이 저장소의 자동화가 지원하는 프로젝트 컨벤션이다.

확인 위치:

```text
GitHub 저장소
└─ Settings
   └─ Rules
      └─ Rulesets
```

## 브랜치 생성 전 체크리스트

- 실제 GitHub 이슈 번호를 확인한다.
- 작업 성격에 맞는 표준 type을 고른다.
- 설명은 영문 소문자 kebab-case로 작성한다.
- 하나의 브랜치가 하나의 리뷰 가능한 작업 범위를 갖도록 한다.
- CI, CodeQL, CD처럼 독립적으로 검증할 수 있는 작업은 브랜치를 분리한다.
- 브랜치를 push하거나 PR을 열기 전에 전체 이름을 다시 확인한다.

## PR 작성 시 주의사항

- 브랜치명에서 연결한 이슈와 PR 본문의 이슈가 일치해야 한다.
- 하나의 이슈를 여러 PR로 나누면 중간 PR은 `Refs #<issue-number>`를 사용한다.
- 이슈의 모든 작업이 완료되는 마지막 PR에서만 `Closes #<issue-number>`를 사용한다.
- 자동 생성된 PR 제목 접두어와 작업 type이 일치하는지 확인한다.
- 자동 라벨이 지정되지 않았다면 브랜치명과 `.github/labeler.yml`을 확인한다.
