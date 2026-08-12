-- ISSUE-022 — 카테고리 소개 문구 (SPEC-06 §3.8, FR-COCKTAIL-031, NFR-S-07)
--
-- 이슈 본문은 "SPEC-06 에 이 테이블이 없다 → GAPS 에 등재하라" 고 적혀 있지만 그 사이
-- G-19 가 해소되며 SPEC-06 §3.8 에 이미 들어왔다. 새로 등재할 누락이 아니라 명세 구현이다.
--
-- 카테고리는 테이블이 아니라 enum 이다 (정본은 kr.kcocktail.common.taxonomy).
-- 그래서 문구만 담는 별도 표를 두고 (축, 슬러그) 로 붙인다. 카테고리 자체를 표로 만들면
-- 분류 축의 정본이 Kotlin 과 DB 두 곳이 되고, 그게 PRIN-T02 가 막으려는 상황이다.

CREATE TABLE category_intro (
    -- 축이 셋뿐인 것이 PRIN-P06 의 구현이다. 당도·도수·향맛은 카테고리가 아니라 필터다.
    axis  VARCHAR(8)  NOT NULL,
    slug  VARCHAR(24) NOT NULL,
    intro TEXT,

    -- 복합 PK 다 — 실체가 아니라 (축, 슬러그) 에 붙는 값이라서 대리키 id 를 두지 않는다.
    -- SPEC-06 §1.2 의 공통 컬럼 규약은 실체 테이블 대상이고, SchemaLint 도 구조로 판정한다.
    CONSTRAINT pk_category_intro PRIMARY KEY (axis, slug),

    CONSTRAINT ck_category_intro__axis CHECK (axis IN ('base', 'style', 'method')),

    -- ADR-0002 확정 슬러그. V009 의 세 CHECK 와 같은 목록이다 —
    -- 없는 카테고리에 문구만 남으면 어드민이 "왜 화면에 안 나오나" 를 추적하게 된다.
    CONSTRAINT ck_category_intro__slug CHECK (
        (axis = 'base' AND slug IN (
            'gin', 'vodka', 'whisky', 'rum', 'agave',
            'brandy', 'liqueur', 'wine', 'korean', 'non-alcoholic'))
        OR (axis = 'style' AND slug IN (
            'highball', 'sour', 'spirit-forward', 'spritz', 'tiki',
            'creamy', 'hot', 'frozen', 'shot'))
        OR (axis = 'method' AND slug IN ('build', 'shake', 'stir', 'blend', 'etc'))
    )
);

COMMENT ON TABLE category_intro IS
    'SPEC-06 §3.8 — 카테고리 고유 소개 문구 (FR-COCKTAIL-031·NFR-S-07, ISSUE-022).';
COMMENT ON COLUMN category_intro.intro IS
    'NULL 허용. D-1 이 NFR-S-07 의 "발행 차단" 을 경고로 확정했다 (DECISIONS §2).';
