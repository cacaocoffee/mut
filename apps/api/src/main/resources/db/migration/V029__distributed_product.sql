-- ISSUE #194 — 국내 유통 술 (GAPS G-41 · FR-INGREDIENT-001 · PRIN-P05)
--
-- ## 무엇을 담나
--
-- 식약처에 신고된 술 한 건이 한 행이다. 수입 술은 「수입식품 수입신고별 한글표시사항」
-- (data.go.kr 15110213), 국산 술은 「식품(첨가물)품목제조보고」(foodsafetykorea I1250) 에서 받는다.
-- 두 출처 다 **제품** 단위다 — `ingredient`(드라이 진) 와 단위가 다르다.
-- 둘을 잇는 표(`ingredient_product_match`)는 #195 가 만든다. 여기서는 받아 두기만 한다.
--
-- ## "유통 가능" 이 아니라 "신고가 있었다" 다
--
-- 식약처 데이터는 신고 사실만 말한다. 재고 소진·판매 중단은 모른다.
-- 그래서 컬럼 이름도 `last_reported_on`(마지막 신고일) 이지 "판매 중" 이 아니다.
--
-- ## 왜 raw 를 통째로 두나
--
-- 두 API 의 필드가 다르고(수입: 한글표시사항·원재료명·수출국 / 국산: 보고번호·업소명·보고일),
-- 어느 필드가 매핑에 쓸모 있을지 #195 를 만들어 봐야 안다. 지금 고른 컬럼만 남기면
-- 다시 받아야 한다. 하루 1만 건 한도라 다시 받는 비용이 크다.

CREATE TABLE distributed_product (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 어디서 받았나. manual 은 어드민이 직접 넣은 것 (국산 술이 API 에 안 잡힐 때).
    source             VARCHAR(16)  NOT NULL,
    -- 출처 안에서의 식별자. 수입: 수입업체|제품명(한글) — 신고 API 에 관리번호가 없다.
    -- 국산: 품목제조보고번호. 만드는 규칙은 scripts/lib/distributed-product.ts 다.
    source_key         VARCHAR(160) NOT NULL,

    name_ko            TEXT         NOT NULL,
    name_en            TEXT,
    -- 수입이면 수입업체, 국산이면 제조업소.
    importer_or_maker  TEXT,
    -- 해외 제조업소. 국산은 NULL.
    manufacturer       TEXT,
    origin_country     VARCHAR(80),
    -- 식품공전 유형 그대로 (위스키 · 리큐르 · 일반증류주 · 과실주 …). 스크립트가 주류만 남긴다.
    food_type          VARCHAR(40)  NOT NULL,
    last_reported_on   DATE,

    raw                JSONB        NOT NULL DEFAULT '{}',

    CONSTRAINT uq_distributed_product__source_key UNIQUE (source, source_key),
    CONSTRAINT ck_distributed_product__source CHECK (source IN
        ('mfds_import', 'mfds_domestic', 'manual'))
);

CREATE TRIGGER distributed_product_set_updated_at BEFORE UPDATE ON distributed_product
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- #195 의 매칭 배치가 유형별로 훑는다.
CREATE INDEX ix_distributed_product__food_type ON distributed_product (food_type);

COMMENT ON TABLE distributed_product IS
    '식약처 신고 기준 국내 유통 술. 출처의 사본이라 물리 삭제를 막지 않는다 — 시드가 통째로 다시 채운다.';
