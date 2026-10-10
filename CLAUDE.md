# Hanil Keyboard

한글 두벌식 자판으로 일본어를 입력하는 Android IME. 상세 명세는 `docs/SPEC.md`, 한글→히라가나 규칙은 `docs/CONVERSION_RULES.md`, 사용자 프롬프트 원문은 `docs/PROMPTS.md`.

## 문서 갱신 규칙 (필수)
- 사용자의 새 프롬프트(요청)는 `docs/PROMPTS.md` 맨 아래에 원문 그대로 추가한다. 상시 지침이면 같은 파일의 "작업 규칙"에도 추가한다.
- 새 요구사항이나 결정사항이 생기면 `docs/SPEC.md` 의 해당 절과 "결정 기록" 표를 갱신한다.
- 한글→히라가나 매핑을 바꾸면 `docs/CONVERSION_RULES.md` 와 `HangulTest` 를 함께 수정한다.
- 상태 머신 전이를 바꾸면 `docs/SPEC.md` §4 전이표와 `ImeEngineTest` 를 함께 수정한다.

## 브랜치·PR 규칙 (필수)
- 기본 작업 브랜치는 `develop`. `main` 에 직접 커밋하지 않는다.
- 적당한 작업 단위(기능 하나, 규칙 변경 하나 등)마다 `develop` 에서 작업 브랜치를 만든다.
- 브랜치 이름: `feature/{번호}-{작업내용}` (작업내용은 영어 kebab-case). 예: `feature/3-candidate-preview`
  - 번호는 이 브랜치로 만들 GitHub PR 번호와 맞춘다 (이슈·PR 공용 번호의 다음 값: `gh pr list --state all --limit 1`, `gh issue list --state all --limit 1` 중 큰 값 + 1).
- 작업 브랜치 안에서는 아래 커밋 규칙대로 작은 커밋을 쌓고, push 후 `develop` 대상 PR 을 `gh pr create --base develop` 으로 만든다.
- PR 을 만든 뒤 **병합 전에 사용자에게 "머지할까요?" 라고 묻고**, 승인을 받으면 병합한다: `gh pr merge {번호} --merge --delete-branch` (작은 커밋 이력 보존을 위해 merge commit 방식).
  - 승인은 해당 PR 에만 적용된다. PR 마다 다시 묻는다.
  - 묻기 전에 단위 테스트 통과를 확인한다. 충돌이 나면 `develop` 을 작업 브랜치에 병합해 해결한 뒤 묻는다.
  - 다음 작업이 병합 전 PR 에 의존하면 그 브랜치에서 새 브랜치를 따고 PR base 를 해당 브랜치로 지정한다.
  - 병합 후 로컬 `develop` 을 최신으로 맞추고 다음 브랜치를 만든다.
- PR 본문: 변경 요약, 커밋 목록 성격(문서/기능 등), 테스트 결과.

## 커밋 규칙 (필수)
- 작업 후 사용자가 요청하지 않아도 **자동으로 커밋**한다.
- 커밋은 하나의 작업으로 분리할 수 있는 **작은 단위**로 나눈다. (예: 서버 API 엔드포인트 하나 추가 정도)
- **변경 종류를 섞지 않는다**: 문서 수정 / 스타일(포맷·네이밍 등 동작 무관) / 리팩토링(동작 유지 구조 변경) / 기능 수정(버그 수정·동작 변경) / 기능 추가는 각각 별도 커밋으로 분리한다.
  - 기능과 함께 바뀌는 테스트는 해당 기능 커밋에 포함한다. 그 기능에 따른 문서 갱신은 별도의 문서 커밋으로 분리한다.
- 각 커밋은 가능한 한 단독으로 빌드가 깨지지 않도록 순서를 정한다. 커밋 메시지는 한국어로 작성한다.
- 사용자 IDE 설정 파일(`.idea/` 등)은 사용자가 요청하지 않는 한 커밋하지 않는다.

## 개발
- 단위 테스트: `./gradlew :app:testDebugUnitTest` (로직 패키지 `hangul`, `conversion`, `engine` 은 순수 Kotlin 으로 유지)
- 사전 재생성: `python tools/dictgen/build_dictionary.py` (Mozc 사전 다운로드 → `app/src/main/assets/dict/*.gzip`)
- 입력 문자열은 기기 내부에서만 처리한다. 네트워크 권한·외부 전송·입력 내용 저장을 추가하지 않는다.
