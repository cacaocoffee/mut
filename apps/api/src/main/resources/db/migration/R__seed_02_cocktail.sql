-- ISSUE-036 — 프로토타입 칵테일 49종 시드 (SPEC-01 §6 · SPEC-06 §6).
--
-- ⚠️ **손으로 고치지 않는다.** `packages/domain/src/data.ts` 를 고치고
--    `npx tsx scripts/seed-from-prototype.ts` 로 다시 만든다.
--    변환 규칙은 그 스크립트에 있고, 그것이 이관 근거다.

-- ## draft 로 넣는다
--
-- `tasting_note` 가 발행 필수인데(GATE-COCKTAIL-01) 프로토타입에 그 필드가 없다.
-- **자동 생성하지 않는다** — `PRIN-P03` 이 "만들어보지 않은 것은 쓰지 않는다" 이고,
-- 향과 맛 서술이야말로 그 원칙이 지키려는 바로 그 값이다.
--
-- 에디터가 서술을 채우고 어드민에서 발행한다 (이슈 025 의 `NFR-O-01` 경로).
-- 그때까지 24종은 draft 이고, 공개 조회에는 안 나온다.

DO $seed$
DECLARE
    v_cocktail_id   BIGINT;
    v_recipe_id     BIGINT;
    v_ingredient_id BIGINT;
BEGIN
    -- 네그로니 (Negroni)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'negroni') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'negroni', '네그로니', 'Negroni', '동량 배합의 교본. 캄파리의 쓴맛과 베르무트의 단맛이 진의 주니퍼 위에서 정확히 상쇄된다.',
            'gin', 'spirit-forward', 'stir',
            'dry', '올드 패션드',
            24,
            true, '## 쓴맛을 배우는 첫 잔



아메리카노에 소다 대신 진을 넣어달라는 주문에서 시작됐다는 이야기는 확인된 문서가 없다. 다만 1919년 피렌체의 카페 카소니에서 이 배합이 팔리고 있었다는 정황은 여러 기록에서 겹친다.

세 재료를 같은 양으로 쓰는 구조 덕분에 네그로니는 레시피가 아니라 비율로 기억된다. 진을 45ml로 올리면 드라이해지고, 베르무트를 45ml로 올리면 디저트에 가까워진다. 아카이브에서는 1:1:1을 기준값으로 둔다.',
            '1919년경', '피렌체, 이탈리아', '카밀로 네그로니 백작 (구전)',
            ARRAY[2, 1, 5, 4, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, '아페롤 — 쓴맛이 절반으로 줄고 오렌지 향이 앞섭니다. 도수도 2% 정도 내려갑니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 30, 'ml', NULL, '푼트 에 메스 — 쓴맛이 더 강해집니다. 반대로 카르파노 안티카는 바닐라 톤이 올라옵니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '믹싱 글라스에 얼음을 가득 채우고 세 재료를 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '20~25회 스터. 표면에 서리가 앉으면 충분하다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '큰 얼음을 넣은 올드 패션드 글라스에 스트레인한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '오렌지 필의 껍질을 짜 오일을 뿌리고 글라스에 넣는다.');
    END IF;
    -- 마티니 (Dry Martini)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'martini') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'martini', '마티니', 'Dry Martini', '드라이 베르무트의 양이 전부를 결정한다. 온도는 −3℃ 이하로 유지한다.',
            'gin', 'spirit-forward', 'stir',
            'dry', '칵테일',
            30,
            true, '## 가장 적게 넣는 기술



마티니의 역사는 베르무트가 줄어드는 역사다. 19세기 마티네즈는 단맛이 분명했고, 20세기 중반에 이르러 베르무트는 잔을 적시는 정도로 남았다.

아카이브 기준값은 6:1이다. 여기서 베르무트를 20ml까지 올리면 50-50, 5ml 이하로 내리면 사실상 차가운 진이다.',
            '1888년 이전', '뉴욕, 미국', '불명 — 마티네즈에서 파생',
            ARRAY[1, 1, 2, 4, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 10, 'ml', NULL, '릴레 블랑 — 단맛과 감귤 향이 조금 붙습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1 dash', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '믹싱 글라스와 잔을 미리 얼려둔다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음과 재료를 넣고 25회 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼린 칵테일 글라스에 더블 스트레인한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '레몬 필로 오일을 뿌린다. 올리브를 쓰면 짠맛 계열로 바뀐다.');
    END IF;
    -- 김렛 (Gimlet)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'gimlet') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'gimlet', '김렛', 'Gimlet', '라임 코디얼의 단맛과 진의 골격이 만나는 가장 단순한 사워.',
            'gin', 'sour', 'shake',
            'semi_dry', '칵테일',
            26,
            true, '## 비타민C의 칵테일



라임 코디얼은 원래 항해 중 비타민 결핍을 막기 위한 보급품이었다. 진과 섞은 것은 그 다음 일이다.

생라임과 시럽으로 만들면 산미가 날카롭고, 코디얼로 만들면 향이 둥글다. 두 방식은 다른 음료로 취급해도 무리가 없다.',
            '1928년 기록', '런던, 영국', '영국 해군 관행에서 유래',
            ARRAY[3, 4, 1, 2, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, '라임 코디얼 — 원형에 가깝고 단맛이 둥글어집니다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '셰이커에 재료를 넣고 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '12초 하드 셰이크.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼린 칵테일 글라스에 더블 스트레인한다.');
    END IF;
    -- 진토닉 (Gin & Tonic)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'gintonic') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'gintonic', '진토닉', 'Gin & Tonic', '희석의 정확도가 맛을 만든다. 얼음은 크고 단단할수록 좋다.',
            'gin', 'highball', 'build',
            'semi_dry', '하이볼',
            12,
            true, '## 약이었던 배합



토닉의 키니네는 말라리아 예방약이었다. 쓴맛을 견디기 위해 진과 설탕, 라임을 더한 것이 이 잔의 시작이다.

토닉의 당도가 완성도를 좌우한다. 아카이브는 진 1 : 토닉 2.5~3을 기준으로 잡는다.',
            '1850년대', '인도 주둔 영국군', '키니네 복용 관행',
            ARRAY[2, 2, 3, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'tonic-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-wedge';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '하이볼 글라스에 얼음을 가득 채워 잔을 식힌다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '진을 붓고 토닉을 글라스 벽면을 따라 천천히 따른다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '바 스푼으로 한 번만 들어올린다. 탄산을 지키는 것이 목적이다.');
    END IF;
    -- 사우스사이드 (Southside)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'southside') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'southside', '사우스사이드', 'Southside', '민트를 넣은 진 사워. 모히토의 진 버전으로 읽어도 된다.',
            'gin', 'sour', 'shake',
            'semi_dry', '칵테일',
            22,
            true, '## 금주법의 잔



거칠던 밀주 진의 맛을 감추기 위해 민트와 레몬을 썼다는 설명이 오래 따라다닌다.

민트를 세게 눌러 으깨면 풀비린내가 난다. 향만 깨우는 정도가 기준이다.',
            '1920년대', '시카고 / 뉴욕', '금주법 시대 클럽',
            ARRAY[3, 4, 1, 4, 3]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 50, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 18, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'mint';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '8장', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '셰이커에 민트를 넣고 가볍게 눌러 향만 낸다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '나머지 재료와 얼음을 넣고 10초 셰이크.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '더블 스트레인해 민트 잎 하나를 띄운다.');
    END IF;
    -- 모스코 뮬 (Moscow Mule)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'mule') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'mule', '모스코 뮬', 'Moscow Mule', '진저비어의 매운맛이 중심. 보드카는 뼈대만 세운다.',
            'vodka', 'highball', 'build',
            'semi_sweet', '구리 머그',
            12,
            true, '## 재고 처리의 성공작



팔리지 않던 보드카와 진저비어를 묶어 팔기 위한 상업적 발명이었다.

구리 머그는 마케팅에서 왔지만, 열전도가 빨라 실제로 잔이 더 차게 느껴진다.',
            '1941년', '로스앤젤레스, 미국', '잭 모건 · 존 마틴',
            ARRAY[3, 3, 1, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vodka';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'ginger-beer';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 120, 'ml', NULL, '진저에일 — 매운맛이 크게 줄어듭니다. 생강 시럽 5ml를 더해 보완합니다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '머그에 얼음을 채우고 보드카와 라임을 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '진저비어로 채운 뒤 한 번만 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '라임 웨지를 올린다.');
    END IF;
    -- 에스프레소 마티니 (Espresso Martini)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'espresso') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'espresso', '에스프레소 마티니', 'Espresso Martini', '크레마 층이 완성도의 지표. 커피는 뽑은 직후에 쓴다.',
            'vodka', 'creamy', 'shake',
            'semi_sweet', '칵테일',
            20,
            true, '## 깨워달라는 주문



한 손님이 정신을 차리게 해달라고 부탁한 데서 나왔다는 일화가 널리 알려져 있다.

