#!/data/data/com.termux/files/usr/bin/bash

run_id=$(gh run list --workflow build-apk.yml --status failure --limit 1 \
  --json databaseId --jq '.[0].databaseId')

if [ -z "$run_id" ] || [ "$run_id" = "null" ]; then
  echo "No failed runs found for build-apk.yml"
  exit 0
fi

info=$(gh run view "$run_id" --json name,headBranch,createdAt,url \
  --jq '"Workflow: \(.name)\nBranch: \(.headBranch)\nCreated: \(.createdAt)\nRun: \(.url)"')
logs=$(gh run view "$run_id" --log-failed 2>&1 | tail -n 80)

gh issue create \
  --title "Fix failed Android build (run $run_id)" \
  --body "$(printf '%s\n\n### Failed-step logs\n```text\n%s\n```' "$info" "$logs")"
