// SPEC-05 §2 — apps/api 는 Gradle 이 독립적으로 관리한다.
// npm workspaces(apps/web · packages/*)와 나란히 있을 뿐 서로를 모른다.
//
// 단일 Gradle 프로젝트다. 모듈별 서브프로젝트로 쪼개지 않는다 —
// PRIN-T03 이 요구하는 것은 패키지 경계이고, 그것은 이슈 001 의 경계 테스트가 강제한다.
rootProject.name = "mut-api"