거품은 커피의 오일과 이산화탄소에서 나온다. 뽑고 1분이 지난 에스프레소로는 같은 층이 생기지 않는다.',
            '1983년', '런던, 영국', '딕 브래드셀',
            ARRAY[3, 1, 4, 4, 3]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'nutty');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vodka';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'espresso';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'coffee-liqueur';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, '깔루아 대신 미스터 블랙 — 단맛이 줄고 커피 강도가 올라갑니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 5, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '에스프레소를 뽑아 곧바로 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 15초 강하게 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '더블 스트레인하고 크레마가 자리 잡을 때까지 20초 둔다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '커피 원두 세 알을 올린다.');
    END IF;
    -- 블러디 메리 (Bloody Mary)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'bloody') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'bloody', '블러디 메리', 'Bloody Mary', '짠맛·감칠맛 계열. 단맛이 거의 없는 유일한 브런치 잔.',
            'vodka', 'highball', 'build',
            'dry', '하이볼',
            12,
            true, '## 식사에 가까운 잔



스파이스와 산·염을 함께 쓰는 구조라 칵테일보다 수프의 조리 논리에 가깝다.

정답 레시피가 없는 대신 균형 원칙이 있다. 산 : 염 : 매운맛을 각각 따로 조절한다.',
            '1921년경', '파리 → 뉴욕', '페르낭 프티오',
            ARRAY[1, 3, 2, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vodka';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'tomato-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'worcestershire-tabasco';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '각 2 dash', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '하이볼 글라스에 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '모든 재료를 넣고 롤링(잔 사이를 오가며 섞기)한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '셀러리와 후추로 마무리한다.');
    END IF;
    -- 코즈모폴리탄 (Cosmopolitan)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'cosmo') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'cosmo', '코즈모폴리탄', 'Cosmopolitan', '크랜베리의 색과 트리플 섹의 오렌지 향. 산미가 축이 된다.',
            'vodka', 'sour', 'shake',
            'semi_sweet', '칵테일',
            22,
            true, '## 색이 만든 유행



1990년대에 이 잔이 팔린 이유의 절반은 맛이 아니라 색이었다.

크랜베리를 60ml까지 늘리면 주스에 가까워진다. 30ml가 산미와 색을 모두 지키는 지점이다.',
            '1987년', '샌프란시스코, 미국', '토비 체키니 (통설)',
            ARRAY[3, 4, 1, 3, 3]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'citron-vodka';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'cointreau';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, '트리플 섹 — 단맛이 더 직선적이고 향의 층이 얇아집니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'cranberry-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 30, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '모든 재료와 얼음을 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '10초 셰이크.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼린 칵테일 글라스에 더블 스트레인하고 오렌지 필로 오일을 뿌린다.');
    END IF;
    -- 올드 패션드 (Old Fashioned)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'oldfashioned') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'oldfashioned', '올드 패션드', 'Old Fashioned', '술·설탕·비터스·물. 칵테일의 정의 그 자체.',
            'whisky', 'spirit-forward', 'stir',
            'semi_dry', '올드 패션드',
            32,
            true, '## 원형이라는 이름



“옛날식으로”라는 주문이 그대로 이름이 됐다. 즉 이 잔은 어떤 칵테일보다 오래된 구조를 가리킨다.

희석이 유일한 변수다. 큰 얼음 하나로 천천히 마시는 전제로 설계된 배합이다.',
            '1880년대', '루이빌, 미국', '펜던니스 클럽 (통설)',
            ARRAY[2, 1, 3, 4, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'bourbon-rye';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, '라이 위스키 — 스파이스가 앞서고 단맛이 마릅니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rich-simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 7, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'angostura-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '2 dash', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 시럽과 비터스를 넣고 섞는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '위스키를 절반 붓고 얼음 하나로 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '남은 위스키와 큰 얼음을 넣고 20회 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '오렌지 필로 오일을 뿌린다.');
    END IF;
    -- 맨해튼 (Manhattan)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'manhattan') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'manhattan', '맨해튼', 'Manhattan', '라이의 스파이스와 스위트 베르무트. 네그로니와 마티니 사이.',
            'whisky', 'spirit-forward', 'stir',
            'semi_dry', '칵테일',
            30,
            true, '## 비율의 문제



2:1은 기준이고, 1:1은 퍼펙트에 가깝다. 베르무트의 상태가 이 잔의 수명을 결정한다.

개봉한 베르무트는 냉장 보관해도 3주가 한계다. 맨해튼이 실패하는 대부분의 이유가 여기에 있다.',
            '1880년대', '뉴욕, 미국', '맨해튼 클럽 (통설)',
            ARRAY[3, 1, 3, 4, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rye-whiskey';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'angostura-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '2 dash', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'cherry';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1개', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '믹싱 글라스에 재료와 얼음을 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '25회 스터.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼린 칵테일 글라스에 스트레인하고 체리를 넣는다.');
    END IF;
    -- 위스키 사워 (Whiskey Sour)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'whiskeysour') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'whiskeysour', '위스키 사워', 'Whiskey Sour', '사워 공식(술 2 : 산 1 : 당 1)의 표준 예시.',
            'whisky', 'sour', 'shake',
            'semi_sweet', '쿠페',
            20,
            true, '## 공식으로서의 사워



사워는 이름이 아니라 비율이다. 술과 산, 당의 삼각형만 지키면 재료는 교체 가능하다.

흰자는 맛보다 질감을 위한 재료다. 넣지 않으면 산미가 더 뚜렷해진다.',
            '1862년 수록', '미국', '제리 토머스 저서',
            ARRAY[3, 5, 1, 3, 3]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'bourbon';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 50, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 25, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'egg-white';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '15ml', '아쿠아파바 15ml — 비건 대체가 가능하고 거품이 더 안정적입니다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '얼음 없이 재료를 넣고 15초 드라이 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 넣고 다시 12초 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '쿠페 글라스에 더블 스트레인하고 비터스로 표면에 점을 찍는다.');
    END IF;
    -- 페니실린 (Penicillin)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'penicillin') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'penicillin', '페니실린', 'Penicillin', '생강·꿀·레몬에 아일라 위스키의 연기를 얹은 현대 고전.',
            'whisky', 'sour', 'shake',
            'semi_sweet', '올드 패션드',
            22,
            true, '## 현대의 고전



2000년대 이후 만들어진 칵테일 중 가장 널리 복제된 배합이다.

연기를 섞지 않고 위에 띄우는 것이 핵심이다. 첫 향과 끝 맛이 분리된다.',
            '2005년', '뉴욕, 미국', '샘 로스',
            ARRAY[3, 4, 2, 5, 3]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'smoky');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'blended-scotch';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 50, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'islay-single-malt';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 7, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 22, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'honey-ginger-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 22, 'ml', NULL, '꿀 시럽 + 생강즙 5ml로 즉석 대체가 가능합니다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '아일라를 뺀 재료를 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 넣은 잔에 스트레인한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '아일라 위스키를 표면에 띄운다(플로트).');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '생강 절임을 올린다.');
    END IF;
    -- 불바디에 (Boulevardier)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'boulevardier') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'boulevardier', '불바디에', 'Boulevardier', '네그로니의 위스키 버전. 진보다 무게가 있고 단맛이 길다.',
            'whisky', 'spirit-forward', 'stir',
            'semi_dry', '올드 패션드',
            26,
            true, '## 잡지에서 나온 잔



파리에서 발행된 소책자에 실린 배합이 그대로 이름과 함께 남았다.

위스키를 45ml로 올려 캄파리보다 우위에 두는 것이 현행 표준이다.',
            '1927년', '파리, 프랑스', '어스킨 그웬 (잡지 편집자)',
            ARRAY[3, 1, 4, 4, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'bourbon-rye';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 30, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '믹싱 글라스에 재료와 얼음을 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '25회 스터.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '큰 얼음 위에 스트레인하고 오렌지 필을 짠다.');
    END IF;
    -- 다이키리 (Daiquiri)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'daiquiri') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'daiquiri', '다이키리', 'Daiquiri', '럼의 품질이 그대로 드러난다. 세 재료 뒤에 숨을 곳이 없다.',
            'rum', 'sour', 'shake',
            'semi_dry', '쿠페',
            24,
            true, '## 바텐더의 시험지



바를 평가할 때 다이키리를 주문하는 관행은 이 잔이 기술을 감추지 못하기 때문이다.

시럽 18ml는 라임의 산도에 따라 조정한다. 라임이 날카로운 계절에는 20ml까지 올린다.',
            '1898년경', '산티아고, 쿠바', '제닝스 콕스 (통설)',
            ARRAY[3, 4, 1, 3, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'white-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 25, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 18, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '재료와 얼음을 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '12초 하드 셰이크.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼린 쿠페 글라스에 더블 스트레인한다.');
    END IF;
    -- 모히토 (Mojito)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'mojito') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'mojito', '모히토', 'Mojito', '민트 향, 라임 산미, 탄산의 세 층이 분리되어 있어야 한다.',
            'rum', 'highball', 'build',
            'semi_sweet', '하이볼',
            13,
            true, '## 눌러 으깨지 않는다



민트를 으깨면 엽록소의 쓴맛이 나온다. 향이 올라올 정도로만 압을 준다.

