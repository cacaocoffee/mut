-- #183 — 블로그에서 이관한 아티클 본문의 영문 소제목을 한국어로 바꾼다.
-- body 는 블록 배열(JSONB)이고 소제목 블록은 {"kind":"heading","text":"…"} 다.
-- 시드(R__seed_03_article)는 손대지 않는다 — 다시 돌면 어드민에서 쓴 글까지 지우기 때문이다.
-- 코드 시드(packages/domain/src/articles)는 같은 표로 함께 바꿨다.

UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"About the Cocktail"}', '{"kind":"heading","text":"칵테일 이야기"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"About the Cocktail"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"About The Cocktail"}', '{"kind":"heading","text":"칵테일 이야기"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"About The Cocktail"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"Ingredients"}', '{"kind":"heading","text":"재료"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"Ingredients"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"How to Mix"}', '{"kind":"heading","text":"만드는 법"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"How to Mix"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"How to MIx"}', '{"kind":"heading","text":"만드는 법"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"How to MIx"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"Recipe"}', '{"kind":"heading","text":"레시피"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"Recipe"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"Nose"}', '{"kind":"heading","text":"향"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"Nose"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"Palate"}', '{"kind":"heading","text":"맛"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"Palate"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"Finish"}', '{"kind":"heading","text":"여운"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"Finish"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"About The Whisky"}', '{"kind":"heading","text":"위스키 이야기"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"About The Whisky"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"About the Whisky"}', '{"kind":"heading","text":"위스키 이야기"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"About the Whisky"}%';
UPDATE article SET body = replace(body::text, '{"kind":"heading","text":"About The Whisk(e)y"}', '{"kind":"heading","text":"위스키 이야기"}')::jsonb
  WHERE body::text LIKE '%{"kind":"heading","text":"About The Whisk(e)y"}%';
