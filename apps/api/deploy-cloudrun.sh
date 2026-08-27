#!/usr/bin/env bash
# Cloud Run 배포 (ADR-0007 2단계 — Fly 에서 이관, 2026-08-25).
#
# 왜 Cloud Run 인가: 요청이 없으면 인스턴스가 0 으로 내려가 과금이 멈춘다.
# 트래픽이 아직 거의 없는 단계라 상시 과금(Fly)보다 맞다. 콜드스타트는
# min-instances=0 의 대가다 — 어드민 첫 요청이 몇 초 걸린다. 트래픽이 붙으면
# min-instances 를 1 로 올린다(그때만 상시 과금).
#
# 사용법: GCP_PROJECT=<프로젝트ID> bash deploy-cloudrun.sh
#
# 시크릿은 ~/mut-secrets 에서 읽어 Secret Manager 에 넣고, Cloud Run 은 이름으로
# 참조한다 — 값이 콘솔의 env 목록에 평문으로 남지 않는다.
set -euo pipefail

GCLOUD=/opt/homebrew/share/google-cloud-sdk/bin/gcloud
PROJECT="${GCP_PROJECT:?GCP_PROJECT 환경변수에 프로젝트 ID 를 주세요}"
REGION="asia-southeast1"   # 싱가포르 — Neon(ap-southeast-1) 옆
SVC="mut-api"
SECRETS_DIR="$HOME/mut-secrets"

echo "▶ 프로젝트 설정: $PROJECT"
"$GCLOUD" config set project "$PROJECT" >/dev/null

echo "▶ API 활성화 (run · cloudbuild · secretmanager · artifactregistry)"
"$GCLOUD" services enable \
  run.googleapis.com cloudbuild.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com >/dev/null

# ── 시크릿을 Secret Manager 에 올린다 (있으면 새 버전 추가) ──────────────────
put_secret() {
  local name="$1" value="$2"
  if "$GCLOUD" secrets describe "$name" >/dev/null 2>&1; then
    printf '%s' "$value" | "$GCLOUD" secrets versions add "$name" --data-file=- >/dev/null
  else
    printf '%s' "$value" | "$GCLOUD" secrets create "$name" --data-file=- --replication-policy=automatic >/dev/null
  fi
  echo "  · $name"
}

NEON_MUT_URL="$(cat "$SECRETS_DIR/neon-mut-url.txt")"
HOST="$(printf '%s' "$NEON_MUT_URL" | sed 's|.*@||;s|/.*||')"
OWNER_PW="$(printf '%s' "$(cat "$SECRETS_DIR/neon-url.txt")" | sed 's|postgresql://neondb_owner:||;s|@.*||')"

echo "▶ 시크릿 업로드"
put_secret DB_URL "jdbc:postgresql://$HOST/mut?sslmode=require"
put_secret DB_PASSWORD "$(cat "$SECRETS_DIR/mut-web-password.txt")"
put_secret DB_MIGRATE_PASSWORD "$OWNER_PW"
put_secret KAKAO_CLIENT_ID "$(cat "$SECRETS_DIR/kakao-rest-key.txt" | tr -d '[:space:]')"
put_secret KAKAO_CLIENT_SECRET "$(cat "$SECRETS_DIR/kakao-client-secret.txt" | tr -d '[:space:]')"
put_secret MUT_REVALIDATE_SECRET "$(cat "$SECRETS_DIR/revalidate-secret.txt")"

echo "▶ Cloud Run 배포 (소스에서 빌드 — Dockerfile 사용)"
"$GCLOUD" run deploy "$SVC" \
  --source . \
  --region "$REGION" \
  --allow-unauthenticated \
  --port 8080 \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 2 \
  --timeout 60 \
  --set-env-vars "DB_USER=mut_web,DB_MIGRATE_USER=neondb_owner,MUT_OAUTH_REDIRECT_BASE=https://mut-web.vercel.app,MUT_OAUTH_ALLOWED_RETURNS=https://mut-web.vercel.app,MUT_OAUTH_KAKAO_SCOPES=profile_nickname,MUT_REVALIDATE_ENABLED=true,MUT_FRONTEND_URL=https://mut-web.vercel.app,MUT_MEDIA_BUCKET=mut-media-project-9400c8f3-9760-4e8f-8d8" \
  --set-secrets "DB_URL=DB_URL:latest,DB_PASSWORD=DB_PASSWORD:latest,DB_MIGRATE_PASSWORD=DB_MIGRATE_PASSWORD:latest,KAKAO_CLIENT_ID=KAKAO_CLIENT_ID:latest,KAKAO_CLIENT_SECRET=KAKAO_CLIENT_SECRET:latest,MUT_REVALIDATE_SECRET=MUT_REVALIDATE_SECRET:latest"

echo "▶ 완료. 서비스 URL:"
"$GCLOUD" run services describe "$SVC" --region "$REGION" --format 'value(status.url)'