크러시드 아이스는 희석 속도가 빠르다. 그래서 시럽이 20ml까지 들어간다.',
            '19세기', '하바나, 쿠바', '불명',
            ARRAY[3, 3, 1, 4, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'white-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'mint';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '10장', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, 60, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 민트와 시럽을 넣고 가볍게 누른다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '라임과 럼을 붓고 크러시드 아이스를 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '스푼으로 아래에서 위로 한 번 들어올린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '소다로 채우고 민트 다발을 올린다.');
    END IF;
    -- 마이 타이 (Mai Tai)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'maitai') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'maitai', '마이 타이', 'Mai Tai', '오르자(아몬드 시럽)가 향의 중심. 과일 주스는 들어가지 않는다.',
            'rum', 'tiki', 'shake',
            'semi_sweet', '올드 패션드',
            26,
            true, '## 파인애플은 없다



대량 판매용 마이 타이가 과일 주스로 채워지며 원형과 멀어졌다. 원 배합에 파인애플은 없다.

두 종류의 럼을 쓰는 이유는 무게(자메이카)와 풀 향(아그리콜)을 동시에 얻기 위해서다.',
            '1944년', '오클랜드, 미국', '빅터 버제론',
            ARRAY[4, 4, 1, 5, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'tiki');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'nutty');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'jamaican-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rhum-agricole';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-curacao';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, '쿠앵트로 — 색이 맑아지고 단맛이 가벼워집니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orgeat';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, 22, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '크러시드 아이스와 재료를 셰이커에 넣고 짧게 흔든다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '잔에 얼음까지 그대로 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '짜낸 라임 껍질과 민트를 올린다.');
    END IF;
    -- 다크 앤 스토미 (Dark ’n’ Stormy)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'darknstormy') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'darknstormy', '다크 앤 스토미', 'Dark ’n’ Stormy', '층을 만들어 마시는 구조. 섞지 않고 낸다.',
            'rum', 'highball', 'build',
            'semi_sweet', '하이볼',
            14,
            true, '## 상표가 된 배합



버뮤다에서는 이 이름과 배합이 상표로 관리된다.

럼을 띄우면 첫 모금은 생강, 마지막은 당밀이다. 섞으면 이 대비가 사라진다.',
            '20세기 초', '버뮤다', '고슬링스 럼',
            ARRAY[4, 2, 1, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dark-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'ginger-beer';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-wedge';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 얼음과 진저비어를 먼저 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '다크 럼을 스푼 뒤로 흘려 위에 띄운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '라임을 곁들여 그대로 낸다.');
    END IF;
    -- 마르가리타 (Margarita)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'margarita') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'margarita', '마르가리타', 'Margarita', '소금·산·단맛 세 축의 균형. 데킬라는 100% 아가베를 쓴다.',
            'agave', 'sour', 'shake',
            'semi_dry', '쿠페',
            24,
            true, '## 소금은 절반만



림 전체에 소금을 묻히면 선택권이 사라진다. 절반만 묻히는 것이 현행 관행이다.

쿠앵트로를 아가베 시럽으로 바꾸면 데킬라의 식물성 향이 훨씬 선명해진다.',
            '1930~40년대', '멕시코 / 미국 국경', '다수의 주장',
            ARRAY[3, 5, 1, 3, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'blanco-tequila';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 50, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'cointreau';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, '트리플 섹 — 향의 폭이 좁아집니다. 아가베 시럽 10ml로 바꾸면 토미스 스타일이 됩니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 25, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'salt';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '림 절반', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔 테두리 절반에만 소금을 묻힌다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '재료와 얼음을 넣고 12초 셰이크.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '더블 스트레인하고 라임 휠을 올린다.');
    END IF;
    -- 팔로마 (Paloma)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'paloma') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'paloma', '팔로마', 'Paloma', '멕시코에서 실제로 가장 많이 마시는 데킬라 롱드링크.',
            'agave', 'highball', 'build',
            'semi_sweet', '하이볼',
            14,
            true, '## 소금 한 꼬집



소금은 짠맛을 위해서가 아니라 자몽의 쓴맛을 눌러 단맛을 끌어올리기 위해 들어간다.

생자몽으로 만들면 완전히 다른 잔이 된다. 아카이브는 두 방식을 모두 표준으로 본다.',
            '1950년대', '멕시코', '불명',
            ARRAY[3, 3, 2, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'blanco-tequila';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 50, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'grapefruit-soda';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, '생자몽 60ml + 소다 60ml + 시럽 5ml로 대체하면 단맛이 크게 줄어듭니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'salt';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1핀치', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 얼음을 채우고 데킬라와 라임을 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '자몽 소다로 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '소금 한 꼬집을 넣고 한 번 젓는다.');
    END IF;
    -- 소주 하이볼 (Soju Highball)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'sojuhighball') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'sojuhighball', '소주 하이볼', 'Soju Highball', '증류식 소주의 곡물 향을 탄산으로 늘린 구조. 희석률이 관건.',
            'korean', 'highball', 'build',
            'semi_dry', '하이볼',
            9,
            true, '## 희석의 문화



일본식 하이볼 문법이 증류식 소주에 그대로 옮겨오면서 정착한 형식이다.

25도 소주는 1:2.5, 40도대는 1:4를 기준으로 잡는다. 곡물 향이 남는 선이다.',
            '2010년대', '서울, 한국', '국내 바 씬',
            ARRAY[2, 1, 1, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'distilled-soju';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, '40도대 소주를 쓰면 30ml로 줄입니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔과 소주를 미리 차게 둔다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 가득 채우고 소주를 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '탄산수를 벽면을 따라 붓고 한 번만 들어올린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '레몬 필의 오일을 뿌린다.');
    END IF;
    -- 문배 올드 패션드 (Munbae Old Fashioned)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'munbae') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'munbae', '문배 올드 패션드', 'Munbae Old Fashioned', '문배주의 배·수수 향을 올드 패션드 구조에 넣은 응용 배합.',
            'korean', 'spirit-forward', 'stir',
            'semi_dry', '올드 패션드',
            28,
            true, '## 전통주의 좌표



문배주는 배를 넣지 않는데도 배 향이 난다. 이 향은 수수와 좁쌀의 발효에서 온다.

조청은 설탕보다 점도가 높아 5~8ml에서 이미 충분한 무게가 붙는다.',
            '2010년대 응용', '한국', '아카이브 편집부 배합',
            ARRAY[2, 1, 3, 5, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'smoky');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'munbaeju-40';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'jocheong-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 8, 'ml', NULL, '꿀 시럽 — 곡물 향이 줄고 꽃 향이 붙습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'angostura-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '2 dash', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'pear-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 조청 시럽과 비터스를 넣고 섞는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '문배주를 붓고 큰 얼음으로 20회 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '배 껍질의 향을 뿌리고 넣는다.');
    END IF;
    -- 막걸리 콜라다 (Makgeolli Colada)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'makgeolli') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'makgeolli', '막걸리 콜라다', 'Makgeolli Colada', '막걸리의 유산 향과 코코넛·파인애플. 가장 단 항목.',
            'korean', 'creamy', 'shake',
            'sweet', '하이볼',
            8,
            true, '## 단맛의 상한



아카이브의 24개 항목 중 당도가 가장 높다. 디저트 자리에 놓기 위한 배합이다.

막걸리는 살균/비살균에 따라 산미 차이가 크다. 비살균 제품은 파인애플을 30ml로 줄인다.',
            '2020년대', '한국', '아카이브 편집부 배합',
            ARRAY[5, 2, 1, 3, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'tiki');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'makgeolli';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 90, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'white-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, '생략하면 무알콜에 가까운 3% 대가 됩니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'coconut-cream';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 25, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'pineapple-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 40, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '막걸리를 흔들어 침전물을 고르게 섞는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '모든 재료를 얼음과 함께 짧게 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '크러시드 아이스 위에 붓고 파인애플을 올린다.');
    END IF;
    -- 시트러스 슈럽 (Citrus Shrub (NA))
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'shrub') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'shrub', '시트러스 슈럽', 'Citrus Shrub (NA)', '식초 기반 시럽으로 산미의 층을 만든 무알콜 항목.',
            'non-alcoholic', 'highball', 'build',
            'semi_sweet', '하이볼',
            0,
            true, '## 알코올 없는 골격



무알콜 음료가 심심해지는 이유는 대개 산미와 쓴맛이 없기 때문이다. 식초는 그 자리를 메운다.

식초의 양이 3ml만 넘어도 균형이 무너진다. 슈럽 시럽으로 미리 배합해 쓰는 편이 안정적이다.',
            '17세기 보존법', '유럽 → 현대 바', '식초 보존 전통',
            ARRAY[3, 4, 2, 4, 0]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'citrus-shrub-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 35, 'ml', NULL, '애플사이다 비니거 15ml + 설탕 시럽 20ml로 즉석 조합이 가능합니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 140, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'grapefruit-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rosemary';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1줄기', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 얼음을 채우고 슈럽과 자몽을 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '탄산수로 채우고 한 번 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '로즈마리를 손바닥에 쳐 향을 내고 올린다.');
    END IF;
    -- 콥스 리바이버 넘버 2 (Corpse Reviver No.2)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'corpsereviver2') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'corpsereviver2', '콥스 리바이버 넘버 2', 'Corpse Reviver No.2', '시트러스의 새콤달콤함 위로 릴레의 와인 뉘앙스가 겹치고, 진의 보타니컬이 중심을 잡는다. 압생트는 끝에 미묘하게만 스친다.',
            'gin', 'sour', 'shake',
            'semi_sweet', '칵테일',
            20,
            true, '## 죽은 자를 깨우는 잔



콥스 리바이버라는 이름은 19세기부터 있었지만 특정 레시피가 아니라 ''해장을 위한 술''이라는 뜻으로 쓰였다. 1861년 런던의 잡지 《펀치》가 슬링·스톤 월과 함께 이 이름을 언급한 것이 가장 오래된 기록이다.

지금의 넘버 2는 1930년 사보이 칵테일 북에서 굳어졌다. 책은 레시피 아래에 ''4잔을 연속으로 빠르게 마시면 되살아난 시체도 다시 죽을 것''이라고 적어 두었다. 원전은 동량 배합이지만 균형이 좋다고 보기 어려워 아카이브는 22.5ml 4등분을 기준으로 둔다.',
            '1930년', '런던, 영국', '해리 크래독 《The Savoy Cocktail Book》',
            ARRAY[3, 4, 1, 4, 3]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 22.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lillet-blanc';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 22.5, 'ml', NULL, '코키 아메리카노 — 단종된 키나 릴레에 더 가깝다는 평이 있습니다. 쓴맛이 조금 올라옵니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'cointreau';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 22.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 22.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'absinthe';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, NULL, NULL, '1 dash (잔 린싱)', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '압생트 외의 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '차게 해 둔 잔에 압생트를 뿌려 린싱한다. 스프레이가 없으면 1바스푼을 넣고 잔을 돌린 뒤 남은 것을 버린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '셰이커에 얼음을 채우고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '린싱한 잔에 따른다.');
    END IF;
    -- 베스퍼 (Vesper)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'vesper') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'vesper', '베스퍼', 'Vesper', '잔을 입에 가져가면 레몬 향이 먼저 오고, 보타니컬과 술 자체의 단맛 뒤로 릴레의 와인스러운 뉘앙스가 살짝 남는다. 깔끔하지만 도수는 상당히 높다.',
            'gin', 'spirit-forward', 'shake',
            'dry', '칵테일',
            30,
            true, '## 본드가 직접 읊은 배합



제임스 본드가 바텐더에게 배합을 불러 주는 장면에서 등장한다. 고든스 3, 보드카 1, 키나 릴레 0.5를 얼음처럼 차가워질 때까지 셰이크하고 레몬 껍질을 크게 저민 것. 이름은 연인 베스퍼 린드에서 따왔고, 이유는 ''한 번 맛보고 나면 다른 건 마실 수 없기 때문''이었다.

원전의 키나 릴레는 1986년에 퀴닌을 줄이고 단맛을 올리며 릴레 블랑으로 바뀌었다. 술만 들어가는데 왜 셰이킹인가는 오래된 질문인데, 스터로 만들어도 문제는 없고 차이는 결국 잔에 들어가는 물의 양이다.',
            '1953년', '소설 《카지노 로얄》', '이언 플레밍',
            ARRAY[1, 1, 1, 4, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vodka';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lillet-blanc';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 8.75, 'ml', NULL, '코키 아메리카노 — 원전의 키나 릴레에 더 가깝습니다. 퀴닌의 쓴맛이 붙습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '레몬 껍질 외의 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '잔에 따른 뒤 레몬 껍질을 짜 오일을 뿌린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '껍질을 다듬어 장식한다.');
    END IF;
    -- 화이트 네그로니 (White Negroni)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'whitenegroni') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'whitenegroni', '화이트 네그로니', 'White Negroni', '진의 보타니컬이 중심을 잡고 수즈의 달큰하면서 쌉쌀한 뿌리 식물 캐릭터가 은은하게 올라온다. 클래식 네그로니보다 한껏 가볍고 섬세하다.',
            'gin', 'spirit-forward', 'stir',
            'semi_dry', '올드 패션드',
            24,
            true, '## 프랑스 재료로 만든 네그로니



2001년 여름, 플리머스 진의 디렉터 닉 블랙넬과 런던의 바텐더 웨인 콜린스는 각각 주류 박람회와 칵테일 대회 때문에 프랑스에 있었다. 보르도 근처 메독의 작은 마을에는 괜찮은 바가 없었고, 둘은 리쿼샵에서 네그로니 재료를 찾다가 ''프랑스 재료로 만들어 보자''는 데 이르렀다.

그래서 캄파리 자리에 수즈, 스위트 베르무트 자리에 릴레 블랑이 들어갔다. 이름은 블랙넬이 붙였는데, 네그로니의 어두운 적갈색과 정반대로 부르자는 뜻이었다. 실제 색은 금색에 가깝다.',
            '2001년', '메독, 프랑스', '웨인 콜린스 · 닉 블랙넬',
            ARRAY[2, 1, 4, 4, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lillet-blanc';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'suze';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 22.5, 'ml', NULL, '아베즈 — 같은 용담(젠티아나) 리큐르입니다. 국내 수입되는 대안이지만 맛은 조금 다릅니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '락 글라스에 모든 재료를 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '레몬 껍질의 오일을 잔 위에 뿌린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '껍질을 다듬어 잔에 넣는다. 자몽 껍질을 쓰면 쌉쌀함이 더 산다.');
    END IF;
    -- 스테이 업 레이트 (Stay Up Late)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'stayuplate') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'stayuplate', '스테이 업 레이트', 'Stay Up Late', '진 피즈에 꼬냑을 얹은 구조라 사이드카와 진 피즈를 매시업한 듯한 맛이 난다. 유자는 전혀 들어가지 않는데 유자청 같은 인상이 남는다.',
            'gin', 'sour', 'shake',
            'semi_sweet', '하이볼',
            11,
            true, '## 나이트클럽에서 온 이름



언론인 루시우스 비비가 1946년에 쓴 《스토크 클럽 바 북》 부록에 실린 칵테일이다. 스토크 클럽은 1929년부터 1965년까지 뉴욕 맨해튼에서 운영된 나이트클럽으로 유명인이 자주 찾던 곳이었다.

부록은 클럽 스태프를 취재해 덧붙인 목록인데, 이 잔은 모자 관리 부서의 베로니카 해롤드가 올린 것이다. 그녀가 만든 것인지는 알 수 없지만 이름만큼은 나이트클럽에 잘 어울린다. 원전은 비율이 꽤 달라 아카이브는 조정된 배합을 기준으로 둔다.',
            '1946년', '뉴욕, 미국', '《The Stork Club Bar Book》 부록 · 베로니카 해롤드',
            ARRAY[3, 4, 1, 4, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 40, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vsop-cognac';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 10, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rich-simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 10, 'ml', NULL, '설탕 2 : 물 1로 끓여 만듭니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, 80, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 6, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '탄산수를 제외한 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '긴 잔에 얼음을 채우고 셰이커에서 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '탄산수를 적당량 붓고 얼음을 위아래로 살짝 들었다 놓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '레몬 껍질의 오일을 짜 뿌리고 다듬어 넣는다.');
    END IF;
    -- 카이칸 피즈 (Kaikan Fizz)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'kaikanfizz') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'kaikanfizz', '카이칸 피즈', 'Kaikan Fizz', '우유가 들어간 진 피즈. 일반 진 피즈보다 부드럽고 실키하며, 칼피스나 밀키스를 살짝 떠올리게 하는 맛이 난다.',
            'gin', 'sour', 'shake',
            'semi_sweet', '하이볼',
            10,
            true, '## 우유로 변장한 진 피즈



전후 도쿄카이칸은 연합군 최고사령부에 의해 ''아메리칸 클럽 오브 도쿄''라는 장교 클럽으로 쓰였다. 가장 널리 알려진 이야기는 장교들이 낮부터 몰래 마시기 위해 진 피즈에 우유를 넣어 달라고 했다는 것이다. 마치 우유를 마시는 것처럼 보이도록.

우유와 레몬을 같이 쓰는 건 사실 좋은 조합이 아니다. 레몬의 산이 우유의 단백질을 응고시키기 때문이다. 게다가 탄산수를 세게 부으면 거품이 넘친다. 그래서 일반 진 피즈보다 만들기 까다로운 잔으로 통한다.',
            '1945~1952년', '도쿄, 일본', '도쿄카이칸 메인 바',
            ARRAY[3, 3, 1, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 11, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rich-simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 9, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'milk';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, 60, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '탄산수를 제외한 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 넣고 다소 길게 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '긴 잔에 얼음을 채우고 셰이커에서 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '탄산수를 살살 붓는다. 세게 부으면 거품이 순식간에 넘친다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '얼음을 위아래로 두세 번 들었다 놓는다.');
    END IF;
    -- 카이피로스카 (Caipiroska)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'caipiroska') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'caipiroska', '카이피로스카', 'Caipiroska', '달고 상큼해 리프레시하기 좋다. 맛이 비교적 직관적이고 단순한 편이다.',
            'vodka', 'sour', 'etc',
            'semi_sweet', '올드 패션드',
            22,
            true, '## 보드카로 바꾼 국민 칵테일



브라질의 국민 칵테일 카이피리냐에서 기주를 카샤사에서 보드카로 바꾼 변형이다. 카이피로브스카라고도 부른다. 언제 만들어졌는지는 정확히 알 수 없고, 대체로 보드카가 전 세계적으로 인기를 끌던 80~90년대로 본다.

국내에서는 카샤사를 구할 수는 있지만 쓸 곳이 많지 않다. 보드카가 훨씬 구하기 쉬워 원형보다 이쪽을 권하게 된다. 딸기나 블루베리를 같이 넣는 변형도 많다.',
            '1980~90년대 추정', '브라질', '미상',
            ARRAY[3, 5, 0, 3, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vodka';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 55, 'ml', NULL, '카샤사 — 원형인 카이피리냐가 됩니다. 풀 같은 거친 향이 붙습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, NULL, NULL, '반 개 (6~9등분 깍둑썰기)', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sugar';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '3~4 barspoon', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '라임 반 개를 머들링하기 좋게 6~9등분으로 깍둑썬다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '라임과 설탕을 잔에 넣고 머들링한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '크러시드 아이스를 넣고 다소 길게 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '얼음을 더 채우고 라임을 올린다.');
    END IF;
    -- 롭 로이 (Rob Roy)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'robroy') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'robroy', '롭 로이', 'Rob Roy', '바닐라 느낌이 적고 맨해튼보다 섬세하다. 생각보다 달큰하고 복잡하며, 쓰는 스카치에 따라 편차가 크다.',
            'whisky', 'spirit-forward', 'stir',
            'semi_dry', '칵테일',
            29,
            true, '## 맨해튼의 스코틀랜드 형제



월도프-아스토리아 호텔의 한 바텐더가 1894년에 만들었다는 이야기가 가장 널리 알려져 있다. 이름은 오페레타 《롭 로이》의 초연을 기념해 붙였고, 주인공 로버트 로이 맥그리거의 약칭이기도 하다. 스코틀랜드의 민속 영웅이니 스카치가 들어가는 잔에는 그럴듯한 작명이다.

다만 10년 앞선 1884년에 이미 스카치와 베르무트를 쓴 다른 이름의 칵테일이 있었고, 기주만 다른 맨해튼은 그보다도 먼저 있었다. 당시에는 스위트 베르무트가 들어간 칵테일이 크게 유행했다. 정말 이 호텔에서 ''탄생''했는지에는 의문이 남는다.',
            '1894년', '뉴욕, 미국', '월도프-아스토리아 호텔 (구전)',
            ARRAY[3, 1, 2, 4, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'scotch-whisky';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, '블렌디드로 만들어도 무방하나, 캐릭터가 확실한 싱글 몰트를 권합니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'angostura-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1~2 dash', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '가니시 외의 재료를 믹싱 글라스에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '칵테일 잔에 따른다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '오렌지 껍질을 짜 오일을 뿌린다. 마라스키노 체리를 하나 넣어도 좋다.');
    END IF;
    -- 바비 번스 (Bobby Burns)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'bobbyburns') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'bobbyburns', '바비 번스', 'Bobby Burns', '아주 복합적이다. 스카치의 바닐라와 숙성감, 베르무트의 와인스러움과 향신료, 베네딕틴의 꿀과 허브까지 겹겹이 쌓인다. 나이트캡에 어울린다.',
            'whisky', 'spirit-forward', 'stir',
            'semi_dry', '칵테일',
            28,
            true, '## 시인인가, 시가 판매원인가



스코틀랜드의 국민 시인 로버트 번스를 기린 잔으로 알려져 있고 그의 생일을 기념하는 번스 나이트에서 자주 언급된다. 다만 이름이 같다는 것 말고는 근거가 빈약하다. 알버트 크로켓은 《Old Waldorf-Astoria Bar Days》(1931)에서 옛 월도프 호텔 바의 단골이던 시가 판매원의 이름일 가능성도 함께 적었다.

가장 오래된 기록은 1899년까지 올라가지만 진저 코디얼이 들어가는 전혀 다른 배합이다. 1900년대 초에 베이비 번스라는 이름으로 지금과 비슷한 것이 기록됐고, 현재의 형태는 1930년 사보이 칵테일 북에서 굳어졌다. 재료와 시기를 보면 롭 로이의 변형으로 읽는 편이 자연스럽다.',
            '1930년', '런던, 영국', '해리 크래독 《The Savoy Cocktail Book》',
            ARRAY[3, 1, 2, 5, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'scotch-whisky';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, '피트가 없고 숙성감이 과하지 않은 것을 권합니다. 피트가 들어가면 균형이 무너집니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'benedictine-dom';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 6.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '믹싱 글라스에 모든 재료를 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '차게 해 둔 잔에 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '레몬 껍질을 짜 오일을 뿌린다.');
    END IF;
    -- 올드 팔 (Old Pal)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'oldpal') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'oldpal', '올드 팔', 'Old Pal', '네그로니 특유의 묵직한 단맛 대신 가볍고 화사하다. 쌉쌀함·달달함·허브감이 화사하게 겹치고 라이의 바닐라가 살짝 비친다.',
            'whisky', 'spirit-forward', 'stir',
            'semi_dry', '칵테일',
            24,
            true, '## 누구의 오랜 친구인가



파리 해리스 뉴욕 바의 주인 해리 맥켈혼이 낸 《Barflies and Cocktails》 마지막에 실린 아서 모스의 에세이 〈Cocktails Round Town〉에서 처음 등장한다. 개정판에 따르면 만든 사람은 윌리엄 로빈슨이고, 오랜 친구인 ''저자''를 위해 ''오랜 친구''라는 뜻의 이름을 붙였다.

그 ''저자''가 맥켈혼인지 모스인지는 논란이 있는데, 이 대목이 메인 칵테일 목록이 아니라 모스의 에세이에 있다는 점에서 최근에는 모스로 보는 쪽이 힘을 얻는다. 에세이의 배합은 ''이탈리안 베르무트''라고만 적혀 있어 지금의 드라이 베르무트 배합과는 거리가 있다. 원전에서 꽤 많이 변한 잔이다.',
            '1927년', '파리, 프랑스', '해리 맥켈혼 《Barflies and Cocktails》',
            ARRAY[2, 1, 4, 4, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rye-whiskey';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 20, 'ml', NULL, '스위트 베르무트로 바꾸면 불바디에에 가까워집니다. 무게가 확 붙습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '믹싱 글라스에 모든 재료를 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '차게 해 둔 잔에 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '레몬 껍질을 짜 오일을 뿌린다. 라이 위스키와 레몬 껍질은 의외로 궁합이 좋다.');
    END IF;
    -- 바나나 불바디에 (Banana Boulevardier)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'bananaboulevardier') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'bananaboulevardier', '바나나 불바디에', 'Banana Boulevardier', '쨍하고 확실한 바나나 맛에 캄파리의 쌉쌀함과 버무스의 허브감이 붙고, 버번의 견과류가 중심을 잡는다. 단맛이 꽤 강한 편이다.',
            'whisky', 'spirit-forward', 'stir',
            'sweet', '올드 패션드',
            23,
            true, '## 남은 리큐르를 비우려다



계기가 단순하다. 앤빌 바 & 레퓨지의 총괄 매니저 테리 윌리엄스는 메뉴 개발 후 소량 남은 바나나 리큐르를 비우고 싶었고, 캄파리와 동량으로 샷 잔에 섞어 마셔 봤다가 엄청나게 맛있다는 걸 알았다. 그 조합이 이 잔이 됐다.

바나나 리큐르와 캄파리의 비율을 조금 낮춰 잡는 편이 낫다. 그대로 만들면 단맛이 앞선다. 기주를 버번이 아니라 오버프루프 자메이칸 럼으로 바꾸면 훨씬 펑키한 네그로니 변형이 된다.',
            '2015년 8월', '휴스턴, 미국', '테리 윌리엄스 (Anvil Bar & Refuge)',
            ARRAY[4, 1, 3, 4, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'nutty');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'bourbon-whiskey';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'banana-liqueur';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '가니시 외의 재료를 락 잔에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '오렌지 껍질을 짜 오일을 뿌린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '사용한 껍질과 말린 바나나를 올린다.');
    END IF;
    -- 럼앤소다 (Rum & Soda)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'rumsoda') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'rumsoda', '럼앤소다', 'Rum & Soda', '열대과일과 바닐라, 후숙된 바나나가 은은하게 느껴지는 부담 없는 한 잔. 결국 럼 하이볼이라 어떤 럼을 쓰느냐가 맛의 전부를 정한다.',
            'rum', 'highball', 'build',
            'dry', '하이볼',
            10,
            true, '## 본드가 처음 주문한 잔



다니엘 크레이그가 새 제임스 본드로 데뷔한 《카지노 로얄》(2006)에서 그가 가장 먼저 주문하는 술은 마티니가 아니라 마운트 게이 럼으로 만든 럼앤소다다. 배경인 바하마는 아열대 기후이고 마운트 게이는 이웃 섬 바베이도스에서 만들어진다. 현지 술을 기후에 맞춰 고른 셈이다.

럼은 오랫동안 선원과 노동자의 술이라는 이미지가 강했다. 이전 세대의 본드가 샴페인과 마티니처럼 상류층의 코드를 공유하는 술을 마셨다는 걸 생각하면, 첫 잔을 럼으로 고른 것은 거칠고 대담한 본드로 바뀌었다는 신호로 읽을 수 있다.',
            '미상', '바하마 (《카지노 로얄》 배경)', '미상',
            ARRAY[1, 0, 0, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'barbados-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 40, 'ml', NULL, '숙성감이 과하지 않은 골드 럼이면 무엇이든 됩니다. 자메이카 럼을 쓰면 펑키함이 확 올라옵니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '하이볼 글라스에 럼과 얼음을 채우고 럼을 식힐 정도로만 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '탄산수를 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼음을 위아래로 몇 번 들었다 놓는다.');
    END IF;
    -- 킹스톤 네그로니 (Kingston Negroni)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'kingstonnegroni') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'kingstonnegroni', '킹스톤 네그로니', 'Kingston Negroni', '클래식보다 허브감이 훨씬 덜하고 단순한데, 후숙된 바나나와 흑당·초콜릿·향신료가 통통 튄다. 복합성은 떨어져도 자극적인 매력이 있다.',
            'rum', 'spirit-forward', 'stir',
            'semi_dry', '올드 패션드',
            28,
            true, '## 5분 만에 나온 변주



바텐더 호아킨 시모가 주류 수입업자에게서 스미스 앤 크로스라는 럼을 건네받고 5분 만에 만들어 냈다고 한다. 이 럼은 자메이카 럼 중에서도 오버프루프에 해당해 캐릭터가 아주 강렬하다.

자메이카 럼의 특징은 ''펑키함''이다. 과하게 후숙된 바나나, 열대 과일, 따뜻한 계열의 향신료가 삐죽삐죽 튀어나오는 캐릭터다. 클래식과 궁합이 나쁠 것 같지만 오히려 캄파리처럼 센 재료와 잘 맞물린다. 마실 때 도수감이 크게 느껴지지 않으니 주의해야 한다.',
            '2010년경', '뉴욕, 미국', '호아킨 시모',
            ARRAY[3, 1, 4, 4, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'jamaican-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, '애플턴 — 도수는 아쉽지만 구하기 쉽습니다. 다른 지역 럼을 쓰면 그냥 럼 네그로니가 됩니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 30, 'ml', NULL, '코키 디 토리노 · 안티카 포뮬라 — 기주와 캄파리가 둘 다 세니 캐릭터가 강한 쪽이 낫습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '락 글라스에 모든 재료를 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 넣고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '오렌지 껍질의 오일을 잔 위에 뿌린다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '쓴 껍질을 다듬어 넣는다.');
    END IF;
    -- 보스턴 쿨러 (Boston Cooler)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'bostoncooler') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'bostoncooler', '보스턴 쿨러', 'Boston Cooler', '상큼 달달하고 생강의 알싸한 맛이 매력적이다. 약간의 탄산감이 붙어 모스코 뮬에 럼의 풍미가 더해진 느낌이 난다.',
            'rum', 'highball', 'shake',
            'semi_sweet', '하이볼',
            10,
            true, '## 이름만 서양에 남은 잔



쿨러는 지금은 대체로 ''기주 + 시트러스 즙 + 당 + 탄산 믹서''의 형태를 가리킨다. 피즈·콜린스·벅 계열과 구분에 큰 의미가 없다시피 하다. 데이비드 엠버리는 《The Fine Art of Mixing Drinks》(1948)에서 쿨러를 근본적으로 홀시스 넥의 변형이라고 적기도 했다.

흥미로운 건 인지도다. 분명 서양에서 만들어진 것으로 보이는데 지금 서양에서는 거의 알려져 있지 않고 일본과 한국 정도에서만 통한다. 영어로 검색하면 진저에일에 바닐라 아이스크림을 얹은 디트로이트의 음료가 주로 나온다.',
            '미상', '미국 (추정)', '미상 (쿨러 계열)',
            ARRAY[3, 4, 1, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, '골드 럼이나 자메이카 럼을 쓰면 맛의 폭이 넓어집니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 19, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 10, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'ginger-beer';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 90, 'ml', NULL, '진저에일 — 알싸한 맛이 크게 줄어듭니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-wheel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '진저비어 외의 모든 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '긴 잔에 얼음을 채우고 셰이커에서 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '진저비어를 더하고 얼음을 위아래로 두세 번 움직인다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '레몬 휠을 넣는다.');
    END IF;
    -- 비앤비 (B&B)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'bnb') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'bnb', '비앤비', 'B&B', '베네딕틴의 사프란·꿀·허브 캐릭터에 꼬냑의 풍미가 겹친다. 재료가 둘뿐이고 베네딕틴의 맛은 정해져 있으니 어떤 꼬냑을 쓰느냐로 맛이 갈린다.',
            'brandy', 'spirit-forward', 'build',
            'semi_sweet', '올드 패션드',
            35,
            true, '## 두 글자짜리 배합



이름은 Benedictine & Brandy의 약자다. 가장 잘 알려진 이야기는 1930년대 뉴욕의 사교클럽 ''21 Club''의 바텐더가 만들었다는 것인데, 이미 1910년 레이먼드 설리번의 《The Barkeeper''s Manual》에 푸스카페 스타일로 기록돼 있다. 그때는 베네딕틴과 꼬냑이 2:1이었다.

베네딕틴이 19세기에 상품화된 것을 생각하면 1910년 이전에 이미 이 조합이 있었을 가능성도 무리한 추측은 아니다. 인기가 있었던 모양인지 1937년에는 베네딕틴이 직접 베네딕틴 60%와 프랑스 브랜디 40%를 섞은 RTD 제품을 내놓기도 했다.',
            '1910년 이전', '미국', '미상 (1930년대 21 Club 설)',
            ARRAY[3, 0, 2, 5, 5]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vsop-cognac';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'benedictine-dom';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1조각 (선택)', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '잔에 재료를 넣고 충분히 젓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 조금만 스터한다. 70~80%의 맛만 내면 된다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '큰 얼음으로 교체해 서브한다. 나머지는 마시는 동안 녹으며 희석된다.');
    END IF;
    -- 비트윈 더 시츠 (Between the Sheets)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'betweenthesheets') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'betweenthesheets', '비트윈 더 시츠', 'Between the Sheets', '원형인 사이드카보다 오렌지와 알코올에서 오는 단맛이 강하게 난다. 다만 복합성 자체는 사이드카보다 꽤 많이 떨어진다.',
            'brandy', 'sour', 'shake',
            'semi_sweet', '쿠페',
            24,
            true, '## 사이드카의 못난 형제



파리 해리스 뉴욕 바의 해리 맥켈혼이 만들었다는 설이 가장 널리 알려져 있다. 1921년 런던 버클리 호텔의 매니저 폴리가 만들었다는 설도 있는데, 맥켈혼의 저서에 실린 칵테일 중 그가 만들지 않은 것이 그의 것으로 잘못 알려진 경우가 여럿이라 확실하지 않다.

구조상 사이드카에서 꼬냑을 줄이고 그만큼 럼을 채운 것이다. 이름은 ''침대 안에서''라는 뜻이고 나이트캡으로 마시는 잔이라고 하는데, 이런 신맛의 칵테일을 나이트캡으로 잘 마시지 않는 걸 보면 이름 때문에 붙은 설명 같다. 원전 배합은 알코올감이 너무 강해 아카이브는 레몬을 늘린 배합을 기준으로 둔다.',
            '1920~30년대', '파리, 프랑스', '해리 맥켈혼 (구전)',
            ARRAY[3, 4, 1, 3, 4]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'vsop-cognac';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 22.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'white-rum';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 22.5, 'ml', NULL, '숙성 럼을 써도 무방합니다. 색과 맛이 달라집니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'cointreau';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 15, 'ml', NULL, '오렌지 큐라소로 대체할 수 있습니다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '모든 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '차게 해 둔 잔에 붓는다.');
    END IF;
    -- 칼바도스 토닉 (Calvados Tonic)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'calvadostonic') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'calvadostonic', '칼바도스 토닉', 'Calvados Tonic', '잔을 입으로 가져가면 향긋한 레몬과 달큰한 사과 향이 먼저 온다. 사과의 단맛과 토닉의 쌉쌀함이 청량하게 균형을 잡는다.',
            'brandy', 'highball', 'build',
            'semi_sweet', '하이볼',
            9,
            true, '## 지역 이름이 곧 술 이름



칼바도스는 사과(또는 배)를 발효한 시드르를 증류해 숙성한 브랜디다. 프랑스 노르망디의 칼바도스 지역에서 수확한 것으로 만들어야 하는데, 지역명이 곧 술 이름이 되는 것은 꼬냑과 같은 이치다. 꼬냑처럼 AOC로 보호받으며 세 등급으로 나뉜다.

구조는 진토닉과 같지만 칼바도스가 달달한 사과 캐릭터를 가지고 있어 훨씬 달고 가볍다. 앙고스투라를 한 방울 넣으면 맛이 풍부해지고, 토닉을 줄이고 탄산수를 더하면 탄산감이 산다.',
            '미상', '노르망디, 프랑스 (칼바도스 산지)', '미상',
            ARRAY[3, 1, 2, 4, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'calvados';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 40, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'tonic-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 120, 'ml', NULL, '토닉 100ml + 탄산수 20ml — 탄산감이 살아납니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'angostura-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1 dash (선택)', NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '긴 잔에 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '칼바도스와 비터를 붓고 살짝 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '토닉워터를 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '바 스푼으로 얼음을 살짝 들었다 놓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '레몬 껍질의 오일을 짜 넣고 껍질도 넣는다.');
    END IF;
    -- 뱀부 (Bamboo)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'bamboo') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'bamboo', '뱀부', 'Bamboo', '드라이하고 깔끔하며 약간 떫고 독특한 짠맛이 있다. 피노 셰리의 견과류와 베르무트의 허브·향신료가 조용히 겹친다.',
            'wine', 'spirit-forward', 'stir',
            'dry', '쿠페',
            13,
            true, '## 누가 먼저였는지 알 수 없는 잔



1890년대 요코하마 그랜드 호텔에서 독일계 미국인 바텐더 루이스 에핑어가 만들었다는 것이 통설이고, 출처는 윌리엄 부스비의 《The World''s Drinks and How to Mix Them》(1908)으로 보인다. 그런데 1886년 9월 11일자 《Western Kansas World》는 뱀부가 이미 영국인들에 의해 소개되어 뉴욕에서 인기를 끌고 있다고 적었다.

주목할 점은 ''만들어졌다''가 아니라 ''소개되어 왔다''는 표현이다. 에핑어는 1880년대 초중반 미국 북서부 항구도시에서 술집을 운영했다. 그 영국인들이 거기서 맛보고 다른 도시로 옮겼을 가능성을 상상하게 하지만 더 이상의 기록은 없다. 1890~1910년대에 베르무트만 다른 아도니스가 나온 걸 보면 셰리와 베르무트의 조합 자체가 당시 새로운 것은 아니었다.',
            '1880년대 중반', '요코하마, 일본 (통설)', '루이스 에핑어 (통설)',
            ARRAY[1, 1, 2, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'nutty');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'fino-sherry';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, '다른 셰리를 써도 되지만, 드라이하고 깔끔한 맛을 원하면 피노를 권합니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1 dash (8~10 drops)', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '모든 재료를 믹싱 글라스에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '스터한 뒤 잔에 따른다.');
    END IF;
    -- 아도니스 (Adonis)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'adonis') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'adonis', '아도니스', 'Adonis', '감칠맛이 압도적이다. 와인스러움에 한약 같은 뉘앙스가 겹치고, 약간의 오렌지와 견과류 뒤로 향신료·허브·차의 캐릭터가 훅 지나간다.',
            'wine', 'spirit-forward', 'stir',
            'semi_dry', '칵테일',
            12,
            true, '## 뮤지컬에서 온 이름



1884년 초연 후 브로드웨이에서 500회 넘게 공연된 동명의 뮤지컬에서 이름을 따왔다는 것이 공통된 설명이다. 1887년 뉴욕의 한 신문 칼럼에 ''아도니스''라는 이름의 칵테일 일화가 처음 나오지만 그것이 지금의 아도니스인지는 알 수 없다. 지금의 형태는 자크 스트라우브의 《Straub''s Manual of Mixed Drinks》(1913)에서 확인된다.

월도프-아스토리아 호텔 설이 그럴듯한 이유는 여럿이다. 당시 셰리와 베르무트는 상류층이 즐기던 값비싼 술이라 고급 호텔에서나 취급했고, 이 호텔은 사교의 중심지였으며, 1931년과 1935년에 호텔의 레시피집이 따로 출판될 정도였다. 뱀부에서 드라이 베르무트를 스위트로 바꾼 잔이라 캐릭터를 어느 정도 공유한다.',
            '1913년 이전', '뉴욕, 미국', '월도프-아스토리아 호텔 (추정)',
            ARRAY[2, 1, 2, 4, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'spirit-forward');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'nutty');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'spicy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'fino-sherry';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 22.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-bitters';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '1~2 dash', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '모든 재료를 믹싱 글라스에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 채우고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '칵테일 잔에 따른다.');
    END IF;
    -- 폼피에 (Pompier)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'pompier') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'pompier', '폼피에', 'Pompier', '덜 달지만 허브감과 와인스러움이 붙은, 약간은 복잡한 카시스 소다 같다. 복합미가 있는 잔은 아니고 맛 자체는 단순한 편이다.',
            'wine', 'highball', 'build',
            'semi_sweet', '하이볼',
            7,
            true, '## 베르무트가 기주인 잔



폼피에는 프랑스어로 ''소방관''을 뜻한다. 어떻게 생겨났는지, 왜 이런 이름이 붙었는지는 전혀 알려진 바가 없다. 국내에는 인지도가 없다시피 하고 일본과 서양에서 조금 알려져 있는 정도다.

기주가 드라이 베르무트라는 점이 특이하다. 베르무트를 사 두고 쓸 곳이 없어 애를 먹는 경우에 특히 쓸모가 있다. 크렘 드 카시스는 제품마다 품질 차이가 극명하니 좋은 것을 고르는 편이 낫다.',
            '미상', '미상', '미상',
            ARRAY[3, 1, 1, 3, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, '프랑스 베르무트를 권합니다. 이탈리안은 깔끔한 편이라 달고 밋밋해질 수 있습니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'creme-de-cassis';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 17.5, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 110, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '긴 잔에 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '카시스와 베르무트를 넣고 잘 섞이게 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '탄산수를 넣고 얼음을 살짝 위아래로 움직인다.');
    END IF;
    -- 비어 아메리카노 (Beer Americano)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'beeramericano') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'beeramericano', '비어 아메리카노', 'Beer Americano', '맥주가 들어간 것치고 쓴맛이 안 나고 오히려 들큰하면서 산뜻하다. 탄산 대신 폭신폭신한 질감이 캄파리의 쓴맛을 감싼다.',
            'liqueur', 'highball', 'build',
            'semi_sweet', '하이볼',
            11,
            true, '## 탄산 대신 거품



클래식 칵테일 아메리카노에서 탄산수를 맥주로 바꾼 잔이다. 캄파리와 베르무트에 맥주를 섞거나 맥주에 캄파리를 조금 넣어 마시는 방식은 이전부터 있었다. 이 잔의 특별함은 다른 데 있다.

토마소 세카는 맥주를 휘핑해 실질적으로 거품을 넣었다. 맥주 하면 청량한 탄산을 떠올리게 되지만 여기에는 탄산이 없고 폭신한 질감이 대신 들어간다. 클래식 아메리카노가 쓴맛을 가볍고 청량하게 즐기는 잔이라면, 이쪽은 무게감을 잃지 않으면서 부드럽게 가는 잔이다.',
            '2010년대 중반', '밀라노, 이탈리아', '토마소 세카 (Cafe Trussardi)',
            ARRAY[3, 1, 3, 3, 2]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'creamy');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sweet-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lager-beer';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, NULL, NULL, '적당량 (휘핑해 거품으로)', '가볍고 깔끔한 라거를 권합니다. 캐릭터가 센 맥주는 캄파리와 부딪힙니다.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-peel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '얼음을 채운 하이볼 잔에 맥주 외의 재료를 넣고 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '맥주를 휘핑해 거품을 만든다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '거품을 잔에 따른다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '바 스푼으로 잘 섞는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '오렌지 껍질로 가니시한다.');
    END IF;
    -- 슬로 진 피즈 (Sloe Gin Fizz)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'sloeginfizz') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'sloeginfizz', '슬로 진 피즈', 'Sloe Gin Fizz', '베리류와 핵과류의 달큰한 과실감에 레몬의 상큼함이 붙어 새콤달콤함이 주를 이룬다. 슬로 진이 26%라 알코올감이 거의 느껴지지 않는다.',
            'liqueur', 'sour', 'shake',
            'semi_sweet', '하이볼',
            7,
            true, '## 가정집에서 바로



슬로 진은 오래전부터 영국 가정에서 만들어지던 리큐르다. 토지 경계를 나누는 울타리에 흔한 블랙손 가지의 열매 슬로베리는 그대로 먹기에는 떫고 시어서 진에 설탕과 함께 담가 침출했다. 병에 담아 두면 되니 가정에서 만들기 쉬웠다.

이 술은 19세기 말~20세기 초 영국에서 상업적으로 생산되기 시작했고, 같은 시기에 이미 여러 저서가 다양한 기주의 피즈를 소개하고 있었다. 두 흐름을 겹쳐 보면 이 잔은 19세기 말쯤 생겼을 것으로 짐작된다. 가정에서 만들던 술이 상업화를 거쳐 바의 문화로 편입된 독특한 이력이다.',
            '19세기 말 추정', '영국', '미상',
            ARRAY[4, 4, 0, 3, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'sour');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'sloe-gin';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lemon-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 20, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 10, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 90, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '탄산수 외의 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 넣고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼음을 채운 하이볼 잔에 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '탄산수를 2~3번에 나누어 조심히 붓는다. 한 번에 부으면 거품이 확 올라온다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '바 스푼으로 얼음을 위아래로 들어 섞는다.');
    END IF;
    -- 조엽수림 (Shoyojurin)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'shoyojurin') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'shoyojurin', '조엽수림', 'Shoyojurin', '쌉쌀하고 달큰한 차의 맛이 이어지다 끝에 살짝 쌉쌀하고 텁텁한 여운이 남는다. 도수가 낮아 식후나 마지막 한 잔에 어울린다.',
            'liqueur', 'highball', 'build',
            'semi_sweet', '하이볼',
            5,
            true, '## 차의 길을 따라 걷는 이름



기원이 잘 알려지지 않은 잔이다. 만화 《바텐더》에 따르면 산토리 스쿨에서 강사를 했던 바텐더 후쿠니시 에이조가 만들었다고 한다. 녹차 리큐르를 단독 기주로 쓰는데, 산토리가 만드는 헤르메스 그린 티 리큐르가 원조로 보인다. 1950년대에 처음 발매됐을 만큼 오래된 리큐르다.

조엽수림은 습기 많은 곳에 분포하는 상록 활엽수 중심의 삼림 군계를 가리킨다. 만화는 그 분포가 히말라야 중턱에서 동남아시아·중국·한반도를 거쳐 일본에 이르는 차의 길과 겹친다고 표현한다. 색과 이야기에 딱 맞는 이름이라는 뜻이다.',
            '미상', '일본', '후쿠니시 에이조 (구전)',
            ARRAY[3, 0, 3, 3, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'green-tea-liqueur';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'oolong-tea';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 127, 'ml', NULL, '맛이 너무 연한 것은 피합니다. 변화를 줄 재료가 적어 우롱차가 맛의 절반입니다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '차게 식혀 둔 긴 잔에 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '녹차 리큐르를 넣고 살짝 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '우롱차를 넣고 얼음을 몇 번 올렸다 내린다.');
    END IF;
    -- 차이나 블루 (China Blue)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'chinablue') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'chinablue', '차이나 블루', 'China Blue', '리치의 달달하고 화려한 맛에 자몽의 신맛, 블루 큐라소의 달달한 시트러스가 겹친다. 간단하지만 복합적이고 비주얼이 화려하다.',
            'liqueur', 'highball', 'build',
            'sweet', '하이볼',
            5,
            true, '## 중국이 아니라 도자기



이름의 ''차이나''는 중국이 아니라 도자기(china)를 뜻한다. 풀어 보면 ''청색의 아름다운 도자기''를 본떠 만든 잔이다. 이름 때문에 중국에서 만들어졌을 것 같지만 실제로는 일본에서 만들어졌다. 도야마현의 노포 바 하쿠바칸에서 우치다 테루히로 바텐더가 만들었다고 하며, 이 바는 3대에 걸쳐 지금도 운영되고 있다.

원래는 토닉이 들어가지 않고 자몽즙이 더 많이 들어가는 쇼트 스타일이었지만 지금은 롱 스타일이 더 대중적이다. 토닉 외의 재료를 셰이킹하는 바텐더도 있고 그편이 일체감은 낫다. 다만 연한 핑크색 위로 파란 큐라소를 부어 색이 변하는 연출은 이 방식에서만 나온다.',
            '미상', '도야마현, 일본', '우치다 테루히로 (Bar Hakubakan)',
            ARRAY[4, 3, 1, 3, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'fruity');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lychee-liqueur';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, '콰이페 또는 디타.');
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'grapefruit-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'tonic-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 67, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'blue-curacao';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 10, 'ml', NULL, NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '긴 잔에 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '리치 리큐르와 자몽즙을 넣고 잠시 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '토닉워터를 얼음에 닿지 않게 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '바 스푼으로 얼음을 위아래로 들어 준다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '블루 큐라소를 붓는다. 마실 때 다시 저어 섞는다.');
    END IF;
    -- 스푸모니 (Spumoni)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'spumoni') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'spumoni', '스푸모니', 'Spumoni', '캄파리의 쌉쌀하고 시트러시한 맛이 자몽과 정말 잘 어울린다. 과하게 달지 않고 약간 쌉쌀하며 도수가 낮아 부담이 없다.',
            'liqueur', 'highball', 'build',
            'semi_sweet', '하이볼',
            5,
            true, '## 이탈리아인가 일본인가



어디에서 누가 만들었는지 단서가 없다시피 한 잔이다. 한국과 일본에서는 ''거품이 나다''라는 뜻의 이탈리아어 spumare에서 유래한 이탈리아 칵테일로 알려져 있다. 그런데 서양에서는 오히려 일본 칵테일로 인식한다. 한 이탈리아 음식·문화 전문가는 이탈리아에서 스푸모니라는 이름의 칵테일을 들어 본 적이 없다고 할 정도다.

이탈리아에서 스푸모니는 전통적인 아이스크림 디저트를 가리키고 이 칵테일과는 상관이 없어 보인다. 결국 이탈리아와의 접점은 캄파리 하나뿐이다. 이름의 어원이 이탈리아어라는 사실이 와전되어 칵테일 자체가 이탈리아에서 유래했다고 받아들여진 것 아닐까 싶다.',
            '미상', '미상', '미상',
            ARRAY[3, 3, 4, 3, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'grapefruit-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 45, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'tonic-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 67, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'grapefruit-slice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, NULL, NULL, '1조각 (선택)', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '긴 잔에 얼음을 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '캄파리와 자몽즙을 넣고 잠시 스터한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼음에 닿지 않게 토닉워터를 따른다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '바 스푼으로 얼음을 위아래로 들어 준다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '자몽 조각으로 장식한다.');
    END IF;
    -- 롬 윗 어 뷰 (Rome with a View)
    IF NOT EXISTS (SELECT 1 FROM cocktail WHERE slug = 'romewithaview') THEN
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary, base_spirit, style_primary, method, sweetness,
            glass_type, abv_override, is_classic, story,
            origin_year, origin_place, origin_creator, flavor_profile, status
        ) VALUES (
            'romewithaview', '롬 윗 어 뷰', 'Rome with a View', '살짝 찌르는 듯한 상큼함에 쌉쌀함과 좋은 허브감이 얹힌다. 캄파리에서 자몽 같은 시트러스 캐릭터가 나오고, 저도수라 아주 편하게 마신다.',
            'liqueur', 'sour', 'shake',
            'semi_sweet', '하이볼',
            7,
            true, '## 쓴맛이 싫다는 손님에게



현대 칵테일계에 큰 영향을 미친 뉴욕의 바 밀크앤허니에서 마이클 매킬로이가 만들었다. 당시 미국에는 쓴맛의 아페리티프나 저도수 술에 대한 유행이 아직 오지 않았고, 캄파리나 네그로니도 큰 인기를 끌지 못하던 시기였다.

매킬로이는 쓴맛 나는 칵테일이 싫다는 손님에게 ''그럴 리가요, 이것 한 번 드셔 보세요'' 하며 이 잔을 내줬다고 한다. 구조는 피즈 계열과 아메리카노를 매시업한 느낌이다. 캄파리와 라임을 함께 쓰는 칵테일이 많지 않은데, 이 잔만 마셔 봐도 둘의 궁합을 알 수 있다.',
            '2008년', '뉴욕, 미국', '마이클 매킬로이 (Milk & Honey)',
            ARRAY[3, 4, 4, 4, 1]::SMALLINT[],
            'draft'
        ) RETURNING id INTO v_cocktail_id;
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'sour');
        INSERT INTO cocktail_style (cocktail_id, style) VALUES (v_cocktail_id, 'highball');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'bitter');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'citrus');
        INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (v_cocktail_id, 'herbal');
        INSERT INTO recipe (cocktail_id, version_type) VALUES (v_cocktail_id, 'standard')
            RETURNING id INTO v_recipe_id;
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'campari';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 1, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'dry-vermouth';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 2, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'lime-juice';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 3, 30, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'simple-syrup';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 4, 15, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'soda-water';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 5, 60, 'ml', NULL, NULL);
        SELECT id INTO STRICT v_ingredient_id FROM ingredient WHERE slug = 'orange-wheel';
        INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit, amount_label, substitute_note)
        VALUES (v_recipe_id, v_ingredient_id, 6, NULL, NULL, '1조각', NULL);
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 1, '탄산수 외의 재료를 셰이커에 넣는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 2, '얼음을 넣고 셰이크한다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 3, '얼음을 채운 긴 잔에 붓는다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 4, '탄산수를 적당량 채운다.');
        INSERT INTO recipe_step (recipe_id, step_no, text) VALUES (v_recipe_id, 5, '오렌지 휠을 올린다.');
    END IF;
END
$seed$;
